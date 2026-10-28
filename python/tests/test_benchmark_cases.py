"""The benchmark's cases, on the answers the models really gave.

Every answer here is read from the eval datasets in outputgate-eval (results.jsonl and repair.jsonl
per model), checked against the statement it was extracted from, with the same rules as the Java
contract (examples/balance_sheet.py).
"""

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from balance_sheet import BalanceSheet, source
from outputgate import describe

DATASETS = Path(__file__).resolve().parents[2] / "outputgate-eval" / "datasets"
FIRST, HOLDOUT = "sec-balance-sheets", "sec-balance-sheets-holdout"
MODELS = ["gemini-3.1-pro-preview", "gemini-3.5-flash", "gemini-3.8-flash", "qwen2.5-14b"]


def answers(dataset, model, kind="results"):
    with open(DATASETS / dataset / "runs" / model / f"{kind}.jsonl") as f:
        return [json.loads(line) for line in f if line.strip()]


def answer(dataset, model, ticker, kind="results"):
    return next(r for r in answers(dataset, model, kind) if r["file"].startswith(ticker + "-"))


def judge(dataset, record):
    """The validation errors as (kind, path) pairs, empty when the answer is accepted."""
    statement = (DATASETS / dataset / "documents" / record["file"]).read_text()
    try:
        BalanceSheet.model_validate_json(record["output"], context={"source": source(statement)})
        return []
    except ValidationError as e:
        print(describe(e))
        return [(err["type"], ".".join(str(p) for p in err["loc"])) for err in e.errors()]


def test_tsla_retry_that_rewrites_the_total_is_rejected():
    # First pass: two non-current items added, so the lines sum to 78,902 against a printed 70,402.
    first = judge(FIRST, answer(FIRST, "qwen2.5-14b", "TSLA"))
    assert first == [("math", "total_current_assets")]
    # The retry left the lines alone and changed the total to 78902, still quoting "70,402". The sum
    # now holds, so arithmetic alone would accept it; 78,902 is not printed anywhere, so the quote
    # check rejects it.
    retry = answer(FIRST, "qwen2.5-14b", "TSLA", "repair")
    assert '"total_current_assets": {"value": 78902' in retry["output"]
    assert judge(FIRST, retry) == [("quote_value", "total_current_assets")]


def test_kdp_dropped_digit_is_rejected_by_quote_and_arithmetic():
    # 8,761,900,000 for the printed "87,619,000,000", in total assets and in total liabilities and equity.
    errors = judge(HOLDOUT, answer(HOLDOUT, "gemini-3.8-flash", "KDP"))
    assert sorted(errors) == [
        ("math", "total_current_assets"),            # current assets now exceed total assets
        ("math", "total_current_liabilities"),       # and current liabilities exceed the whole
        ("quote_value", "total_assets"),
        ("quote_value", "total_liabilities_and_equity"),
    ]


@pytest.mark.parametrize("model", MODELS)
@pytest.mark.parametrize("ticker, items, total", [
    ("IBM", "current_assets", "total_current_assets"),
    ("TMO", "current_liabilities", "total_current_liabilities"),
])
def test_rounded_lines_that_miss_their_total_by_two_are_accepted(model, ticker, items, total):
    record = answer(FIRST, model, ticker)
    data = json.loads(record["output"])
    lines = sum(line["amount"]["value"] for line in data[items])
    assert abs(lines - data[total]["value"]) == 2  # each figure rounded to millions where printed
    assert judge(FIRST, record) == []


# Every answer the Java contract rejects, as (dataset, model, ticker); the rest it accepts.
REJECTED = {
    (FIRST, "gemini-3.8-flash", "BBY"),
    (FIRST, "qwen2.5-14b", "BBY"),
    (FIRST, "qwen2.5-14b", "DRI"),
    (FIRST, "qwen2.5-14b", "GOOGL"),
    (FIRST, "qwen2.5-14b", "MO"),
    (FIRST, "qwen2.5-14b", "TSLA"),
    (FIRST, "qwen2.5-14b", "TXN"),
    (HOLDOUT, "gemini-3.8-flash", "KDP"),
    (HOLDOUT, "qwen2.5-14b", "ADP"),
    (HOLDOUT, "qwen2.5-14b", "EXC"),
    (HOLDOUT, "qwen2.5-14b", "MCHP"),
    (HOLDOUT, "qwen2.5-14b", "PSX"),
}


@pytest.mark.parametrize("dataset", [FIRST, HOLDOUT])
@pytest.mark.parametrize("model", MODELS)
def test_same_verdict_as_the_java_contract_on_every_recorded_answer(dataset, model):
    records = answers(dataset, model)
    assert len(records) == 60
    rejected = {r["file"].split("-")[0] for r in records if judge(dataset, r)}
    assert rejected == {t for d, m, t in REJECTED if (d, m) == (dataset, model)}


@pytest.mark.parametrize("dataset", [FIRST, HOLDOUT])
@pytest.mark.parametrize("model", MODELS)
def test_same_verdict_as_the_java_contract_on_every_retry(dataset, model):
    for record in answers(dataset, model, "repair"):
        assert (judge(dataset, record) == []) == record["recovered"], record["file"]
