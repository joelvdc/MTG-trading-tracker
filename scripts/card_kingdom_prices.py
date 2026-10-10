#!/usr/bin/env python3
"""Slims Card Kingdom's public price list down to what MTG Trader reads (since 1.30).

Card Kingdom refuses some visitors (HTTP 403, e.g. phones in Denmark), so a daily GitHub job
(.github/workflows/card-kingdom.yml) downloads the list from GitHub's servers, runs this script and
keeps the result on the "card-kingdom-prices" branch, where the app can always reach it.

Usage: card_kingdom_prices.py <pricelist.json or .json.gz> <out.tsv.gz>

The output is gzipped text: a first line "#card-kingdom<TAB><created_at>", then one line per
printing and finish:
  scryfall_id, F (foil) or N, retail, buy, nm, ex, vg, g, url path (after https://www.cardkingdom.com/)
Prices are US dollars as Card Kingdom writes them; empty when missing or zero. When Card Kingdom
lists a printing twice (a The List copy carries the original's Scryfall id), the entry without a
variation wins (the last such one, e.g. for double-sided tokens), as in the app's own reader (data/PriceSources.kt, readCardKingdom).
"""
import gzip
import json
import sys


def price(v):
    try:
        f = float(v)
    except (TypeError, ValueError):
        return ""
    return v.strip() if isinstance(v, str) and f > 0 else ("" if f <= 0 else repr(f))


def main(src, dst):
    opener = gzip.open if open(src, "rb").read(2) == b"\x1f\x8b" else open
    with opener(src, "rt", encoding="utf-8") as f:
        doc = json.load(f)
    created = (doc.get("meta") or {}).get("created_at", "")
    rows = {}
    plain = {}
    for e in doc.get("data") or []:
        sid = (e.get("scryfall_id") or "").strip()
        if not sid:
            continue
        foil = "F" if str(e.get("is_foil")).lower() == "true" else "N"
        key = (sid, foil)
        is_plain = not (e.get("variation") or "").strip()
        # Same rule as the app: an entry with a variation never replaces another; a plain one replaces any earlier.
        if key in plain and not is_plain:
            continue
        plain[key] = is_plain
        c = e.get("condition_values") or {}
        rows[key] = [
            sid, foil, price(e.get("price_retail")), price(e.get("price_buy")),
            price(c.get("nm_price")), price(c.get("ex_price")), price(c.get("vg_price")), price(c.get("g_price")),
            (e.get("url") or "").replace("\t", " "),
        ]
    if len(rows) < 1000:
        sys.exit(f"Only {len(rows)} entries: not replacing the saved list")
    with gzip.open(dst, "wt", encoding="utf-8", compresslevel=9) as out:
        out.write(f"#card-kingdom\t{created}\n")
        for r in rows.values():
            out.write("\t".join(r) + "\n")
    print(f"{len(rows)} entries, list of {created}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
