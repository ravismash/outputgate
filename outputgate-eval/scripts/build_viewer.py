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


def document(dataset, file):
    lines = (DATASETS / dataset / "documents" / file).read_text().splitlines()
    truth = json.loads((DATASETS / dataset / "documents" / "ground-truth.json").read_text())[file]
    end = next(i for i, line in enumerate(lines) if STATEMENT_END.search(line)) + 1
    return {"lines": lines, "truth": truth, "statement": [1, end]}


def state(key, label, output, outcome, violations, note, post_hoc=False):
    return {"key": key, "label": label, "output": json.loads(output), "outcome": outcome,
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
         "summary": "The answer left out lines, so they summed to 8,507 against a printed 9,797. Given the "
                    "violations, the retry kept its lines and changed both totals to match its own sums. "
                    "Arithmetic passes after that edit. The quote check does not, because each total still "
                    "quotes the number the page prints.",
         **document(holdout, exc["file"]),
         "states": [
             state("first", "First answer", exc["output"], exc["outcome"], exc["violations"],
                   "Rejected by arithmetic and grounding. The retry gets every violation word for word."),
             state("retry", "After one retry", exc_retry["output"], "REJECTED", exc_retry["violations_after"],
                   "Totals now equal the sums (9,797 to 8,507 and 9,008 to 5,524), and both are wrong against "
                   "the filing. Rejected by the quote check."),
         ]},
        {"id": "ctva", "ticker": "CTVA", "company": "Corteva", "dataset": holdout, "datasetLabel": "Held-out set",
         "model": qwen, "file": ctva["file"], "kind": "Quoted from the wrong statement",
         "summary": "The page prints two near-identical statements: Corteva's, then its subsidiary EIDP's. "
                    "The answer took two current liabilities from EIDP's (2,958 and the total 10,795; Corteva's "
                    "total is 10,789). Every quote is on the page and every sum holds, so the frozen contract "
                    "accepted it.",
         **document(holdout, ctva["file"]),
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
         "summary": "The statement is in millions with one decimal. The answer multiplied every amount by "
                    "1,000 (13,226,600 for \"13,226.6\") but kept the printed text as its quotes. Every quote "
                    "is on the page and the sums still hold, because every figure was scaled the same way.",
         **document(first, dri["file"]),
         "states": [
             state("first", "First contract", dri["output"], dri["outcome"], dri["violations"],
                   "Accepted, and wrong by a factor of 1,000."),
             state("current", "Current contract", dri["output"], "REJECTED", dri_current,
                   "numbersMatchQuotes() checks that each value is a number its own quote prints. It was "
                   "added after this case, on the tuning set."),
             state("retry", "After one retry", dri_retry["output"], "REJECTED", dri_retry["violations_after"],
                   "The retry scaled again, with the same 15 violations."),
         ]},
    ]


def main():
    data = json.dumps(cases(), ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    OUT.write_text(TEMPLATE.read_text().replace("/*DATA*/[]", data))
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
