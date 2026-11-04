"""Builds docs/index.html, the benchmark case viewer, from the recorded runs.

Every answer, verdict and violation on the page is read from the eval datasets (results.jsonl and
repair.jsonl per model, the statements and ground-truth.json). The one exception is CTVA under the
region rule added after the held-out run: that verdict comes from re-scoring the recorded answer
(python/tests/test_benchmark_cases.py checks the same result), and the page labels it post-hoc.

    python3 -I outputgate-eval/scripts/build_viewer.py
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATASETS = ROOT / "outputgate-eval" / "datasets"
TEMPLATE = Path(__file__).with_name("viewer_template.html")
OUT = ROOT / "docs" / "index.html"

# BalanceSheet.STATEMENT: from the top of the page through the first liabilities-and-equity total.
STATEMENT_END = re.compile(r"(?i)liabilit.*\band\b.*(equity|deficit|investment|capital).*\d")

# The region verdict on CTVA, as re-scoring the recorded answer with the current contract prints it.
CTVA_REGION = ('source_span.text "10,795" is on page 1 but not inside the part of the document this field '
               'must be quoted from (page 1, lines 1 to 35: from the start of the document through the next '
               'line matching /(?i)liabilit.*\\band\\b.*(equity|deficit|investment|capital).*\\d/); quote '
               'the value printed there')


def record(dataset, model, ticker, kind):
    with open(DATASETS / dataset / "runs" / model / f"{kind}.jsonl") as f:
        for line in f:
            r = json.loads(line)
            if r["file"].startswith(ticker + "-"):
                return r
    raise KeyError(ticker)


def document(dataset, file, assets, liabilities):
    """The statement, its XBRL totals, and the lines (by line number) that make up each total."""
    lines = (DATASETS / dataset / "documents" / file).read_text().splitlines()
    truth = json.loads((DATASETS / dataset / "documents" / "ground-truth.json").read_text())[file]
    end = next(i for i, line in enumerate(lines) if STATEMENT_END.search(line)) + 1

    def printed(numbers, total):
        out = []
        for n in numbers:
            label, value = re.split(r"\s{3,}", lines[n - 1])[:2]
            out.append({"line": n, "label": label, "value": float(value.replace("$", "").replace(",", "").strip())})
        # The lines must add up to the filing's own total, or this list is not the statement's.
        assert abs(sum(x["value"] for x in out) - truth[total]) < 0.05, (file, total)
        return out

    return {"lines": lines, "truth": truth, "statement": [1, end],
            "expected": {"current_assets": printed(assets, "total_current_assets"),
                         "current_liabilities": printed(liabilities, "total_current_liabilities")}}


def state(key, label, output, outcome, violations, note, post_hoc=False, recorded=True):
    """recorded: this output is a first answer, which benchmark.md scores schema-only too (all 60 accepted)."""
    return {"key": key, "label": label, "output": json.loads(output), "outcome": outcome, "schemaOnly": recorded,
            "violations": [{"gate": v["gate"], "path": v["path"], "message": v["message"]} for v in violations],
            "note": note, "postHoc": post_hoc}


def cases():
    holdout, first, qwen = "sec-balance-sheets-holdout", "sec-balance-sheets", "qwen2.5-14b"

    exc, exc_retry = record(holdout, qwen, "EXC", "results"), record(holdout, qwen, "EXC", "repair")
    ctva = record(holdout, qwen, "CTVA", "results")
    dri, dri_retry = record(first, qwen, "DRI", "results"), record(first, qwen, "DRI", "repair")
    dri_current = dri_retry["violations_before"]  # the first answer, judged by the current contract

    return [
        {"id": "exc", "ticker": "EXC", "company": "Exelon", "dataset": holdout, "datasetLabel": "Held-out set",
         "model": qwen, "file": exc["file"], "kind": "Retry rewrote its totals",
         "summary": "The answer left out five current-asset lines and added a \"Total inventories, net\" of 1,827 "
                    "that the page does not print, so its lines summed to 8,507 against a printed 9,797. On the "
                    "liability side it left out one line and the two accounts payable printed in blocks below the "
                    "table, so they summed to 5,524 against 9,008. Given the violations, the retry kept its lines "
                    "and changed both totals to match its own sums. Arithmetic passes after that edit. The quote "
                    "check does not, because each total still quotes the number the page prints.",
         **document(holdout, exc["file"], [3, 4, 8, 11, 13, 14, 15, 16, 17],
                    [29, 30, 31, 32, 33, 34, 35, 36, 37, 65, 69]),
         "states": [
             state("first", "First answer", exc["output"], exc["outcome"], exc["violations"],
                   "Rejected by arithmetic and grounding. The retry gets every violation word for word."),
             state("retry", "After one retry", exc_retry["output"], "REJECTED", exc_retry["violations_after"],
                   "Totals now equal the sums (9,797 to 8,507 and 9,008 to 5,524), and both are wrong against "
                   "the filing. The same lines are still missing. Rejected by the quote check.", recorded=False),
         ]},
        {"id": "ctva", "ticker": "CTVA", "company": "Corteva", "dataset": holdout, "datasetLabel": "Held-out set",
         "model": qwen, "file": ctva["file"], "kind": "Quoted from the wrong statement",
         "summary": "The page prints two near-identical statements: Corteva's, then its subsidiary EIDP's. The "
                    "answer's current assets match Corteva's, but its accrued liabilities (2,958) and total current "
                    "liabilities (10,795) are EIDP's; Corteva prints 2,952 and 10,789. Every quote is on the page "
                    "and the lines add up to the total, so the frozen contract accepted it.",
         **document(holdout, ctva["file"], [2, 3, 4, 5, 6], [17, 18, 19, 20, 21]),
         "states": [
             state("frozen", "Frozen contract", ctva["output"], ctva["outcome"], ctva["violations"],
                   "Accepted, and wrong against ground truth. This is the held-out result."),
             state("region", "With the region rule", ctva["output"], "REJECTED",
                   [{"gate": "region", "path": "total_current_liabilities", "message": CTVA_REGION}],
                   "Added on 2026-10-28, after seeing this case: the date and the four totals must be quoted "
                   "from the first statement. Re-scoring every recorded answer changed only this verdict.",
                   post_hoc=True),
         ]},
        {"id": "dri", "ticker": "DRI", "company": "Darden Restaurants", "dataset": first, "datasetLabel": "Tuning set",
         "model": qwen, "file": dri["file"], "kind": "Scale multiplied by 1,000",
         "summary": "The statement is in millions with one decimal. The answer multiplied all 11 lines and 4 "
                    "totals by 1,000 (13,226,600 for \"13,226.6\") but kept the printed text as its quotes. Every quote "
                    "is on the page and the sums still hold, because every figure was scaled the same way.",
         **document(first, dri["file"], [3, 4, 5, 6, 7], [16, 17, 18, 19, 20, 21]),
         "states": [
             state("first", "First contract", dri["output"], dri["outcome"], dri["violations"],
                   "Accepted, and wrong by a factor of 1,000."),
             state("current", "Current contract", dri["output"], "REJECTED", dri_current,
                   "numbersMatchQuotes() checks that each value is a number its own quote prints. It was "
                   "added after this case, on the tuning set."),
             state("retry", "After one retry", dri_retry["output"], "REJECTED", dri_retry["violations_after"],
                   "The retry scaled again, with the same 15 violations.", recorded=False),
         ]},
    ]


def main():
    data = json.dumps(cases(), ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    OUT.write_text(TEMPLATE.read_text().replace("/*DATA*/[]", data))
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
