"use client";

import { useEffect, useId, useRef, useState } from "react";
import type { DeckRow } from "@/lib/deckshared";

const FINISH_NAMES = { NONFOIL: "nonfoil", FOIL: "foil", ETCHED: "etched" } as const;

/** A printing in words: "Set name (CODE) #123". */
function printing(c: { setCode: string; setName: string; collectorNumber: string }): string {
  const code = c.setCode.toUpperCase();
  return `${c.setName && c.setName !== code ? `${c.setName} (${code})` : code} #${c.collectorNumber}`;
}

/**
 * A deck line's "Collection tracker" (like Moxfield's): a green tick when the collection holds enough copies of the
 * printing and finish shown, an amber ring when it holds the card in another printing or finish (or too few copies),
 * an empty grey ring when it holds none. Pointing at it (or focusing it) shows what the deck needs and what the
 * collection holds; clicking opens the collection on that card. On a touch screen the first tap shows the box.
 */
export function OwnedIcon({ row }: { row: DeckRow }) {
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [tapped, setTapped] = useState(false);
  const wrap = useRef<HTMLSpanElement>(null);
  const tipId = useId();
  const shown = hovered || focused || tapped;
  const kind = row.exact >= row.quantity ? "ok" : row.held.length > 0 ? "some" : "none";
  const label = kind === "ok" ? "You own this printing" : kind === "some" ? "You own the card, not this printing" : "Not in your collection";

  // A tap elsewhere closes the box that a first tap opened.
  useEffect(() => {
    if (!tapped) return;
    const away = (e: PointerEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setTapped(false);
    };
    document.addEventListener("pointerdown", away);
    return () => document.removeEventListener("pointerdown", away);
  }, [tapped]);

  return (
    <span
      ref={wrap}
      className="own-wrap"
      // The line itself opens its dialog on a click or Enter; the icon and its box don't.
      onClick={(e) => e.stopPropagation()}
      onKeyDown={(e) => e.stopPropagation()}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <a
        className={`own-icon own-${kind}`}
        href={`/collection?search=${encodeURIComponent(row.name)}&binder=`}
        aria-label={label}
        aria-describedby={shown ? tipId : undefined}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onClick={(e) => {
          if (!tapped && window.matchMedia("(hover: none)").matches) {
            e.preventDefault();
            setTapped(true);
          }
        }}
      >
        {kind === "ok" ? "✓" : ""}
      </a>
      {shown && (
        <span id={tipId} role="tooltip" className="own-tip small">
          <strong className="own-title">Collection tracker</strong>
          <span className="own-part">Needed for the deck</span>
          <span>
            {row.quantity}× {printing(row.card)}, {FINISH_NAMES[row.finish]}
          </span>
          <span className="own-part">Found in your collection</span>
          {row.held.length === 0 ? (
            <span className="muted">None</span>
          ) : (
            row.held.map((h, i) => {
              const exact = h.scryfallId === row.card.scryfallId && h.finish === row.finish;
              return (
                <span key={i} className={exact ? "own-exact" : undefined}>
                  {exact ? "✓ " : ""}
                  {h.quantity}× {printing(h)}, {FINISH_NAMES[h.finish]}, {h.condition}, {h.binder}
                </span>
              );
            })
          )}
          {row.exact < row.quantity && (
            <span className="own-note">
              You own {row.exact} of {row.quantity} in this printing{row.owned > 0 ? ` (${row.owned} in any printing)` : ""}.
            </span>
          )}
          {tapped && (
            <a className="own-link" href={`/collection?search=${encodeURIComponent(row.name)}&binder=`}>
              Show in collection
            </a>
          )}
        </span>
      )}
    </span>
  );
}
