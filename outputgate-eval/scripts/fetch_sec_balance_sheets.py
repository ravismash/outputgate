#!/usr/bin/env python3
"""Downloads real balance sheets from SEC EDGAR as plain-text documents for the eval harness.

For each company it takes the latest 10-Q, finds the balance-sheet statement in the filing's
FilingSummary.xml (the page EDGAR's XBRL viewer shows, e.g. R4.htm), and writes it as text:
one line per table row, cells separated by three spaces, exactly as the viewer prints them.
Only classified balance sheets (with "Total current assets" and "Total current liabilities")
are kept.

Writes to OUT:
  TICKER-YYYY-MM-DD.txt   one document per filing (BenchmarkFolder reads .txt documents)
  sources.tsv             ticker, CIK, accession, statement URL: what was downloaded
  ground-truth.json       the filing's own XBRL facts for the first (latest) column, by file

Usage: python3 -I fetch_sec_balance_sheets.py OUT [--limit 60] [--user-agent "name email"]
SEC asks for a User-Agent naming who is calling and at most 10 requests a second.
Standard library only.
"""
import argparse
import html
import json
import re
import sys
import time
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

# Large US companies across sectors, in a fixed order so a run is repeatable. Banks and insurers
# are left out: their balance sheets have no current/non-current split.
TICKERS = """
AAPL MSFT NVDA AMZN GOOGL META TSLA AVGO ORCL CSCO ADBE CRM INTC AMD QCOM TXN IBM HPQ DELL NKE
KO PEP PG CL KMB MDLZ GIS HSY MO PM WMT TGT COST HD LOW TJX ROST DG DLTR BBY MCD SBUX CMG YUM
DRI JNJ PFE MRK ABBV LLY BMY AMGN GILD TMO DHR ABT MDT SYK BDX ZTS CAT DE HON MMM GE LMT RTX
NOC GD BA UPS FDX UNP CSX NSC XOM CVX COP OXY SLB HAL F GM DIS CMCSA NFLX T VZ TMUS
""".split()

# XBRL elements recorded as ground truth, by the name the eval uses.
FACTS = {
    "total_current_assets": "us-gaap_AssetsCurrent",
    "total_assets": "us-gaap_Assets",
    "total_current_liabilities": "us-gaap_LiabilitiesCurrent",
    "total_liabilities_and_equity": "us-gaap_LiabilitiesAndStockholdersEquity",
}

BALANCE_SHEET = re.compile(r"balance sheet|financial position|financial condition", re.I)


class Fetcher:
    def __init__(self, user_agent):
        self.user_agent = user_agent
        self.last = 0.0

    def get(self, url):
        wait = 0.15 - (time.monotonic() - self.last)
        if wait > 0:
            time.sleep(wait)
        self.last = time.monotonic()
        req = urllib.request.Request(url, headers={"User-Agent": self.user_agent})
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.read().decode("utf-8", errors="replace")


class Statement(HTMLParser):
    """Rows of the first <table class="report">: each row's cells as text, and the XBRL element of its label."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.rows = []
        self.depth = 0
        self.done = False
        self.row = None
        self.cell = None
        self.element = None

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "table":
            if self.depth or ("report" in (a.get("class") or "") and not self.done):
                self.depth += 1
        if not self.depth:
            return
        if tag == "tr" and self.depth == 1:
            self.row, self.element = [], None
        elif tag in ("td", "th") and self.row is not None and self.depth == 1:
            self.cell = []
        elif tag == "br" and self.cell is not None:
            self.cell.append(" ")
        elif tag == "a" and self.cell is not None:
            m = re.search(r"defref_([\w-]+)", a.get("onclick") or "")
            if m and not self.row:
                self.element = m.group(1)
        elif tag == "sup":
            # Footnote markers ([1]) are noise next to an amount.
            self.cell = None if self.cell is None else self.cell + ["\0"]

    def handle_endtag(self, tag):
        if not self.depth:
            return
        if tag in ("td", "th") and self.cell is not None and self.depth == 1:
            text = "".join(self.cell).split("\0")[0]
            self.row.append(re.sub(r"\s+", " ", text).strip())
            self.cell = None
        elif tag == "tr" and self.row is not None and self.depth == 1:
            if any(self.row):
                self.rows.append((self.row, self.element))
            self.row = None
        elif tag == "table":
            self.depth -= 1
            if not self.depth:
                self.done = True

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)


def amount(cell):
    """'$ (1,234)' -> -1234.0; None when the cell is not a number."""
    m = re.fullmatch(r"\$?\s*(\()?\$?\s*([\d,]+(?:\.\d+)?)\)?", cell)
    if not m:
        return None
    v = float(m.group(2).replace(",", ""))
    return -v if m.group(1) else v


def statement_url(fetch, cik, accession):
    base = f"https://www.sec.gov/Archives/edgar/data/{int(cik)}/{accession.replace('-', '')}/"
    summary = fetch.get(base + "FilingSummary.xml")
    for report in re.findall(r"<Report\b.*?</Report>", summary, re.S):
        name = html.unescape(re.search(r"<ShortName>(.*?)</ShortName>", report, re.S).group(1))
        category = re.search(r"<MenuCategory>(.*?)</MenuCategory>", report, re.S)
        file = re.search(r"<HtmlFileName>(.*?)</HtmlFileName>", report, re.S)
        if (file and category and category.group(1) == "Statements" and BALANCE_SHEET.search(name)
                and "parenthetical" not in name.lower()):
            return base + file.group(1)
    return None


def render(rows):
    """The statement as text: a line per row, cells separated by three spaces."""
    return "\n".join("   ".join(c for c in cells if c) for cells, _ in rows) + "\n"


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("out")
    p.add_argument("--limit", type=int, default=60)
    p.add_argument("--user-agent", default="outputgate-eval (https://github.com/ravismash/outputgate)")
    args = p.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    fetch = Fetcher(args.user_agent)

    ciks = {v["ticker"]: v["cik_str"] for v in json.loads(fetch.get("https://www.sec.gov/files/company_tickers.json")).values()}
    sources = ["ticker\tcik\taccession\tform\tstatement_url"]
    truth = {}
    for ticker in TICKERS:
        if len(truth) >= args.limit:
            break
        try:
            cik = ciks[ticker]
            recent = json.loads(fetch.get(f"https://data.sec.gov/submissions/CIK{int(cik):010d}.json"))["filings"]["recent"]
            i = recent["form"].index("10-Q")
            accession, period = recent["accessionNumber"][i], recent["reportDate"][i]
            url = statement_url(fetch, cik, accession)
            if url is None:
                print(f"{ticker}: no balance sheet in {accession}", file=sys.stderr)
                continue
            parser = Statement()
            parser.feed(fetch.get(url))
            text = render(parser.rows)
            if "total current assets" not in text.lower() or "total current liabilities" not in text.lower():
                print(f"{ticker}: unclassified balance sheet, skipped", file=sys.stderr)
                continue
            facts = {}
            for cells, element in parser.rows:
                for name, wanted in FACTS.items():
                    if element == wanted and name not in facts and len(cells) > 1:
                        facts[name] = amount(cells[1])
            file = f"{ticker}-{period}.txt"
            (out / file).write_text(text)
            truth[file] = facts
            sources.append(f"{ticker}\t{cik}\t{accession}\t10-Q\t{url}")
            print(f"{file}: {len(parser.rows)} rows")
        except (KeyError, ValueError, OSError) as e:
            print(f"{ticker}: {type(e).__name__}: {e}", file=sys.stderr)

    (out / "sources.tsv").write_text("\n".join(sources) + "\n")
    (out / "ground-truth.json").write_text(json.dumps(truth, indent=1, sort_keys=True) + "\n")
    print(f"wrote {len(truth)} balance sheets to {out}")


if __name__ == "__main__":
    main()
