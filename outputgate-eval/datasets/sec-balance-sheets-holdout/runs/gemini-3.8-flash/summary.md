# Eval run: gemini-3.8-flash (Gemini API, temperature 0, response_format: balance-sheet output schema)

- When: 2026-10-26T14:08:38.220975+05:30
- Documents: 60 from `/Users/raviprakameena/Desktop/outputgate/outputgate-eval/datasets/sec-balance-sheets-holdout/documents`
- Outcomes: PASSED: 59 · REJECTED: 1 · NO_TEXT: 0 · ERROR: 0
- Output tokens: 40187 · model time: 676 s

## Rejections by gate (documents where the gate failed)

| gate | documents |
|---|---|
| math | 1 |
| provenance | 1 |

## Every document

| file | pages | outcome | output tokens | seconds | failing gates |
|---|---|---|---|---|---|
| ACN-2026-05-31.txt | 1 | PASSED | 791 | 11 |  |
| ADP-2026-03-31.txt | 1 | PASSED | 438 | 15 |  |
| ADSK-2026-07-31.txt | 1 | PASSED | 706 | 10 |  |
| AMAT-2026-07-26.txt | 1 | PASSED | 565 | 10 |  |
| ANET-2026-06-30.txt | 1 | PASSED | 650 | 10 |  |
| APD-2026-06-30.txt | 1 | PASSED | 726 | 11 |  |
| AZO-2026-05-09.txt | 1 | PASSED | 630 | 11 |  |
| BKNG-2026-06-30.txt | 1 | PASSED | 586 | 5 |  |
| BSX-2026-06-30.txt | 1 | PASSED | 590 | 10 |  |
| CARR-2026-06-30.txt | 1 | PASSED | 608 | 10 |  |
| CDNS-2026-06-30.txt | 1 | PASSED | 541 | 9 |  |
| CI-2026-06-30.txt | 1 | PASSED | 662 | 10 |  |
| CMI-2026-06-30.txt | 1 | PASSED | 769 | 14 |  |
| CPRT-2026-04-30.txt | 1 | PASSED | 1147 | 14 |  |
| CTAS-2026-08-31.txt | 1 | PASSED | 781 | 11 |  |
| CTVA-2026-06-30.txt | 1 | PASSED | 634 | 11 |  |
| CVS-2026-06-30.txt | 1 | PASSED | 606 | 11 |  |
| DOW-2026-09-30.txt | 1 | PASSED | 775 | 12 |  |
| DUK-2026-06-30.txt | 1 | PASSED | 849 | 10 |  |
| ECL-2026-06-30.txt | 1 | PASSED | 644 | 8 |  |
| EL-2026-03-31.txt | 1 | PASSED | 852 | 11 |  |
| EMR-2026-06-30.txt | 1 | PASSED | 539 | 7 |  |
| ETN-2026-06-30.txt | 1 | PASSED | 638 | 9 |  |
| EW-2026-06-30.txt | 1 | PASSED | 708 | 9 |  |
| EXC-2026-06-30.txt | 1 | PASSED | 1033 | 41 |  |
| FAST-2026-09-30.txt | 1 | PASSED | 682 | 9 |  |
| FTNT-2026-06-30.txt | 1 | PASSED | 686 | 10 |  |
| GLW-2026-06-30.txt | 1 | PASSED | 554 | 10 |  |
| GWW-2026-06-30.txt | 1 | PASSED | 632 | 10 |  |
| HCA-2026-06-30.txt | 1 | PASSED | 570 | 10 |  |
| HUM-2026-06-30.txt | 1 | PASSED | 630 | 8 |  |
| IDXX-2026-06-30.txt | 1 | PASSED | 663 | 10 |  |
| INTU-2026-04-30.txt | 1 | PASSED | 416 | 16 |  |
| ISRG-2026-09-30.txt | 1 | PASSED | 521 | 9 |  |
| ITW-2026-06-30.txt | 1 | PASSED | 587 | 10 |  |
| KDP-2026-06-30.txt | 1 | REJECTED | 852 | 11 | math, provenance |
| KHC-2026-06-27.txt | 1 | PASSED | 779 | 10 |  |
| KLAC-2026-03-31.txt | 1 | PASSED | 680 | 11 |  |
| LIN-2026-06-30.txt | 1 | PASSED | 648 | 5 |  |
| LRCX-2026-09-27.txt | 1 | PASSED | 690 | 9 |  |
| LULU-2026-08-02.txt | 1 | PASSED | 817 | 12 |  |
| MCHP-2026-06-30.txt | 1 | PASSED | 502 | 8 |  |
| MCK-2026-06-30.txt | 1 | PASSED | 463 | 8 |  |
| MNST-2026-06-30.txt | 1 | PASSED | 811 | 12 |  |
| MPC-2026-06-30.txt | 1 | PASSED | 659 | 8 |  |
| MSI-2026-07-04.txt | 1 | PASSED | 464 | 9 |  |
| MU-2026-05-28.txt | 1 | PASSED | 568 | 8 |  |
| NOW-2026-06-30.txt | 1 | PASSED | 654 | 12 |  |
| NUE-2026-07-04.txt | 1 | PASSED | 653 | 9 |  |
| ODFL-2026-06-30.txt | 1 | PASSED | 748 | 11 |  |
| ORLY-2026-06-30.txt | 1 | PASSED | 808 | 11 |  |
| PANW-2026-04-30.txt | 1 | PASSED | 570 | 9 |  |
| PAYX-2026-08-31.txt | 1 | PASSED | 428 | 12 |  |
| PH-2026-03-31.txt | 1 | PASSED | 694 | 10 |  |
| PSX-2026-06-30.txt | 1 | PASSED | 711 | 24 |  |
| REGN-2026-06-30.txt | 1 | PASSED | 651 | 9 |  |
| RMD-2026-03-31.txt | 1 | PASSED | 769 | 10 |  |
| ROK-2026-06-30.txt | 1 | PASSED | 657 | 8 |  |
| ROP-2026-06-30.txt | 1 | PASSED | 764 | 11 |  |
| SHW-2026-06-30.txt | 1 | PASSED | 738 | 9 |  |

## Every violation and error

- `KDP-2026-06-30.txt` [math] (whole output): custom rule 'total_current_assets <= total_assets' failed
- `KDP-2026-06-30.txt` [math] (whole output): custom rule 'total_current_liabilities <= total_liabilities_and_equity' failed
- `KDP-2026-06-30.txt` [provenance] total_assets: value 8761900000 is not a number its quote prints (it prints 87,619,000,000)
- `KDP-2026-06-30.txt` [provenance] total_liabilities_and_equity: value 8761900000 is not a number its quote prints (it prints 87,619,000,000)
