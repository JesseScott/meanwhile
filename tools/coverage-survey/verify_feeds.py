#!/usr/bin/env python3
"""
Re-fetch every feed the research agents recommended and check it again from scratch, the way the app would: its own
User-Agent, plain GET, no cookies. Records HTTP status, bytes, format, item count, newest date and a few titles.

    python verify_feeds.py       # reads out/feeds/*.json, writes out/feeds_verified.csv and prints a summary

Polite: one request per feed, and at most one per second to any one host.
"""
import csv
import glob
import json
import os
import re
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from urllib.parse import urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
UA = "Meanwhile/0.1 (hobby app; +https://jesses.co.tt)"
NOW = datetime.now(timezone.utc)


def parse_date(text):
    text = (text or "").strip()
    if not text:
        return None
    try:
        d = parsedate_to_datetime(text)
    except (TypeError, ValueError):
        try:
            d = datetime.fromisoformat(text.replace("Z", "+00:00"))
        except ValueError:
            return None
    return d if d.tzinfo else d.replace(tzinfo=timezone.utc)


def strip_ns(tag):
    return tag.rsplit("}", 1)[-1]


def inspect(body):
    try:
        root = ET.fromstring(body)
    except ET.ParseError as e:
        return {"format": "unparseable", "items": 0, "newest": None, "titles": [], "error": str(e)[:60]}
    kind = strip_ns(root.tag).lower()
    fmt = {"rss": "rss", "feed": "atom", "rdf": "rdf"}.get(kind, kind)
    items = [e for e in root.iter() if strip_ns(e.tag) in ("item", "entry")]
    newest, titles = None, []
    for it in items:
        d = None
        for child in it:
            if strip_ns(child.tag) in ("pubDate", "published", "updated", "date"):
                d = parse_date(child.text)
                break
        if d and (newest is None or d > newest):
            newest = d
        if len(titles) < 3:
            for child in it:
                if strip_ns(child.tag) == "title":
                    titles.append((child.text or "").strip()[:80])
                    break
    return {"format": fmt, "items": len(items), "newest": newest, "titles": titles, "error": ""}


def main():
    rows, last_hit = [], {}
    for path in sorted(glob.glob(os.path.join(OUT, "feeds", "*.json"))):
        data = json.load(open(path, encoding="utf-8"))
        for c in data["countries"]:
            for f in c.get("feeds", []):
                if not f.get("recommend"):
                    continue
                url = f["url"]
                host = urlparse(url).netloc
                wait = last_hit.get(host, 0) + 1.0 - time.time()
                if wait > 0:
                    time.sleep(wait)
                last_hit[host] = time.time()
                status, ctype, size, body, err = 0, "", 0, b"", ""
                try:
                    req = urllib.request.Request(url, headers={"User-Agent": UA})
                    with urllib.request.urlopen(req, timeout=25) as r:
                        status, ctype, body = r.status, r.headers.get("Content-Type", ""), r.read()
                        size = len(body)
                except urllib.error.HTTPError as e:
                    status = e.code
                except Exception as e:
                    err = str(e)[:60]
                info = inspect(body) if status == 200 and body else {"format": "-", "items": 0, "newest": None, "titles": [], "error": err}
                age_h = round((NOW - info["newest"]).total_seconds() / 3600) if info["newest"] else ""
                rows.append({
                    "country": c["country"], "fips": c["fips"], "outlet": f.get("outlet", ""), "url": url,
                    "language": f.get("language", ""), "http": status, "https": url.startswith("https://"),
                    "format": info["format"], "items": info["items"], "age_hours": age_h, "kb": round(size / 1024),
                    "claimed_items": f.get("items", ""), "title": (info["titles"] or [""])[0], "problem": info["error"],
                })
                print(f"{c['country'][:14]:14} {status:3} {info['format']:6} {info['items']:4} items {str(age_h):>5}h {round(size/1024):5} KB  {url}", flush=True)
    with open(os.path.join(OUT, "feeds_verified.csv"), "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    ok = [r for r in rows if r["http"] == 200 and r["items"] > 0 and r["age_hours"] != "" and r["age_hours"] < 24 * 7]
    print(f"\n{len(ok)} of {len(rows)} recommended feeds verified (200, parsed, newest item under 7 days)")


if __name__ == "__main__":
    main()
