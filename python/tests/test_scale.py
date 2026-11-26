"""Units: values from a table printed in a scale must be stated in base units."""

import pytest
from pydantic import ValidationError

from outputgate import GroundedModel, GroundedScale, ScaledNumber, describe, scaled

IN_THOUSANDS = ("BALANCE SHEETS\n(in thousands, except per share data)\n"
                "Total current assets   13,226\nTotal assets   48,310")
NO_SCALE = "Order summary\nTotal   1,250.00"


class Sheet(GroundedModel):
    units: GroundedScale
    total_assets: ScaledNumber
    total_current_assets: ScaledNumber

    def checks(self):
        yield scaled(self.total_assets, self.units, path="total_assets")
        yield scaled(self.total_current_assets, self.units, path="total_current_assets")


def ev(value, text, page=1):
    return {"value": value, "source_span": {"page": page, "text": text}}


def sheet(scale, scale_quote, assets, current):
    return {"units": ev(scale, scale_quote), "total_assets": ev(assets, "Total assets   48,310"),
            "total_current_assets": ev(current, "Total current assets   13,226")}


def problems(data, source=IN_THOUSANDS):
    with pytest.raises(ValidationError) as e:
        Sheet.model_validate(data, context={"source_text": source})
    return describe(e.value)


def test_base_units_with_the_scale_quoted_pass():
    Sheet.model_validate(sheet(1000, "(in thousands, except per share data)", 48310000, 13226000),
                         context={"source_text": IN_THOUSANDS})


def test_a_value_copied_as_printed_is_rejected():
    msg = problems(sheet(1000, "(in thousands, except per share data)", 48310, 13226000))
    assert msg == ("[scale] total_assets: value 48310 is not a number its quote prints times the scale 1000 "
                   "(48,310 (48310000 at that scale))")


def test_scale_one_on_a_page_that_states_thousands_is_rejected():
    msg = problems(sheet(1, "BALANCE SHEETS", 48310, 13226))
    assert "scale is 1 but page 1 states figures in thousands" in msg


def test_the_scale_quote_must_state_the_scale():
    assert "says thousands" in problems(sheet(1000000, "(in thousands, except per share data)", 48310000000,
                                              13226000000))
    assert "states no scale" in problems(sheet(1000, "BALANCE SHEETS", 48310000, 13226000))


def test_an_odd_scale_is_rejected():
    assert "scale must be 1, 1000" in problems(sheet(500, "(in thousands, except per share data)", 24155000,
                                                     6613000))


def test_a_scaled_number_off_every_scale_is_rejected_on_its_own():
    assert "not a number its quote prints, at any scale" in problems(
        sheet(1000, "(in thousands, except per share data)", 48310001, 13226000))


def test_scale_one_passes_where_no_scale_is_stated():
    class Order(GroundedModel):
        units: GroundedScale
        total: ScaledNumber

        def checks(self):
            yield scaled(self.total, self.units, path="total")

    Order.model_validate({"units": ev(1, "Order summary"), "total": ev(1250, "Total   1,250.00")},
                         context={"source_text": NO_SCALE})


@pytest.mark.parametrize("quote", ["(in thousands)", "($ in thousands)", "(In Thousands of U.S. Dollars)",
                                   "in USD thousands", "(thousands)", "Amounts in thousands", "(₹ in thousands)"])
def test_common_ways_of_stating_thousands(quote):
    source = f"{quote}\nTotal assets   48,310\nTotal current assets   13,226"
    data = {"units": ev(1000, quote), "total_assets": ev(48310000, "Total assets   48,310"),
            "total_current_assets": ev(13226000, "Total current assets   13,226")}
    Sheet.model_validate(data, context={"source_text": source})


def test_prose_is_not_a_scale_statement():
    source = "We serve thousands of customers.\nTotal assets   48,310\nTotal current assets   13,226"
    data = {"units": ev(1, "We serve thousands of customers."), "total_assets": ev(48310, "Total assets   48,310"),
            "total_current_assets": ev(13226, "Total current assets   13,226")}
    Sheet.model_validate(data, context={"source_text": source})
