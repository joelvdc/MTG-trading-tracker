#!/usr/bin/env python3
"""Slims MTGJSON's AtomicCards down to the EDHREC salt score of each card, for MTG Trader (2026-11-23).

The website's deck rating wants to know how salty each card is (how much other players dislike playing
against it). MTGJSON's AtomicCards is 50+ MB, far too big for the website to download, so a weekly GitHub
job (.github/workflows/card-salt.yml) downloads it from GitHub's servers, runs this script and keeps the
result on the "card-salt" branch, where the website can always reach it.

Usage: card_salt.py <AtomicCards.json[.gz]> <out.json.gz>

The output is gzipped JSON by card name: {"<name>": <salt>, ...}, names sorted, salt to 2 decimals; a card
without a salt is left out. Quits with an error (and the job keeps the last copy) when fewer than 5000
names came out, which means MTGJSON's file changed shape.
"""
import gzip
import json
import sys

MIN_NAMES = 5000


def main(src, dst):
    opener = gzip.open if open(src, "rb").read(2) == b"\x1f\x8b" else open
    with opener(src, "rt", encoding="utf-8") as f:
        doc = json.load(f)
    out = {}
    for name, faces in (doc.get("data") or {}).items():
        if not name:
            continue
        for face in faces or []:
            salt = face.get("edhrecSaltiness") if isinstance(face, dict) else None
            # A card's faces carry the same salt; the first face that has a number is enough.
            if isinstance(salt, (int, float)) and not isinstance(salt, bool):
                out[name] = round(salt, 2)
                break
    if len(out) < MIN_NAMES:
        sys.exit(f"Only {len(out)} cards have a salt score: not replacing the saved file")
    with gzip.open(dst, "wt", encoding="utf-8", compresslevel=9) as f:
        json.dump(out, f, sort_keys=True, separators=(",", ":"))
    print(f"{len(out)} cards with a salt score")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
