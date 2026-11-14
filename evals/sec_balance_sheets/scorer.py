"""Scoring for the SEC balance-sheet eval: schema validity, ground-truth-free checks, XBRL truth."""

from __future__ import annotations

import json
import math
import re
from decimal import Decimal
from pathlib import Path
from typing import Any

from inspect_ai.scorer import Score, Target, mean, scorer, stderr
from inspect_ai.solver import TaskState
from jsonschema import Draft202012Validator

SCHEMA_PATH = (
    Path(__file__).resolve().parents[2]
    / "outputgate-eval"
    / "src"
    / "main"
    / "resources"
    / "schemas"
    / "balance-sheet.schema.json"
)
VALIDATOR = Draft202012Validator(json.loads(SCHEMA_PATH.read_text(encoding="utf-8")))
FENCE = re.compile(r"^\s*```(?:json)?\s*(.*?)\s*```\s*$", re.DOTALL)
ISO_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def parse(completion: str) -> Any | None:
    """Parses the answer as JSON. A single surrounding code fence is tolerated; nothing else is."""
    text = completion.strip()
    fenced = FENCE.match(text)
    if fenced:
        text = fenced.group(1)
    try:
        return json.loads(text, parse_float=Decimal, parse_int=Decimal)
    except (ValueError, RecursionError):
        return None


def schema_valid(answer: Any) -> bool:
    if answer is None:
        return False
    # The validator needs plain numbers; Decimal keeps the printed digits for the rounding check.
    plain = json.loads(json.dumps(answer, default=float))
    return VALIDATOR.is_valid(plain)


def _unit(amount: Decimal) -> Decimal:
    """The value of the last printed digit: 1 for 39544, 0.1 for 13226.6."""
    exponent = amount.normalize().as_tuple().exponent
    return Decimal(1).scaleb(min(int(exponent), 0)) if isinstance(exponent, int) else Decimal(1)


def _sums_within_rounding(lines: list[dict[str, Any]], total: Decimal) -> bool:
    amounts = [line["amount"] for line in lines]
    unit = min([_unit(a) for a in amounts + [total]])
    # Each printed figure is rounded, so the lines may miss the printed total by half a unit each.
    tolerance = unit / 2 * (len(amounts) + 1)
    return abs(sum(amounts, Decimal(0)) - total) <= tolerance


def consistent(answer: dict[str, Any]) -> bool:
    """Checks that need no ground truth, as an output contract would run them. Assumes schema_valid."""
    return (
        bool(ISO_DATE.match(answer["period_end"]))
        and _sums_within_rounding(answer["current_assets"], answer["total_current_assets"])
        and _sums_within_rounding(answer["current_liabilities"], answer["total_current_liabilities"])
        and answer["total_assets"] == answer["total_liabilities_and_equity"]
        and answer["total_current_assets"] <= answer["total_assets"]
        and answer["total_current_liabilities"] <= answer["total_liabilities_and_equity"]
    )


def correct(answer: dict[str, Any], truth: dict[str, float]) -> bool:
    return all(
        math.isclose(float(answer[key]), float(value), rel_tol=1e-9, abs_tol=1e-6) for key, value in truth.items()
    )


@scorer(
    metrics={
        "correct": [mean(), stderr()],
        "schema_valid": [mean(), stderr()],
        "consistent": [mean(), stderr()],
        "valid_but_wrong": [mean(), stderr()],
        "valid_but_wrong_caught": [mean()],
        "valid_but_wrong_missed": [mean()],
    }
)
def balance_sheet_scorer():
    """Scores each answer on schema validity, the ground-truth-free checks and the XBRL totals.

    valid_but_wrong = schema-valid answers whose totals differ from XBRL. It splits into
    valid_but_wrong_caught (the checks reject it) and valid_but_wrong_missed (the checks accept it).
    """

    async def score(state: TaskState, target: Target) -> Score:
        answer = parse(state.output.completion)
        truth = json.loads(target.text)
        valid = schema_valid(answer)
        is_consistent = valid and consistent(answer)
        is_correct = valid and correct(answer, truth)
        wrong_but_valid = valid and not is_correct
        return Score(
            value={
                "correct": float(is_correct),
                "schema_valid": float(valid),
                "consistent": float(is_consistent),
                "valid_but_wrong": float(wrong_but_valid),
                "valid_but_wrong_caught": float(wrong_but_valid and not is_consistent),
                "valid_but_wrong_missed": float(wrong_but_valid and is_consistent),
            },
            answer=state.output.completion,
            explanation=(
                f"schema_valid={valid} consistent={is_consistent} correct={is_correct}; "
                f"expected {truth}"
            ),
        )

    return score
