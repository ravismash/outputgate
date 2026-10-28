"""Deterministic checks for structured LLM output, for Pydantic v2.

The Python port of outputgate's grounding and arithmetic gates. A model built from these types
checks an LLM's structured answer against the source document it was extracted from, and against
its own arithmetic, before the answer reaches application state. No check calls a model.

- ``GroundedNumber`` and ``GroundedText`` are evidence objects, ``{"value": ..., "source_span":
  {"page": n, "text": "<quote>"}}``. The quote must occur in the source document on the page it
  names, and a number must be one its own quote prints, so a value scaled or rewritten away from
  the source is rejected even when every sum holds.
- ``GroundedModel`` adds cross-field checks (``checks()``) and reports every violation at once,
  grounding and arithmetic together, so a retry prompt can quote all of them.
- ``sum_equals_rounded`` lets items miss their total by half a unit of the last printed digit per
  figure, for figures each rounded where they were printed.

The source document travels in Pydantic's validation context, never in global state::

    Report.model_validate_json(answer, context={"source_text": document})

Fail-closed: grounding without a source document, a value with no quote, and a missing number are
all failures, never passes.
"""

from __future__ import annotations

import functools
import re
import unicodedata
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Annotated, Any, Iterable, NamedTuple, Optional, Sequence, Union

from pydantic import (
    BaseModel,
    BeforeValidator,
    ConfigDict,
    Field,
    StrictInt,
    StrictStr,
    ValidationError,
    ValidationInfo,
    WithJsonSchema,
    model_validator,
)
from pydantic_core import PydanticCustomError

__all__ = [
    "Source",
    "SourceSpan",
    "Grounded",
    "GroundedNumber",
    "GroundedText",
    "GroundedModel",
    "Violation",
    "sum_equals_rounded",
    "assert_sum_equals_rounded",
    "equals",
    "at_most",
    "matches",
    "forbids",
    "describe",
]

# Context key set while GroundedModel re-reads an answer for its shape alone (see _every_violation).
_SHAPE_ONLY = "outputgate.shape_only"

_WHITESPACE = re.compile(r"\s+")
_HYPHEN = re.compile("[­‐-―−]")
_NUMBER = re.compile(r"\d[\d,]*(?:\.\d+)?|\.\d+")


def _strip_currency(s: str) -> str:
    """Drops currency signs ($, €, £, ¥, ₹ and the rest of Unicode's Sc category)."""
    return "".join(c for c in s if unicodedata.category(c) != "Sc")


@dataclass(frozen=True)
class Source:
    """The document an answer was extracted from, and how strictly quotes must match it.

    Pass it as ``context={"source": Source([...pages])}``, or pass ``context={"source_text": text}``
    for the defaults (pages split on form feeds, as in the eval datasets).

    - ``ignore_whitespace``: drop all whitespace and read every kind of hyphen or dash as "-",
      in both the quote and the page; everything else must still match exactly (for PDF or OCR text).
    - ``ignore_currency_signs``: a quote may add or drop a currency sign.
    - ``numbers_match_quotes``: a ``GroundedNumber``'s value must be a number its own quote prints.
    """

    pages: tuple[str, ...]
    ignore_whitespace: bool = True
    ignore_currency_signs: bool = False
    numbers_match_quotes: bool = True
    _normalized: tuple[str, ...] = field(init=False, repr=False, compare=False)

    def __post_init__(self) -> None:
        if isinstance(self.pages, str):
            object.__setattr__(self, "pages", tuple(self.pages.split("\f")))
        else:
            object.__setattr__(self, "pages", tuple(self.pages))
        object.__setattr__(self, "_normalized", tuple(self.normalize(p) for p in self.pages))

    def normalize(self, s: str) -> str:
        """The text as grounding compares it, after the loosening the options ask for."""
        if self.ignore_whitespace:
            s = _HYPHEN.sub("-", _WHITESPACE.sub("", s))
        return _strip_currency(s) if self.ignore_currency_signs else s

    def ungrounded(self, span: "SourceSpan") -> Optional[str]:
        """Why a quote is not on the page it names, or None when it is."""
        if span.page > len(self.pages):
            return f"source_span.page {span.page} is past the end of the document ({len(self.pages)} pages)"
        wanted = self.normalize(span.text)
        if not wanted:
            return "source_span.text is only whitespace" + (" or currency signs" if self.ignore_currency_signs else "")
        if wanted in self._normalized[span.page - 1]:
            return None
        loosened = [w for w, on in (("whitespace", self.ignore_whitespace),
                                    ("currency signs", self.ignore_currency_signs)) if on]
        where = f" ({' and '.join(loosened)} ignored)" if loosened else ""
        for i, page in enumerate(self._normalized):
            if wanted in page:
                return f'source_span.text "{span.text}" is not on page {span.page} (it is on page {i + 1}){where}'
        return f'source_span.text "{span.text}" not found in the source document{where}'


@functools.lru_cache(maxsize=32)
def _source_of(text: str) -> Source:
    return Source(text)


def _source(info: ValidationInfo) -> Source:
    context = info.context or {}
    if isinstance(context.get("source"), Source):
        return context["source"]
    text = context.get("source_text")
    if isinstance(text, str):
        return _source_of(text)
    if isinstance(text, (list, tuple)) and all(isinstance(p, str) for p in text):
        return _source_of("\f".join(text))
    raise PydanticCustomError(
        "grounding",
        "no source document to ground against: validate with context={'source_text': ...} or {'source': Source(...)}",
    )


def _number_only(v: Any) -> Any:
    # A JSON number only: "123" and true are not amounts, however Pydantic would coerce them.
    if isinstance(v, bool) or not isinstance(v, (int, float, Decimal)):
        raise PydanticCustomError("number_type", "value must be a number, not {kind}", {"kind": type(v).__name__})
    # A float as written ("146.3", not 146.2999...), which older Pydantic versions do not guarantee.
    return Decimal(repr(v)) if isinstance(v, float) else v


class SourceSpan(BaseModel):
    """Where a value was read: the page (from 1) and the text quoted from it."""

    model_config = ConfigDict(extra="forbid")

    page: StrictInt = Field(ge=1, description="The page the quote is on, from 1")
    text: StrictStr = Field(min_length=1, description="The value copied character for character from that page")


class Grounded(BaseModel):
    """An evidence object: a value and the quote it was read from, checked against the source.

    Subclass it with a typed ``value`` field; ``GroundedNumber`` and ``GroundedText`` are the two
    built in. A subclass may override ``_value_problem`` to add its own value-against-quote check.
    """

    model_config = ConfigDict(extra="forbid")

    source_span: SourceSpan

    @property
    def quote(self) -> str:
        return self.source_span.text

    def _value_problem(self, source: Source) -> Optional[str]:
        return None

    @model_validator(mode="after")
    def _grounded(self, info: ValidationInfo) -> "Grounded":
        if (info.context or {}).get(_SHAPE_ONLY):
            return self
        source = _source(info)
        problem = source.ungrounded(self.source_span)
        if problem is not None:
            raise PydanticCustomError("grounding", problem)
        problem = self._value_problem(source)
        if problem is not None:
            raise PydanticCustomError("quote_value", problem)
        return self


class GroundedNumber(Grounded):
    """A number and its quote. With ``numbers_match_quotes`` (the default), the value must equal a
    number the quote prints. Numbers in the quote are read with "," as grouping and "." as the
    decimal point, and compared by size: the sign is left to other checks, since documents print
    negatives as "(1,203)", "-1,203" or "1,203 CR". The comparison is exact, in decimal.
    """

    model_config = ConfigDict(extra="forbid", json_schema_extra={
        "description": "A number from the source and the text it was read from; value is the number the text prints"})

    value: Annotated[Decimal, BeforeValidator(_number_only), Field(allow_inf_nan=False),
                     WithJsonSchema({"type": "number"})]

    def _value_problem(self, source: Source) -> Optional[str]:
        if not source.numbers_match_quotes:
            return None
        wanted = abs(self.value)
        printed = []
        for m in _NUMBER.finditer(self.quote):
            digits = m.group().replace(",", "")
            if not digits:
                continue
            if Decimal(digits) == wanted:
                return None
            printed.append(m.group())
        it_prints = f"it prints {', '.join(printed)}" if printed else "the quote has no number"
        return (f"value {_plain(self.value)} is not a number its quote prints ({it_prints}). "
                f"Use the number the quote prints; do not change a value to make another check pass")


class GroundedText(Grounded):
    """A text value (a date, a name, an identifier) and its quote."""

    model_config = ConfigDict(extra="forbid", json_schema_extra={
        "description": "A value from the source and the text it was read from"})

    value: StrictStr


class Violation(NamedTuple):
    """One failed cross-field check: the path it concerns, what is wrong, and the kind of check."""

    path: str
    message: str
    kind: str = "math"


Amount = Union[GroundedNumber, Decimal, int]


def _num(x: Amount) -> Decimal:
    return x.value if isinstance(x, GroundedNumber) else Decimal(x)


def _plain(n: Decimal) -> str:
    return format(n, "f")


def _places(n: Decimal) -> int:
    exponent = n.as_tuple().exponent
    return -exponent if isinstance(exponent, int) and exponent < 0 else 0


def sum_equals_rounded(items: Sequence[Amount], total: Amount, *, items_path: str, total_path: str,
                       tolerance: Union[Decimal, int, str] = 0) -> Optional[Violation]:
    """Items add up to their total, allowing for figures each rounded where they were printed.

    Each printed figure, the total included, may be off by half a unit of its last digit, so the
    allowed difference is ``0.5 * unit * (len(items) + 1)``, where ``unit`` is one in the last
    decimal place any of the figures has (1 for whole numbers), or ``tolerance`` if larger.
    Returns the violation, or None when the sum holds.
    """
    numbers = [_num(i) for i in items]
    printed_total = _num(total)
    total_sum = sum(numbers, Decimal(0))
    places = max([_places(printed_total), *(_places(n) for n in numbers)])
    half_unit = Decimal(1).scaleb(-places) / 2
    allowed = max(Decimal(tolerance), half_unit * (len(numbers) + 1))
    if abs(total_sum - printed_total) <= allowed:
        return None
    return Violation(total_path, (
        f"sum of {items_path} is {_plain(total_sum)} but {total_path} is {_plain(printed_total)} "
        f"(allowed for rounding {_plain(allowed)}). Check the items against the source: one may be "
        f"missing, extra, a subtotal or from another column. Keep {total_path} as the source prints it"))


def assert_sum_equals_rounded(items: Sequence[Amount], total: Amount, *, items_path: str = "items",
                              total_path: str = "total", tolerance: Union[Decimal, int, str] = 0) -> None:
    """``sum_equals_rounded`` for a plain Pydantic validator: raises ValueError when the sum fails."""
    violation = sum_equals_rounded(items, total, items_path=items_path, total_path=total_path, tolerance=tolerance)
    if violation is not None:
        raise ValueError(violation.message)


def equals(a: Amount, b: Amount, *, a_path: str, b_path: str,
           tolerance: Union[Decimal, int, str] = 0) -> Optional[Violation]:
    """Two figures are equal within ``tolerance``."""
    x, y = _num(a), _num(b)
    if abs(x - y) <= Decimal(tolerance):
        return None
    return Violation(a_path, f"{a_path} is {_plain(x)} but {b_path} is {_plain(y)} (tolerance {tolerance})")


def at_most(a: Amount, b: Amount, *, a_path: str, b_path: str) -> Optional[Violation]:
    """``a <= b``, such as a part never exceeding its whole."""
    x, y = _num(a), _num(b)
    if x <= y:
        return None
    return Violation(a_path, f"{a_path} is {_plain(x)}, more than {b_path} ({_plain(y)})")


def matches(value: str, pattern: str, *, path: str) -> Optional[Violation]:
    """The whole of ``value`` matches ``pattern``."""
    if re.fullmatch(pattern, value):
        return None
    return Violation(path, f'"{value}" does not match /{pattern}/', "regex")


def forbids(value: str, pattern: str, *, path: str) -> Optional[Violation]:
    """No part of ``value`` matches ``pattern`` (prose, apologies, placeholders)."""
    m = re.search(pattern, value)
    if m is None:
        return None
    return Violation(path, f'forbidden pattern /{pattern}/ matched "{m.group()}"', "regex")


class GroundedModel(BaseModel):
    """A model whose answer is judged as a whole: every field, every quote and every cross-field
    check, with every violation reported in one ``ValidationError``.

    Override ``checks()`` to yield the cross-field rules (``sum_equals_rounded``, ``at_most`` and
    the rest; a ``None`` is a rule that held). The checks also run when a field fails grounding, as
    long as the answer has the right shape, so a retry prompt sees the arithmetic and the quote
    problems together.
    """

    def checks(self) -> Iterable[Optional[Violation]]:
        return ()

    @model_validator(mode="wrap")
    @classmethod
    def _every_violation(cls, data: Any, handler: Any, info: ValidationInfo) -> Any:
        context = info.context or {}
        if context.get(_SHAPE_ONLY):
            return handler(data)
        errors: list[dict[str, Any]] = []
        try:
            model = handler(data)
        except ValidationError as e:
            errors.extend(_line_errors(e))
            try:
                model = cls.model_validate(data, context={**context, _SHAPE_ONLY: True})
            except ValidationError:
                model = None  # the wrong shape: the field errors already say why
        if model is not None:
            for v in model.checks():
                if v is not None:
                    errors.append({"type": PydanticCustomError(v.kind, v.message), "loc": (v.path,), "input": data})
        if errors:
            raise ValidationError.from_exception_data(cls.__name__, errors)
        return model


def _line_errors(e: ValidationError) -> list[dict[str, Any]]:
    return [{"type": PydanticCustomError(err["type"], err["msg"]), "loc": err["loc"], "input": err["input"]}
            for err in e.errors(include_url=False)]


def describe(error: ValidationError) -> str:
    """Every violation as one ``[kind] path: message`` line each, to quote in a retry prompt."""
    return "\n".join(
        f"[{err['type']}] {_path(err['loc'])}: {err['msg']}" for err in error.errors(include_url=False))


def _path(loc: tuple[Any, ...]) -> str:
    out = ""
    for part in loc:
        out += f"[{part}]" if isinstance(part, int) else (f".{part}" if out else str(part))
    return out
