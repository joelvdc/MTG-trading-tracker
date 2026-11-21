"use client";

import { useState } from "react";
import type { CSSProperties } from "react";
import { CONDITIONS, FINISH_LABELS, FINISHES, LANGUAGES } from "@/lib/cards";
import type { CollDeck } from "@/lib/collview";
import {
  COLOR_MATCH_LABELS,
  deckChoices,
  EMPTY_FILTER,
  FACE_KINDS,
  FILTER_RARITIES,
  FILTER_TYPES,
  type ColorMatch,
  type DeckFilter,
  type Filter,
  type MarkFilter,
} from "@/lib/colquery";
import type { Currency } from "@/lib/money";
import { COLOUR_FILL } from "./Mana";
import { Dialog } from "./Dialog";
import { MultiSelect } from "./MultiSelect";

const COLORS: [string, string][] = [
  ["W", "White"],
  ["U", "Blue"],
  ["B", "Black"],
  ["R", "Red"],
  ["G", "Green"],
  ["C", "Colorless"],
  ["M", "Multicolor"],
];

function toggle<T>(list: T[], v: T): T[] {
  return list.includes(v) ? list.filter((x) => x !== v) : [...list, v];
}

function Chips<T extends string>({ options, value, onChange }: { options: [T, string][]; value: T[]; onChange: (v: T[]) => void }) {
  return (
    <div className="chips">
      {options.map(([k, label]) => (
        <button key={k} className={value.includes(k) ? "chip on" : "chip"} aria-pressed={value.includes(k)} onClick={() => onChange(toggle(value, k))}>
          {label}
        </button>
      ))}
    </div>
  );
}

/** A colour's circle: its fill (the mana colours) with the letter on it; Multicolor is the five colours as a wheel. */
function colourDotStyle(code: string): CSSProperties {
  if (code !== "M") {
    return {
      borderColor: COLOUR_FILL[code],
      background: `color-mix(in srgb, ${COLOUR_FILL[code]} 30%, var(--surface))`,
    };
  }
  const tint = (c: string) => `color-mix(in srgb, ${COLOUR_FILL[c]} 60%, var(--surface))`;
  return {
    background: `conic-gradient(from 0deg, ${tint("W")} 0deg 72deg, ${tint("U")} 72deg 144deg, ${tint("B")} 144deg 216deg, ${tint("R")} 216deg 288deg, ${tint("G")} 288deg 360deg)`,
  };
}

/** The collection's filter (the app's filter sheet): everything that's awkward to type in the search field. */
export function FilterDialog({
  filter,
  sets,
  decks = [],
  currency,
  perEuro,
  onApply,
  onClose,
}: {
  filter: Filter;
  /** Sets in the collection: code, name, copies. */
  sets: { code: string; name: string; copies: number }[];
  /** The active decks (the "Used by deck" choice). */
  decks?: CollDeck[];
  currency: Currency;
  perEuro: number;
  onApply: (f: Filter) => void;
  onClose: () => void;
}) {
  const [f, setF] = useState<Filter>(filter);
  const deckNames = deckChoices(decks);
  const [setText, setSetText] = useState("");
  const [setsFocused, setSetsFocused] = useState(false);
  const [min, setMin] = useState(filter.minPrice != null ? (filter.minPrice * perEuro).toFixed(2) : "");
  const [max, setMax] = useState(filter.maxPrice != null ? (filter.maxPrice * perEuro).toFixed(2) : "");
  const num = (s: string) => {
    const t = s.trim().replace(",", ".");
    const n = Number(t);
    return t === "" || !Number.isFinite(n) ? null : n / perEuro;
  };
  const shownSets = sets.filter((s) => !setText.trim() || s.name.toLowerCase().includes(setText.toLowerCase()) || s.code === setText.trim().toLowerCase()).slice(0, 40);
  const setChoices = shownSets.filter((s) => !f.sets.includes(s.code));

  return (
    <Dialog title="Filter" onClose={onClose} wide>
      <div className="form-grid">
        <span className="form-label">Colors</span>
        <div className="form-control colours-line">
          <div className="colour-picks">
            {COLORS.map(([code, name]) => (
              <label key={code} className="colour-pick" title={name}>
                <input
                  type="checkbox"
                  aria-label={name}
                  checked={f.colors.includes(code)}
                  onChange={() => setF({ ...f, colors: toggle(f.colors, code) })}
                />
                <span className={code === "M" ? "colour-dot multi" : "colour-dot"} style={colourDotStyle(code)} aria-hidden="true">
                  {code}
                </span>
              </label>
            ))}
          </div>
          <select aria-label="How colors match" className="colour-match" value={f.colorMatch} onChange={(e) => setF({ ...f, colorMatch: e.target.value as ColorMatch })}>
            {(Object.keys(COLOR_MATCH_LABELS) as ColorMatch[]).map((k) => (
              <option key={k} value={k}>
                {COLOR_MATCH_LABELS[k]}
              </option>
            ))}
          </select>
        </div>

        <span className="form-label">Type</span>
        <div className="form-control">
          <MultiSelect
            label="Type"
            options={FILTER_TYPES.map((t) => [t, t] as [string, string])}
            value={f.types}
            onChange={(types) => setF({ ...f, types })}
          />
        </div>

        <span className="form-label">Faces</span>
        <div className="form-control">
          <MultiSelect label="Faces" options={FACE_KINDS} value={f.faces ?? []} onChange={(faces) => setF({ ...f, faces })} />
        </div>

        <span className="form-label">Rarity</span>
        <div className="form-control">
          <MultiSelect label="Rarity" options={FILTER_RARITIES} value={f.rarities} onChange={(rarities) => setF({ ...f, rarities })} />
        </div>

        <span className="form-label">Sets</span>
        <div className="form-control">
          <div className="set-search">
            <input
              type="search"
              aria-label="Find a set"
              placeholder="Find a set"
              value={setText}
              onChange={(e) => setSetText(e.target.value)}
              onFocus={() => setSetsFocused(true)}
              onKeyDown={(e) => {
                // Escape hides the set list and empties the box, leaving the window itself open.
                if (e.key === "Escape" && (setsFocused || setText.trim() !== "")) {
                  e.preventDefault();
                  setSetText("");
                  setSetsFocused(false);
                }
              }}
              onBlur={(e) => {
                if (!e.currentTarget.parentElement?.contains(e.relatedTarget as Node | null)) setSetsFocused(false);
              }}
            />
            {(setsFocused || setText.trim() !== "") && (
              <div className="set-list">
                {setChoices.map((s) => (
                  <button
                    key={s.code}
                    type="button"
                    onClick={() => {
                      setF({ ...f, sets: [...f.sets, s.code] });
                      setSetText("");
                    }}
                  >
                    {s.name} <span className="muted">({s.copies})</span>
                  </button>
                ))}
                {setChoices.length === 0 && <span className="set-none muted">No matching sets</span>}
              </div>
            )}
          </div>
          {f.sets.length > 0 && (
            <div className="set-chips">
              <Chips
                options={f.sets.map((c) => [c, sets.find((s) => s.code === c)?.name ?? c.toUpperCase()] as [string, string])}
                value={f.sets}
                onChange={(v) => setF({ ...f, sets: v })}
              />
            </div>
          )}
        </div>

        <span className="form-label">Finish</span>
        <div className="form-control">
          <MultiSelect
            label="Finish"
            options={FINISHES.map((x) => [x, FINISH_LABELS[x]] as [(typeof FINISHES)[number], string])}
            value={f.finishes}
            onChange={(finishes) => setF({ ...f, finishes })}
          />
        </div>

        <span className="form-label">Signed or altered</span>
        <div className="form-control">
          <MultiSelect
            label="Signed or altered"
            options={
              [
                ["SIGNED", "Signed"],
                ["ALTERED", "Altered"],
                ["PLAIN", "Neither"],
              ] as [MarkFilter, string][]
            }
            value={f.marks}
            onChange={(marks) => setF({ ...f, marks })}
          />
        </div>

        <span className="form-label">Condition</span>
        <div className="form-control">
          <MultiSelect label="Condition" options={CONDITIONS} value={f.conditions} onChange={(conditions) => setF({ ...f, conditions })} />
        </div>

        <span className="form-label">Language</span>
        <div className="form-control">
          <MultiSelect label="Language" options={LANGUAGES} value={f.languages} onChange={(languages) => setF({ ...f, languages })} />
        </div>

        <span className="form-label">Value of one copy ({currency})</span>
        <div className="form-control">
          <div className="inline price-range">
            <input type="text" inputMode="decimal" aria-label="Lowest value" placeholder="From" value={min} onChange={(e) => setMin(e.target.value)} />
            <span className="price-dash" aria-hidden="true">
              –
            </span>
            <input type="text" inputMode="decimal" aria-label="Highest value" placeholder="To" value={max} onChange={(e) => setMax(e.target.value)} />
          </div>
        </div>

        <span className="form-label">Decks</span>
        <div className="form-control">
          <select aria-label="Decks" value={f.decks} onChange={(e) => setF({ ...f, decks: e.target.value as DeckFilter })}>
            <option value="ANY">All cards</option>
            <option value="IN">Only cards in my decks</option>
            <option value="OUT">Only cards no deck uses (every copy is spare)</option>
            <option value="SPARE">Only spare copies (you own more than your decks use)</option>
          </select>
        </div>

        <span className="form-label">Used by deck</span>
        <div className="form-control">
          <select
            aria-label="Used by deck"
            value={f.deck ?? ""}
            disabled={deckNames.length === 0}
            onChange={(e) => setF({ ...f, deck: e.target.value || null })}
          >
            <option value="">Any deck</option>
            {deckNames.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
          {deckNames.length === 0 && <p className="muted small">No active decks yet: import your own decks from Archidekt (and set your Archidekt username) or make a deck in the app.</p>}
        </div>
      </div>
      <div className="dialog-actions">
        <button
          className="btn"
          onClick={() => {
            setF(EMPTY_FILTER);
            setMin("");
            setMax("");
          }}
        >
          Clear all
        </button>
        <span className="spacer" />
        <button className="btn" onClick={onClose}>
          Cancel
        </button>
        <button className="btn primary" onClick={() => onApply({ ...f, minPrice: num(min), maxPrice: num(max) })}>
          Show cards
        </button>
      </div>
    </Dialog>
  );
}
