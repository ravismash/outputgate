"""SEC balance-sheet extraction: schema-valid is not correct.

Each sample is the balance-sheet statement of a company's latest SEC 10-Q, as EDGAR's XBRL
viewer prints it. The model extracts the period date, the current-asset and current-liability
lines and four totals as JSON. The answer is scored three ways:

* schema_valid: the answer parses and matches the JSON Schema
  (outputgate-eval/src/main/resources/schemas/balance-sheet.schema.json).
* consistent: the answer passes the arithmetic checks a contract can run without ground truth
  (lines add up to their totals within rounding, assets equal liabilities and equity, current
  totals within the overall totals).
* correct: all four totals equal the filing's own XBRL values.

The headline is the gap between the first and the last: an answer can match the schema and still
be wrong. ``valid_but_wrong`` counts those answers, and ``caught_by_checks`` says how many of
them the ground-truth-free checks would have rejected.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Literal

from inspect_ai import Task, task
from inspect_ai.dataset import MemoryDataset, Sample
from inspect_ai.solver import generate, system_message

from .scorer import balance_sheet_scorer

DATASETS = Path(__file__).resolve().parents[2] / "outputgate-eval" / "datasets"
SPLITS = {"tuning": "sec-balance-sheets", "holdout": "sec-balance-sheets-holdout"}
TOTALS = ("total_current_assets", "total_assets", "total_current_liabilities", "total_liabilities_and_equity")

SYSTEM = """You extract data from financial statements. Answer with one JSON object and nothing else."""

INSTRUCTIONS = """Extract this balance sheet as one JSON object and nothing else: no prose, no code fences.
The statement has a column per period. Use only the FIRST amount column (the latest period).
Fields:
  period_end: the latest period's date as yyyy-MM-dd (the first date in the heading).
  current_assets: the lines that add up to the total current assets, as a list of
    {"label": <text>, "amount": <number>}. When a group of lines has its own subtotal inside
    current assets (for example "Total inventories"), list the subtotal and not the lines in it,
    so the amounts add up to the total exactly once.
  total_current_assets, total_assets.
  current_liabilities: the lines that add up to the total current liabilities, same rules.
  total_current_liabilities.
  total_liabilities_and_equity: the last line, total liabilities and (stockholders' or shareholders') equity.
Amounts are plain JSON numbers in the statement's own units, not scaled: "$ 39,544" is 39544.
An amount in parentheses is negative: "(1,203)" is -1203.
Do not compute or guess a value that is not printed.

Balance sheet:
"""


def load_samples(split: Literal["all", "tuning", "holdout"] = "all") -> list[Sample]:
    """Builds one sample per filing that has XBRL values for all four totals."""
    names = list(SPLITS) if split == "all" else [split]
    samples = []
    for name in names:
        folder = DATASETS / SPLITS[name] / "documents"
        truth = json.loads((folder / "ground-truth.json").read_text(encoding="utf-8"))
        for document, values in sorted(truth.items()):
            if not values or any(values.get(k) is None for k in TOTALS):
                continue
            text = (folder / document).read_text(encoding="utf-8")
            samples.append(
                Sample(
                    id=document.removesuffix(".txt"),
                    input=INSTRUCTIONS + text,
                    target=json.dumps({k: values[k] for k in TOTALS}),
                    metadata={"split": name, "document": document},
                )
            )
    return samples


@task
def sec_balance_sheets(split: Literal["all", "tuning", "holdout"] = "all") -> Task:
    """Balance-sheet extraction from SEC 10-Q statements, scored against XBRL.

    Args:
        split: "tuning" (60 filings, 58 with full ground truth), "holdout" (60 other filings,
            55 with full ground truth) or "all".
    """
    return Task(
        dataset=MemoryDataset(load_samples(split), name=f"sec_balance_sheets_{split}"),
        solver=[system_message(SYSTEM), generate()],
        scorer=balance_sheet_scorer(),
    )
