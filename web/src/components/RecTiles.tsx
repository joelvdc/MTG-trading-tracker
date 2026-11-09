"use client";

import { useEffect, useState, type ReactNode } from "react";
import { recFacts, REC_OWNED, REC_TYPES, type RecCard, type RecFilter, type RecOwned, type RecResult, type RecSource } from "@/lib/recshared";
import { api } from "./api";
import { deckAction } from "./DeckEditDialogs";
import { ManaCost } from "./Mana";
import type { Layout } from "./ViewSwitch";

/** Where a recommended card can go: a local deck itself, a deck's maybeboard (a deck from Archidekt: its local one), or the wishlist. */
export type RecTarget = "deck" | "maybe" | "wish";

/** A deck a recommended card can be sent to. */
export interface RecDeck {
  id: number;
  name: string;
  local: boolean;
}

/** What `adding` holds while a card is being sent somewhere. */
export const addKey = (to: RecTarget, deckId: number | null, name: string) => `${to}:${to === "wish" ? "" : (deckId ?? "")}:${name}`;

/** Sends one copy of the card to the wishlist, a deck's maybeboard or a local deck. Answers with the error, if any. */
export async function sendRec(c: RecCard, to: RecTarget, deckId: number | null): Promise<string | null> {
  const card = c.card;
  if (!card) return "This card has no printing to add.";
  if (to === "wish") {
    const r = await api<{ error?: string }>("/api/wishlist", { edit: { type: "add", card, foil: !card.hasNonFoil && card.hasFoil, quantity: 1, anyPrinting: true } });
    return r.ok ? null : (r.data.error ?? "That did not work.");
  }
  if (deckId == null) return "No deck picked.";
  const finish = card.hasNonFoil ? "NONFOIL" : card.hasFoil ? "FOIL" : "ETCHED";
  return (await deckAction(deckId, to === "deck" ? { action: "add-card", card, finish, quantity: 1, category: null } : { action: "add-maybe", card, finish })).error;
}

// ---- The recommendations kept in this browser (IndexedDB: there can be many decks' worth) ----------------------

const DB = "mtg-recs";
const STORE = "recs";
// v3: results from before owned / wished existed are not used.
const keyOf = (deckId: number, source: RecSource) => `v3.${deckId}.${source}`;

function openDb(): Promise<IDBDatabase | null> {
  return new Promise((resolve) => {
    try {
      const r = indexedDB.open(DB, 1);
      r.onupgradeneeded = () => r.result.createObjectStore(STORE);
      r.onsuccess = () => resolve(r.result);
      r.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
}

/** A deck's kept recommendations from [source], or null. */
export async function cachedRecs(deckId: number, source: RecSource): Promise<RecResult | null> {
  const db = await openDb();
  if (!db) return null;
  return new Promise((resolve) => {
    const r = db.transaction(STORE).objectStore(STORE).get(keyOf(deckId, source));
    r.onsuccess = () => resolve((r.result as RecResult | undefined) ?? null);
    r.onerror = () => resolve(null);
  });
}

/** Keeps a deck's recommendations (not kept when the browser has no room: they're fetched again next time). */
export async function keepRecs(deckId: number, source: RecSource, result: RecResult): Promise<void> {
  const db = await openDb();
  if (!db) return;
  await new Promise<void>((resolve) => {
    try {
      const t = db.transaction(STORE, "readwrite");
      t.objectStore(STORE).put(result, keyOf(deckId, source));
      t.oncomplete = () => resolve();
      t.onerror = () => resolve();
      t.onabort = () => resolve();
    } catch {
      resolve();
    }
  });
}

/** Every deck's kept recommendations from [source], by deck id. */
export async function allCachedRecs(source: RecSource): Promise<Map<number, RecResult>> {
  const out = new Map<number, RecResult>();
  const db = await openDb();
  if (!db) return out;
  return new Promise((resolve) => {
    const r = db.transaction(STORE).objectStore(STORE).openCursor();
    r.onsuccess = () => {
      const cur = r.result;
      if (!cur) return resolve(out);
      const m = /^v3\.(-?\d+)\.(\w+)$/.exec(String(cur.key));
      if (m && m[2] === source) out.set(Number(m[1]), cur.value as RecResult);
      cur.continue();
    };
    r.onerror = () => resolve(out);
  });
}

// ---- One recommended card in the list or pictures view ----------------------------------------------------------

/** A card's single price: the nonfoil one, or the foil one (marked ✦) when the card only comes in foil. */
export function recPrice(c: RecCard, money: (eur: number | null) => string): { text: string; foil: boolean } | null {
  const card = c.card;
  if (!card) return null;
  if (card.fallbackEur != null || card.hasNonFoil) return { text: money(card.fallbackEur), foil: false };
  return { text: money(card.fallbackEurFoil), foil: card.fallbackEurFoil != null };
}

/**
 * One recommendation: in the pictures view the card's picture (a green corner when you own it, the buttons on it),
 * in the list view one line (✓ n, the buttons on the right); the name with "New", what the source says, the mana
 * cost above the price. [note] is an extra line (e.g. which decks it's recommended for).
 */
export function RecTile({
  c,
  mode,
  hoverOn,
  hover,
  money,
  actions,
  note,
  onOpen,
}: {
  c: RecCard;
  mode: Layout;
  hoverOn: boolean;
  hover: { follow: (url: string | null) => (e: React.MouseEvent) => void; hide: () => void };
  money: (eur: number | null) => string;
  actions: ReactNode;
  note?: string;
  onOpen: () => void;
}) {
  const owned = c.owned ?? 0;
  const price = recPrice(c, money);
  const open = () => {
    hover.hide();
    onOpen();
  };
  return (
    <li
      className={[c.card ? "clickable" : "", owned > 0 ? "owned" : ""].filter(Boolean).join(" ") || undefined}
      role={c.card ? "button" : undefined}
      tabIndex={c.card ? 0 : undefined}
      title={c.card ? "Open this card" : undefined}
      onClick={(e) => {
        if (!c.card || (e.target as HTMLElement).closest("button")) return;
        open();
      }}
      onKeyDown={(e) => {
        if (!c.card || e.target !== e.currentTarget) return;
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          open();
        }
      }}
      onMouseMove={mode === "list" && hoverOn ? hover.follow(c.card?.imageUrl ?? null) : hover.hide}
      onMouseLeave={hover.hide}
    >
      {mode === "images" && (
        <span className="rec-pic">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          {c.card?.imageUrl ? <img src={c.card.imageUrl} alt="" loading="lazy" /> : <span className="rec-noimg">{c.name}</span>}
          {owned > 0 && (
            <span className="own-ribbon" title={`You own ${owned}`}>
              <span>✓{owned > 1 ? owned : ""}</span>
            </span>
          )}
          {actions}
        </span>
      )}
      <span className="rec-text small">
        <span className="rec-name">
          <strong title={c.name}>{c.name}</strong>
          {c.isNew && <span className="tag">New</span>}
          {mode === "list" && owned > 0 && (
            <span className="own-tag" title={`You own ${owned}`}>
              ✓ {owned}
            </span>
          )}
        </span>
        {note && (
          <span className="rec-note" title={note}>
            {note}
          </span>
        )}
        <span className="muted">{recFacts(c)}</span>
      </span>
      {(c.manaCost || price) && (
        <span className="rec-side small">
          {c.manaCost && (
            <span className="rec-cost">
              <ManaCost cost={c.manaCost} size={14} />
            </span>
          )}
          {price && (
            <span className="num nowrap rec-price" title={price.foil ? "Only printed in foil" : undefined}>
              {price.text}
              {price.foil && <span className="foil-text"> ✦</span>}
            </span>
          )}
        </span>
      )}
      {mode === "list" && actions}
    </li>
  );
}

// ---- The filter row (the app's chips) and the remembered choices ------------------------------------------------

/** New cards · Owned or not / I own / I don't own · card type. */
export function RecFilters({ filter, onFilter }: { filter: RecFilter; onFilter: (change: Partial<RecFilter>) => void }) {
  return (
    <div className="chips rec-filters" role="group" aria-label="Filters">
      <button type="button" className={filter.newOnly ? "chip on" : "chip"} aria-pressed={filter.newOnly} onClick={() => onFilter({ newOnly: !filter.newOnly })}>
        New cards
      </button>
      {(Object.keys(REC_OWNED) as RecOwned[]).map((o) => (
        <button key={o} type="button" className={filter.owned === o ? "chip on" : "chip"} aria-pressed={filter.owned === o} onClick={() => onFilter({ owned: o })}>
          {REC_OWNED[o]}
        </button>
      ))}
      <select value={filter.type ?? ""} onChange={(e) => onFilter({ type: e.target.value || null })} aria-label="Card type">
        <option value="">All types</option>
        {REC_TYPES.map(([t, label]) => (
          <option key={t} value={t}>
            {label}
          </option>
        ))}
      </select>
    </div>
  );
}

const SOURCE_KEY = "mtg.recSource";
const VIEW_KEY = "mtg.recView";

/** A value kept in this browser's localStorage (read after the first render; storage may be unavailable). */
function useKept<T>(key: string, start: T, read: (raw: string) => T | null): [T, (next: T | ((old: T) => T)) => void] {
  const [value, setValue] = useState<T>(start);
  useEffect(() => {
    try {
      const raw = localStorage.getItem(key);
      const v = raw == null ? null : read(raw);
      if (v != null) setValue(v);
    } catch {
      // Storage unavailable.
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  const set = (next: T | ((old: T) => T)) =>
    setValue((old) => {
      const v = typeof next === "function" ? (next as (o: T) => T)(old) : next;
      try {
        localStorage.setItem(key, typeof v === "string" ? v : JSON.stringify(v));
      } catch {
        // Not remembered.
      }
      return v;
    });
  return [value, set];
}

/** The recommendation source, shared by the deck window and the all-decks page (and remembered). */
export const useRecSource = () => useKept<RecSource>(SOURCE_KEY, "edhrec", (s) => (s === "edhrec" || s === "recommander" ? s : null));

/** List or pictures, shared by the deck window and the all-decks page. */
export const useRecLayout = () => useKept<Layout>(VIEW_KEY, "images", (s) => (s === "images" || s === "list" ? s : null));

/** A remembered filter ([key] per page; the all-decks page starts at "I own", as in the app). */
export function useRecFilter(key: string, start: RecFilter): [RecFilter, (change: Partial<RecFilter>) => void] {
  const [f, setF] = useKept<RecFilter>(key, start, (s) => ({ ...start, ...(JSON.parse(s) as Partial<RecFilter>) }));
  return [f, (change) => setF((old) => ({ ...old, ...change }))];
}
