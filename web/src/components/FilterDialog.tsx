"use client";

import { useState } from "react";
import { CONDITIONS, FINISH_LABELS, FINISHES, LANGUAGES } from "@/lib/cards";
import {
  COLOR_MATCH_LABELS,
  EMPTY_FILTER,
  FILTER_RARITIES,
  FILTER_TYPES,
  type ColorMatch,
  type DeckFilter,
  type Filter,
  type MarkFilter,
} from "@/lib/colquery";
import type { Currency } from "@/lib/money";
import { Dialog } from "./Dialog";

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

/** The collection's filter (the app's filter sheet): everything that's awkward to type in the search field. */
export function FilterDialog({
  filter,
  sets,
  currency,
  perEuro,
  onApply,
  onClose,
}: {
  filter: Filter;
  /** Sets in the collection: code, name, copies. */
  sets: { code: string; name: string; copies: number }[];
  currency: Currency;
  perEuro: number;
  onApply: (f: Filter) => void;
  onClose: () => void;
}) {
  const [f, setF] = useState<Filter>(filter);
  const [setText, setSetText] = useState("");
  const [min, setMin] = useState(filter.minPrice != null ? (filter.minPrice * perEuro).toFixed(2) : "");
  const [max, setMax] = useState(filter.maxPrice != null ? (filter.maxPrice * perEuro).toFixed(2) : "");
  const num = (s: string) => {
    const t = s.trim().replace(",", ".");
    const n = Number(t);
    return t === "" || !Number.isFinite(n) ? null : n / perEuro;
  };
  const shownSets = sets.filter((s) => !setText.trim() || s.name.toLowerCase().includes(setText.toLowerCase()) || s.code === setText.trim().toLowerCase()).slice(0, 40);

  return (
    <Dialog title="Filter" onClose={onClose}>
      <div className="stack">
        <div className="field">
          <span>Colors</span>
          <Chips options={COLORS} value={f.colors} onChange={(colors) => setF({ ...f, colors })} />
          <select aria-label="How colors match" value={f.colorMatch} onChange={(e) => setF({ ...f, colorMatch: e.target.value as ColorMatch })}>
            {(Object.keys(COLOR_MATCH_LABELS) as ColorMatch[]).map((k) => (
              <option key={k} value={k}>
                {COLOR_MATCH_LABELS[k]}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <span>Type</span>
          <Chips options={FILTER_TYPES.map((t) => [t, t] as [string, string])} value={f.types} onChange={(types) => setF({ ...f, types })} />
        </div>
        <div className="field">
          <span>Rarity</span>
          <Chips options={FILTER_RARITIES} value={f.rarities} onChange={(rarities) => setF({ ...f, rarities })} />
        </div>
        <div className="field">
          <span>Sets</span>
          {f.sets.length > 0 && (
            <Chips
              options={f.sets.map((c) => [c, sets.find((s) => s.code === c)?.name ?? c.toUpperCase()] as [string, string])}
              value={f.sets}
              onChange={(v) => setF({ ...f, sets: v })}
            />
          )}
          <input type="search" placeholder="Find a set" value={setText} onChange={(e) => setSetText(e.target.value)} />
          <div className="chips scroll">
            {shownSets
              .filter((s) => !f.sets.includes(s.code))
              .map((s) => (
                <button key={s.code} className="chip" onClick={() => setF({ ...f, sets: [...f.sets, s.code] })}>
                  {s.name} <span className="muted">({s.copies})</span>
                </button>
              ))}
          </div>
        </div>
        <div className="field">
          <span>Finish</span>
          <Chips options={FINISHES.map((x) => [x, FINISH_LABELS[x]] as [(typeof FINISHES)[number], string])} value={f.finishes} onChange={(finishes) => setF({ ...f, finishes })} />
        </div>
        <div className="field">
          <span>Signed or altered</span>
          <Chips
            options={[
              ["SIGNED", "Signed"],
              ["ALTERED", "Altered"],
              ["PLAIN", "Neither"],
            ] as [MarkFilter, string][]}
            value={f.marks}
            onChange={(marks) => setF({ ...f, marks })}
          />
        </div>
        <div className="field">
          <span>Condition</span>
          <Chips options={CONDITIONS} value={f.conditions} onChange={(conditions) => setF({ ...f, conditions })} />
        </div>
        <div className="field">
          <span>Language</span>
          <Chips options={LANGUAGES} value={f.languages} onChange={(languages) => setF({ ...f, languages })} />
        </div>
        <div className="field">
          <span>Value of one copy ({currency})</span>
          <div className="inline">
            <input type="text" inputMode="decimal" aria-label="Lowest value" placeholder="From" value={min} onChange={(e) => setMin(e.target.value)} />
            <input type="text" inputMode="decimal" aria-label="Highest value" placeholder="To" value={max} onChange={(e) => setMax(e.target.value)} />
          </div>
        </div>
        <label className="field">
          <span>Decks</span>
          <select value={f.decks} onChange={(e) => setF({ ...f, decks: e.target.value as DeckFilter })}>
            <option value="ANY">All cards</option>
            <option value="IN">Only cards in my decks</option>
            <option value="OUT">Only cards in no deck</option>
          </select>
        </label>
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
