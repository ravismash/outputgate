import json
from decimal import Decimal

from inspect_ai import eval as inspect_eval
from inspect_ai.model import ModelOutput, get_model

from sec_balance_sheets.scorer import consistent, correct, parse, schema_valid
from sec_balance_sheets.sec_balance_sheets import load_samples, sec_balance_sheets

AAPL = {
    "period_end": "2026-06-27",
    "current_assets": [
        {"label": "Cash and cash equivalents", "amount": 39544},
        {"label": "Marketable securities", "amount": 22855},
        {"label": "Accounts receivable, net", "amount": 31398},
        {"label": "Vendor non-trade receivables", "amount": 27509},
        {"label": "Inventories", "amount": 11092},
        {"label": "Other current assets", "amount": 17420},
    ],
    "total_current_assets": 149818,
    "total_assets": 383266,
    "current_liabilities": [
        {"label": "Accounts payable", "amount": 64525},
        {"label": "Other current liabilities", "amount": 62259},
        {"label": "Deferred revenue", "amount": 9538},
        {"label": "Commercial paper", "amount": 1997},
        {"label": "Term debt", "amount": 11007},
    ],
    "total_current_liabilities": 149326,
    "total_liabilities_and_equity": 383266,
}
AAPL_TRUTH = {
    "total_current_assets": 149818.0,
    "total_assets": 383266.0,
    "total_current_liabilities": 149326.0,
    "total_liabilities_and_equity": 383266.0,
}


def answer(**changes):
    return parse(json.dumps({**AAPL, **changes}))


def test_samples_cover_both_splits_with_full_ground_truth():
    assert len(load_samples("tuning")) == 58
    assert len(load_samples("holdout")) == 55
    assert len(load_samples("all")) == 113


def test_correct_answer_passes_everything():
    a = answer()
    assert schema_valid(a) and consistent(a) and correct(a, AAPL_TRUTH)


def test_code_fence_is_tolerated_but_prose_is_not():
    assert schema_valid(parse("```json\n" + json.dumps(AAPL) + "\n```"))
    assert parse("Here is the JSON: " + json.dumps(AAPL)) is None


def test_scaled_answer_is_schema_valid_and_consistent_but_wrong():
    # Every amount times 1,000: sums still hold, so only ground truth (or a quote check) catches it.
    scaled = json.loads(json.dumps(AAPL))
    for key in ("current_assets", "current_liabilities"):
        for line in scaled[key]:
            line["amount"] *= 1000
    for key in AAPL_TRUTH:
        scaled[key] *= 1000
    a = parse(json.dumps(scaled))
    assert schema_valid(a) and consistent(a) and not correct(a, AAPL_TRUTH)


def test_dropped_line_is_schema_valid_but_caught_by_the_sum():
    a = answer(current_liabilities=AAPL["current_liabilities"][:-1])
    assert schema_valid(a) and not consistent(a)


def test_rounding_allows_half_a_unit_per_figure():
    lines = [{"label": "A", "amount": 1.4}, {"label": "B", "amount": 1.4}]
    a = answer(current_assets=lines, total_current_assets=2.9)
    assert consistent(a)
    a = answer(current_assets=lines, total_current_assets=3.0)
    assert not consistent(a)


def test_wrong_type_fails_schema():
    assert not schema_valid(answer(total_assets="383,266"))
    assert not schema_valid(answer(extra=1))


def test_decimal_amounts_compare_exactly():
    assert correct({k: Decimal(str(v)) for k, v in AAPL_TRUTH.items()}, AAPL_TRUTH)


def test_end_to_end_with_mock_model():
    outputs = [ModelOutput.from_content("mockllm/model", json.dumps(AAPL)), ModelOutput.from_content("mockllm/model", "sorry")]
    model = get_model("mockllm/model", custom_outputs=outputs)
    task = sec_balance_sheets(split="tuning")
    task.dataset = task.dataset.filter(lambda s: s.id in ("AAPL-2026-06-27", "ABBV-2026-06-30"))
    [log] = inspect_eval(task, model=model, display="none", log_dir="/tmp/inspect-test-logs")
    assert log.status == "success"
    metrics = {s.name: {k: v.value for k, v in s.metrics.items()} for s in log.results.scores}
    flat = {f"{name}/{k}": v for name, m in metrics.items() for k, v in m.items()}
    values = {k.split("/")[0]: v for k, v in flat.items() if k.endswith("mean")}
    assert values["correct"] == 0.5
    assert values["schema_valid"] == 0.5
