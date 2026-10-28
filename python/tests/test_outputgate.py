"""The library's own rules, on small made-up answers of no particular domain."""

from decimal import Decimal
from typing import Annotated

import pytest
from pydantic import BaseModel, ValidationError, model_validator

from outputgate import (GroundedModel, GroundedNumber, GroundedText, Region, Source, assert_sum_equals_rounded,
                        describe, sum_equals_rounded, within)

SOURCE = "Report for week 42\nParts   1,200.5\nLabour   (300)\nShipping   $ 99\nTotal   999.5\fpage two: 7–8"


class Part(BaseModel):
    name: str
    amount: GroundedNumber


class Report(GroundedModel):
    parts: list[Part]
    total: GroundedNumber

    def checks(self):
        yield sum_equals_rounded([p.amount for p in self.parts], self.total, items_path="parts[*].amount",
                                 total_path="total")


def ev(value, text, page=1):
    return {"value": value, "source_span": {"page": page, "text": text}}


def report(parts, total):
    return {"parts": [{"name": n, "amount": a} for n, a in parts], "total": total}


def errors(model, data, context=None):
    with pytest.raises(ValidationError) as e:
        model.model_validate(data, context={"source_text": SOURCE} if context is None else context)
    return [(err["type"], err["loc"]) for err in e.value.errors()]


GOOD = report([("parts", ev(1200.5, "1,200.5")), ("labour", ev(-300, "(300)")), ("shipping", ev(99, "99"))],
              ev(999.5, "999.5"))


def test_a_grounded_answer_whose_sums_hold_is_accepted():
    r = Report.model_validate(GOOD, context={"source_text": SOURCE})
    assert r.total.value == Decimal("999.5") and r.total.quote == "999.5"


def test_json_input_and_whitespace_in_quotes():
    data = '{"value": 1200.5, "source_span": {"page": 1, "text": "Parts 1,200.5"}}'
    assert GroundedNumber.model_validate_json(data, context={"source_text": SOURCE}).value == Decimal("1200.5")


def test_no_source_document_fails_closed():
    assert errors(GroundedNumber, ev(99, "99"), context={}) == [("grounding", ())]


@pytest.mark.parametrize("span, why", [
    ({"page": 1, "text": "1,999"}, "not found in the source document"),
    ({"page": 2, "text": "999.5"}, "is not on page 2 (it is on page 1)"),
    ({"page": 3, "text": "999.5"}, "past the end of the document (2 pages)"),
])
def test_a_quote_must_be_on_the_page_it_names(span, why):
    with pytest.raises(ValidationError, match=__import__("re").escape(why)):
        GroundedNumber.model_validate({"value": 999.5, "source_span": span}, context={"source_text": SOURCE})


def test_a_value_must_be_a_number_its_quote_prints():
    # Scaled (a digit dropped or added), retyped, or a quote with no number at all.
    for value, quote in [(12005, "1,200.5"), (99.5, "99"), (7, "week")]:
        assert errors(GroundedNumber, ev(value, quote)) == [("quote_value", ())]
    # The sign is left to other checks: "(300)" grounds -300 and 300 alike.
    GroundedNumber.model_validate(ev(300, "(300)"), context={"source_text": SOURCE})


def test_quote_value_check_can_be_turned_off():
    source = Source(SOURCE, numbers_match_quotes=False)
    GroundedNumber.model_validate(ev(99000, "99"), context={"source": source})


def test_currency_signs_and_hyphens():
    with pytest.raises(ValidationError):
        GroundedNumber.model_validate(ev(1200.5, "$ 1,200.5"), context={"source_text": SOURCE})
    GroundedNumber.model_validate(ev(1200.5, "$ 1,200.5"),
                                  context={"source": Source(SOURCE, ignore_currency_signs=True)})
    GroundedText.model_validate(ev("7-8", "7-8", page=2), context={"source_text": SOURCE})
    with pytest.raises(ValidationError):
        GroundedText.model_validate(ev("7-8", "7-8", page=2), context={"source": Source(SOURCE, ignore_whitespace=False)})


@pytest.mark.parametrize("value", ["99", True, None, float("nan")])
def test_a_number_must_be_a_number(value):
    with pytest.raises(ValidationError):
        GroundedNumber.model_validate(ev(value, "99"), context={"source_text": SOURCE})


@pytest.mark.parametrize("bad", [
    99,                                                    # a bare value, no evidence
    {"value": 99},                                         # no quote
    {"value": 99, "source_span": {"page": 0, "text": "99"}},
    {"value": 99, "source_span": {"page": 1.0, "text": "99"}},
    {"value": 99, "source_span": {"page": 1, "text": ""}},
    {"value": 99, "source_span": {"page": 1, "text": "99"}, "note": "extra"},
])
def test_evidence_must_be_well_formed(bad):
    with pytest.raises(ValidationError):
        GroundedNumber.model_validate(bad, context={"source_text": SOURCE})


def test_every_violation_is_reported_together():
    # A line is scaled and the total retyped, so both fail their quotes and the sum fails too:
    # every one is listed, with its path.
    data = report([("parts", ev(12005, "1,200.5")), ("labour", ev(-300, "(300)"))], ev(11800, "999.5"))
    assert errors(Report, data) == [
        ("quote_value", ("parts", 0, "amount")),
        ("quote_value", ("total",)),
        ("math", ("total",)),
    ]


def test_arithmetic_message_steers_a_retry_to_the_items():
    data = report([("parts", ev(1200.5, "1,200.5"))], ev(999.5, "999.5"))
    with pytest.raises(ValidationError) as e:
        Report.model_validate(data, context={"source_text": SOURCE})
    assert describe(e.value) == (
        "[math] total: sum of parts[*].amount is 1200.5 but total is 999.5 (allowed for rounding 0.10). "
        "Check the items against the source: one may be missing, extra, a subtotal or from another column. "
        "Keep total as the source prints it")


def test_wrong_shape_reports_the_shape_only():
    assert errors(Report, {"parts": "none", "total": ev(999.5, "999.5")}) == [("list_type", ("parts",))]


@pytest.mark.parametrize("items, total, ok", [
    ([1, 1, 1], 5, True),                    # 3 lines + total, half a unit each: 2.0 allowed
    ([1, 1, 1], 6, False),
    (["0.1", "0.1"], "0.3", True),           # one decimal place: 0.05 each, 0.15 allowed
    (["0.1", "0.1"], "0.4", False),
    ([31190], 31192, False),                 # IBM's 2 needs more than one line's worth of rounding
])
def test_sum_equals_rounded_allows_half_a_unit_per_figure(items, total, ok):
    v = sum_equals_rounded([Decimal(i) for i in items], Decimal(total), items_path="i", total_path="t")
    assert (v is None) == ok


def test_assert_sum_equals_rounded_in_a_plain_pydantic_validator():
    class Plain(BaseModel):
        parts: list[GroundedNumber]
        total: GroundedNumber

        @model_validator(mode="after")
        def adds_up(self):
            assert_sum_equals_rounded(self.parts, self.total, items_path="parts", total_path="total")
            return self

    ok = {"parts": [ev(1200.5, "1,200.5"), ev(-300, "(300)"), ev(99, "99")], "total": ev(999.5, "999.5")}
    Plain.model_validate(ok, context={"source_text": SOURCE})
    ok["parts"].pop()
    with pytest.raises(ValidationError, match="sum of parts is 900.5"):
        Plain.model_validate(ok, context={"source_text": SOURCE})


# A parent's figures, then a subsidiary's, with the same labels and nearly the same numbers.
TWO_TABLES = ("Quarterly results - Northwind Group\nRevenue   1,200\nCosts   900\nProfit   300\n"
              "Northwind Retail (subsidiary)\nRevenue   1,200\nCosts   905\nProfit   295\n")
GROUP = Region(before=r"\(subsidiary\)")


class Results(GroundedModel):
    revenue: Annotated[GroundedNumber, within(GROUP)]
    costs: Annotated[GroundedNumber, within(GROUP)]
    profit: GroundedNumber


def results(costs, profit):
    return {"revenue": ev(1200, "1,200"), "costs": ev(costs, f"Costs   {costs}"), "profit": ev(profit, str(profit))}


def test_quotes_from_the_right_table_are_accepted():
    Results.model_validate(results(900, 300), context={"source_text": TWO_TABLES})


def test_a_quote_from_the_other_table_is_rejected_though_it_is_on_the_page():
    # profit is not scoped, so its quote from the subsidiary's table passes.
    assert errors(Results, results(905, 295), {"source_text": TWO_TABLES}) == [("region", ("costs",))]
    with pytest.raises(ValidationError) as e:
        Results.model_validate(results(905, 300), context={"source_text": TWO_TABLES})
    assert '"Costs   905" is on page 1 but not inside' in describe(e.value)
    assert "page 1, lines 1 to 4" in describe(e.value)


def test_region_start_and_end_lines():
    sub = Region(start=r"\(subsidiary\)")
    pages = (TWO_TABLES,)
    assert sub.locate(pages) == (0, 4, 0, 8)
    assert Region(through="^Profit").locate(pages) == (0, 0, 0, 3)
    assert Region(before="^Profit").locate(pages) == (0, 0, 0, 2)
    assert Region(through="Grand total").locate(pages) == (0, 0, 0, 8)  # no end line: to the end
    assert Region(start="Balance sheet").locate(pages) is None
    assert Region(start="^Table A", through="^End of table A").locate(
        ("Cover\nTable A\nRow   10", "Row   20\nEnd of table A\nTable B")) == (0, 1, 1, 1)
    with pytest.raises(ValueError):
        Region(through="a", before="b")


def test_a_region_not_in_the_document_fails_closed():
    class Scoped(GroundedModel):
        costs: Annotated[GroundedNumber, within(Region(start="Balance sheet"))]

    with pytest.raises(ValidationError) as e:
        Scoped.model_validate({"costs": ev(900, "Costs   900")}, context={"source_text": TWO_TABLES})
    assert "not in the source document" in describe(e.value)


def test_a_region_uses_the_sources_loosening_and_runs_across_pages():
    class Row(GroundedModel):
        row: Annotated[GroundedNumber, within(Region(start="^Table A", through="^End of table A"))]

    doc = Source(("Cover\nTable A\nRow   10", "Row   $20\nEnd of table A\nTable B\nRow   30"),
                 ignore_currency_signs=True)
    Row.model_validate({"row": ev(20, "Row 20", page=2)}, context={"source": doc})
    with pytest.raises(ValidationError) as e:
        Row.model_validate({"row": ev(30, "Row 30", page=2)}, context={"source": doc})
    assert "page 1 line 2 to page 2 line 2" in describe(e.value)
