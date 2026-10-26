# Eval run: gemini-3.1-pro-preview (Gemini API, temperature 0, response_format: balance-sheet output schema)

- When: 2026-10-26T11:20:42.916022+05:30
- Documents: 60 from `/Users/raviprakameena/Desktop/outputgate/outputgate-eval/datasets/sec-balance-sheets/documents`
- Outcomes: PASSED: 58 · REJECTED: 2 · NO_TEXT: 0 · ERROR: 0
- Output tokens: 60258 · model time: 796 s

## Rejections by gate (documents where the gate failed)

| gate | documents |
|---|---|
| math | 2 |

## Every document

| file | pages | outcome | output tokens | seconds | failing gates |
|---|---|---|---|---|---|
| AAPL-2026-06-27.txt | 1 | PASSED | 1085 | 10 |  |
| ABBV-2026-06-30.txt | 1 | PASSED | 802 | 8 |  |
| ABT-2026-06-30.txt | 1 | PASSED | 1094 | 15 |  |
| ADBE-2026-08-28.txt | 1 | PASSED | 1002 | 10 |  |
| AMD-2026-06-27.txt | 1 | PASSED | 932 | 13 |  |
| AMGN-2026-06-30.txt | 1 | PASSED | 803 | 12 |  |
| AMZN-2026-06-30.txt | 1 | PASSED | 828 | 12 |  |
| AVGO-2026-08-02.txt | 1 | PASSED | 871 | 8 |  |
| BBY-2026-08-01.txt | 1 | PASSED | 1047 | 29 |  |
| BDX-2026-06-30.txt | 1 | PASSED | 975 | 14 |  |
| BMY-2026-06-30.txt | 1 | PASSED | 864 | 12 |  |
| CL-2026-06-30.txt | 1 | PASSED | 861 | 8 |  |
| CMG-2026-06-30.txt | 1 | PASSED | 1184 | 11 |  |
| COST-2026-05-10.txt | 1 | PASSED | 1005 | 14 |  |
| CRM-2026-07-31.txt | 1 | PASSED | 940 | 13 |  |
| CSCO-2026-04-25.txt | 1 | PASSED | 1166 | 16 |  |
| DELL-2026-07-31.txt | 1 | PASSED | 977 | 13 |  |
| DG-2026-07-31.txt | 1 | PASSED | 1009 | 9 |  |
| DHR-2026-09-25.txt | 1 | PASSED | 823 | 12 |  |
| DLTR-2026-08-01.txt | 1 | PASSED | 823 | 16 |  |
| DRI-2026-08-30.txt | 1 | PASSED | 1097 | 10 |  |
| GILD-2026-06-30.txt | 1 | PASSED | 929 | 13 |  |
| GIS-2026-08-30.txt | 1 | PASSED | 969 | 15 |  |
| GOOGL-2026-06-30.txt | 1 | PASSED | 962 | 13 |  |
| HD-2026-08-02.txt | 1 | PASSED | 1206 | 10 |  |
| HPQ-2026-07-31.txt | 1 | PASSED | 840 | 12 |  |
| HSY-2026-06-28.txt | 1 | PASSED | 1002 | 14 |  |
| IBM-2026-09-30.txt | 1 | REJECTED | 1537 | 25 | math |
| INTC-2026-06-27.txt | 1 | PASSED | 946 | 14 |  |
| JNJ-2026-09-27.txt | 1 | PASSED | 1117 | 16 |  |
| KMB-2026-06-30.txt | 1 | PASSED | 984 | 9 |  |
| KO-2026-07-03.txt | 1 | PASSED | 1097 | 11 |  |
| LLY-2026-06-30.txt | 1 | PASSED | 1075 | 14 |  |
| LOW-2026-07-31.txt | 1 | PASSED | 1067 | 9 |  |
| MCD-2026-06-30.txt | 1 | PASSED | 1037 | 12 |  |
| MDLZ-2026-06-30.txt | 1 | PASSED | 1083 | 15 |  |
| MDT-2026-07-31.txt | 1 | PASSED | 1012 | 14 |  |
| META-2026-09-30.txt | 1 | PASSED | 823 | 12 |  |
| MO-2026-06-30.txt | 1 | PASSED | 979 | 15 |  |
| MRK-2026-06-30.txt | 1 | PASSED | 1077 | 14 |  |
| MSFT-2026-03-31.txt | 1 | PASSED | 1046 | 12 |  |
| NKE-2026-08-31.txt | 1 | PASSED | 998 | 14 |  |
| NVDA-2026-07-26.txt | 1 | PASSED | 957 | 13 |  |
| ORCL-2026-08-31.txt | 1 | PASSED | 989 | 9 |  |
| PEP-2026-09-05.txt | 1 | PASSED | 822 | 10 |  |
| PFE-2026-06-28.txt | 1 | PASSED | 1201 | 12 |  |
| PG-2026-09-30.txt | 1 | PASSED | 811 | 13 |  |
| PM-2026-09-30.txt | 1 | PASSED | 1322 | 17 |  |
| QCOM-2026-06-28.txt | 1 | PASSED | 1055 | 14 |  |
| ROST-2026-08-01.txt | 1 | PASSED | 1076 | 9 |  |
| SBUX-2026-06-28.txt | 1 | PASSED | 1130 | 10 |  |
| SYK-2026-06-30.txt | 1 | PASSED | 1069 | 10 |  |
| TGT-2026-08-01.txt | 1 | PASSED | 738 | 11 |  |
| TJX-2026-08-01.txt | 1 | PASSED | 1006 | 14 |  |
| TMO-2026-06-27.txt | 1 | REJECTED | 1082 | 11 | math |
| TSLA-2026-09-30.txt | 1 | PASSED | 949 | 12 |  |
| TXN-2026-09-30.txt | 1 | PASSED | 999 | 12 |  |
| WMT-2026-07-31.txt | 1 | PASSED | 1158 | 16 |  |
| YUM-2026-06-30.txt | 1 | PASSED | 858 | 12 |  |
| ZTS-2026-06-30.txt | 1 | PASSED | 1062 | 10 |  |

## Every violation and error

- `IBM-2026-09-30.txt` [math] total_current_assets: sum of current_assets[*].amount is 31190 but total_current_assets is 31192 (tolerance 1)
- `TMO-2026-06-27.txt` [math] total_current_liabilities: sum of current_liabilities[*].amount is 15071 but total_current_liabilities is 15069 (tolerance 1)
