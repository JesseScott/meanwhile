#!/usr/bin/env python3
"""
A slow, polite GDELT sample for the shortlist of landing countries.

GDELT's DOC API throttles hard (an earlier overnight survey got 12 successes against 33 rate-limited answers), so
this asks for one country at a time, waits a long gap between requests, and backs off on HTTP 429. It saves each raw
answer under out/gdelt/ so the app's own cleaning rules can be run over them offline later, and keeps a one-line
summary per country in out/gdelt_summary.jsonl. It can be stopped and started again: countries already answered
are skipped.

    python gdelt_sample.py [gap_seconds]     # default gap is 150 seconds
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
RAW = os.path.join(OUT, "gdelt")
os.makedirs(RAW, exist_ok=True)

UA = "Meanwhile/0.1 (hobby app; +https://jesses.co.tt)"
GAP = float(sys.argv[1]) if len(sys.argv) > 1 else 150.0

# fips code GDELT's sourcecountry: filter takes, then a name for the report. ISO to FIPS differences are in
# core/.../Countries.kt; these are the shortlist from landing.py.
SHORTLIST = [
    ("NZ", "New Zealand"), ("AS", "Australia"), ("PE", "Peru"), ("AR", "Argentina"), ("BR", "Brazil"),
    ("CI", "Chile"), ("CO", "Colombia"), ("UY", "Uruguay"), ("CH", "China"), ("JA", "Japan"), ("ID", "Indonesia"),
    ("MA", "Madagascar"), ("US", "United States"), ("EC", "Ecuador"), ("CB", "Cambodia"), ("MO", "Morocco"),
    ("CA", "Canada"), ("SP", "Spain"), ("RP", "Philippines"), ("SF", "South Africa"), ("MP", "Mauritius"),
    ("RE", "Reunion"), ("FJ", "Fiji"), ("FP", "French Polynesia"), ("BC", "Botswana"),
]


def fetch(fips):
    url = ("https://api.gdeltproject.org/api/v2/doc/doc?query=sourcecountry:%s&mode=artlist&format=json"
           "&maxrecords=250&timespan=7d" % fips)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # timeouts and resets are just another way of being told no
        return 0, str(e)


def done():
    path = os.path.join(OUT, "gdelt_summary.jsonl")
    if not os.path.exists(path):
        return set()
    return {json.loads(l)["fips"] for l in open(path, encoding="utf-8") if l.strip() and json.loads(l)["status"] == 200}


def main():
    finished = done()
    for fips, name in SHORTLIST:
        if fips in finished:
            continue
        for attempt in range(4):
            status, body = fetch(fips)
            if status == 200:
                break
            wait = 600 if status == 429 else 120
            print(f"{time.strftime('%H:%M:%S')} {fips} {name}: HTTP {status}, waiting {wait}s (attempt {attempt + 1}/4)", flush=True)
            time.sleep(wait)
        articles = 0
        domains = {}
        if status == 200:
            open(os.path.join(RAW, f"{fips}.json"), "w", encoding="utf-8").write(body)
            try:
                arts = json.loads(body).get("articles", []) if body.strip().startswith("{") else []
            except ValueError:
                arts = []
            articles = len(arts)
            for a in arts:
                domains[a.get("domain", "")] = domains.get(a.get("domain", ""), 0) + 1
        top = sorted(domains.items(), key=lambda kv: -kv[1])[:5]
        row = {"fips": fips, "name": name, "status": status, "bytes": len(body), "articles": articles,
               "distinct_domains": len(domains), "top_domains": top, "at": time.strftime("%Y-%m-%d %H:%M:%S")}
        with open(os.path.join(OUT, "gdelt_summary.jsonl"), "a", encoding="utf-8") as f:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
        print(f"{time.strftime('%H:%M:%S')} {fips} {name}: HTTP {status}, {articles} articles, {len(domains)} domains, top {top[:2]}", flush=True)
        time.sleep(GAP)
    print("sample complete", flush=True)


if __name__ == "__main__":
    main()
