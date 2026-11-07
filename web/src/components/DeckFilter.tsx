"use client";

import { useEffect, useMemo, useState } from "react";
import { api } from "@/components/api";
import { isQueryError, matches, parseQuery, unsupportedKeys, type QueryCard } from "@/lib/cardquery";
import { costColours } from "@/lib/decklayout";
import type { DeckRow } from "@/lib/deckshared";
import type { StatsCard } from "@/lib/deckstats";

export type FilterMode = "ONLY" | "HIGHLIGHT";
const MODE_KEY = "mtg.deckFilterMode";
const MAX_NAMES = 250;

/** A deck line as the filter sees it (cardquery's QueryCard), from the line and its Scryfall details when known. */
function queryCard(r: DeckRow, s: StatsCard | undefined): QueryCard {
  const cost = s?.manaCost ?? "";
  const text = s?.oracleText ?? "";
  const colours = costColours(cost).split("").filter(Boolean);
  // Colour identity isn't kept per card: the cost's colours plus the colour symbols in the rules text.
  const identity = costColours(`${cost} ${(text.match(/\{[^}]+\}/g) ?? []).join("")}`).split("").filter(Boolean);
  const [power, toughness] = s?.powerToughness?.split("/") ?? [null, null];
  return {
    name: r.name,
    typeLine: s?.typeLine ?? r.types,
    oracleText: text,
    manaCost: cost,
    mv: r.cmc,
    colors: colours,
    colorIdentity: identity,
    power: power ?? null,
    toughness: toughness ?? null,
    loyalty: s?.loyalty ?? null,
    rarity: r.card.rarity || null,
    setCode: r.card.setCode,
    commander: r.commander,
    gameChanger: r.gameChanger,
    priceEur: r.unitEur,
    category: r.category,
    tags: r.categories,
  };
}

const front = (name: string) => name.split(" // ")[0].toLowerCase();

/**
 * The deck's syntax filter: Scryfall's search words, answered in the browser where it can; a search with words only
 * Scryfall knows (otag:, art:, …) goes to Scryfall whole, for the deck's cards, so "or" and "-" stay right.
 */
export function useDeckFilter(rows: DeckRow[], stats: StatsCard[]) {
  const [text, setText] = useState("");
  const [mode, setModeState] = useState<FilterMode>("ONLY");
  const [remote, setRemote] = useState<{ query: string; names: Set<string> } | null>(null);
  const [remoteError, setRemoteError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    try {
      if (localStorage.getItem(MODE_KEY) === "HIGHLIGHT") setModeState("HIGHLIGHT");
    } catch {
      // Default mode.
    }
  }, []);
  const setMode = (m: FilterMode) => {
    setModeState(m);
    try {
      localStorage.setItem(MODE_KEY, m);
    } catch {
      // Not remembered.
    }
  };

  const query = text.trim();
  const parsed = useMemo(() => (query ? parseQuery(query) : null), [query]);
  const needsScryfall = parsed !== null && !isQueryError(parsed) && unsupportedKeys(parsed).length > 0;
  const names = useMemo(() => [...new Set(rows.map((r) => r.name))].sort(), [rows]);
  const namesKey = names.join("\n");

  useEffect(() => {
    if (!needsScryfall) {
      setRemoteError(null);
      setBusy(false);
      return;
    }
    if (names.length > MAX_NAMES) {
      setRemoteError(`Words like ${unsupportedKeys(parsed!).join(", ")} are looked up on Scryfall, which takes up to ${MAX_NAMES} different cards.`);
      return;
    }
    let stale = false;
    setBusy(true);
    const timer = setTimeout(async () => {
      const r = await api<{ names?: string[] }>("/api/cards/query", { query, names });
      if (stale) return;
      setBusy(false);
      if (r.ok && Array.isArray(r.data.names)) {
        setRemote({ query, names: new Set(r.data.names.flatMap((n) => [n.toLowerCase(), front(n)])) });
        setRemoteError(null);
      } else setRemoteError(r.data.error ?? "Scryfall didn't answer.");
    }, 600);
    return () => {
      stale = true;
      clearTimeout(timer);
    };
    // namesKey stands for names.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [needsScryfall, query, namesKey]);

  const test = useMemo((): ((r: DeckRow) => boolean) | null => {
    if (!parsed || isQueryError(parsed)) return null;
    if (needsScryfall) {
      if (!remote || remote.query !== query) return null;
      return (r) => remote.names.has(r.name.toLowerCase()) || remote.names.has(front(r.name));
    }
    const byId = new Map(stats.map((s) => [s.scryfallId, s]));
    const cache = new Map<string, boolean>();
    return (r) => {
      let hit = cache.get(r.lineUid);
      if (hit === undefined) {
        hit = matches(parsed, queryCard(r, byId.get(r.original?.scryfallId ?? r.card.scryfallId) ?? byId.get(r.card.scryfallId)));
        cache.set(r.lineUid, hit);
      }
      return hit;
    };
  }, [parsed, needsScryfall, remote, query, stats]);

  const error = parsed && isQueryError(parsed) ? parsed.error : remoteError;
  return { text, setText, mode, setMode, test, error, busy: needsScryfall && busy, scryfall: needsScryfall };
}

export type DeckFilterState = ReturnType<typeof useDeckFilter>;

/** The filter box in the deck toolbar, with the Show only / Highlight switch. */
export function DeckFilterBox({ filter, shown, total }: { filter: DeckFilterState; shown: number | null; total: number }) {
  return (
    <div className="deck-filter">
      <input
        type="search"
        className="deck-filter-input"
        value={filter.text}
        onChange={(e) => filter.setText(e.target.value)}
        placeholder="Filter deck (eg: o:haste c:red)"
        aria-label="Filter the deck with Scryfall's search words"
        title="Scryfall's search words: t:creature, o:draw, c:rg, mv<=2, is:commander, cat:ramp, … Words only Scryfall knows (otag:, art:) are looked up there."
      />
      <div className="seg text" role="group" aria-label="Filter mode">
        <button className={filter.mode === "ONLY" ? "on" : ""} onClick={() => filter.setMode("ONLY")} title="Show only the matching cards">
          Show only
        </button>
        <button className={filter.mode === "HIGHLIGHT" ? "on" : ""} onClick={() => filter.setMode("HIGHLIGHT")} title="Show every card, the matching ones highlighted">
          Highlight
        </button>
      </div>
      {filter.text.trim() && (
        <span className={filter.error ? "small warn-text" : "small muted"} role="status">
          {filter.error ?? (filter.busy ? "Asking Scryfall…" : shown !== null ? `${shown} of ${total} match` : "")}
        </span>
      )}
    </div>
  );
}
