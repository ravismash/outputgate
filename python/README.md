# outputgate for Python

Deterministic checks for structured LLM output, as Pydantic v2 types. Each value carries the quote
it was read from; the quote must be in the source document, a number must be one its quote prints,
and parts must add up to their totals within the rounding the source used. No check calls a model.
See [`FRD.md`](../FRD.md) for the requirements.

```python
from outputgate import GroundedModel, GroundedNumber, describe, sum_equals_rounded
from pydantic import BaseModel, ValidationError


class Part(BaseModel):
    name: str
    amount: GroundedNumber          # {"value": 1200.5, "source_span": {"page": 1, "text": "1,200.5"}}


class Report(GroundedModel):
    parts: list[Part]
    total: GroundedNumber

    def checks(self):
        yield sum_equals_rounded([p.amount for p in self.parts], self.total,
                                 items_path="parts[*].amount", total_path="total")


try:
    report = Report.model_validate_json(answer, context={"source_text": document})
except ValidationError as e:
    retry_prompt = describe(e)      # every violation, one "[kind] path: message" line each
```

`context={"source": Source(pages, ignore_currency_signs=True)}` sets grounding options; see the
`Source` docstring. When a document prints the same labels in more than one place (a summary and a
detail table, a parent's statement and a subsidiary's), scope a field to the part it must come from
with `Annotated[GroundedNumber, within(Region(start=r"^Detail", before=r"^Notes"))]`: its quote
must then be inside that region on its page, not just somewhere on the page. [`examples/balance_sheet.py`](examples/balance_sheet.py) is the SEC balance-sheet
contract from `outputgate-eval`, written with these types.

## Tests

```bash
cd python
pip install -r requirements-dev.txt
pytest
```

[`tests/test_benchmark_cases.py`](tests/test_benchmark_cases.py) runs on the answers the models gave
in the eval runs and checks that every verdict matches the Java contract's.
