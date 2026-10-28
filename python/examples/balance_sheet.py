"""Example: a company balance sheet, the shape used by the SEC 10-Q eval datasets.

The same checks as the Java contract in outputgate-eval (``BalanceSheet.contract()``): lines add
up to their totals allowing for rounding, assets equal liabilities and equity, current within
total, every amount quoted from the statement and equal to the number its quote prints, an ISO
date, and no prose in labels. Nothing here is specific to balance sheets except the field names;
any structured answer with parts and totals is written the same way.
"""

from __future__ import annotations

from outputgate import GroundedModel, GroundedNumber, GroundedText, Source, at_most, equals, forbids, matches, \
    sum_equals_rounded
from pydantic import BaseModel, ConfigDict, StrictStr

# Prose, apologies and placeholders that are never a line's label.
NOT_A_LABEL = r"(?i)\b(sorry|unknown|n/a|not (found|printed|available)|as an ai)\b"


class Line(BaseModel):
    model_config = ConfigDict(extra="forbid")

    label: StrictStr
    amount: GroundedNumber


class BalanceSheet(GroundedModel):
    model_config = ConfigDict(extra="forbid")

    period_end: GroundedText
    current_assets: list[Line]
    total_current_assets: GroundedNumber
    total_assets: GroundedNumber
    current_liabilities: list[Line]
    total_current_liabilities: GroundedNumber
    total_liabilities_and_equity: GroundedNumber

    def checks(self):
        # Statements round every figure, so lines may miss their total by half a unit each
        # (IBM's and TMO's by 2); a fixed tolerance of 1 rejected those correct answers.
        yield sum_equals_rounded([l.amount for l in self.current_assets], self.total_current_assets,
                                 items_path="current_assets[*].amount", total_path="total_current_assets")
        yield sum_equals_rounded([l.amount for l in self.current_liabilities], self.total_current_liabilities,
                                 items_path="current_liabilities[*].amount", total_path="total_current_liabilities")
        yield equals(self.total_assets, self.total_liabilities_and_equity, tolerance=1,
                     a_path="total_assets", b_path="total_liabilities_and_equity")
        yield at_most(self.total_current_assets, self.total_assets,
                      a_path="total_current_assets", b_path="total_assets")
        yield at_most(self.total_current_liabilities, self.total_liabilities_and_equity,
                      a_path="total_current_liabilities", b_path="total_liabilities_and_equity")
        yield matches(self.period_end.value, r"\d{4}-\d{2}-\d{2}", path="period_end")
        for section in ("current_assets", "current_liabilities"):
            for i, line in enumerate(getattr(self, section)):
                yield forbids(line.label, NOT_A_LABEL, path=f"{section}[{i}].label")


def source(statement: str) -> Source:
    """The statement as the eval contract grounds it: whitespace and currency signs ignored."""
    return Source(statement, ignore_whitespace=True, ignore_currency_signs=True)
