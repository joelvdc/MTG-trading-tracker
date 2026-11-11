"use client";

import { useEffect, useState } from "react";
import type { StatsCard } from "@/lib/deckstats";
import { winInput, winKey, type WinResult } from "@/lib/winodds";
import { api } from "./api";

/** The win turn's state: being worked out, worked out (null: no commander or too few cards), or failed. */
export type WinState = { status: "working"; started: number; games: number } | { status: "done"; win: WinResult | null } | { status: "failed"; error: string } | { status: "off" };

const KEY = "mtg.winOdds.";
/** The games the app plays (WinOddsCalc.GAMES). */
const GAMES = 10_000;

/** A win turn the website kept for the deck (lib/winturns.ts), with the key of the list it belongs to. */
export type KeptWin = { key: string; win: WinResult | null } | null;

/**
 * The deck's win turn (the app's WinOddsStore): the one the website worked out for this list ([kept], when its key is
 * this list's), or kept in this browser until the deck's cards, colours or combos change, else worked out in a Web
 * Worker (half a minute or so) and handed to the website to keep. [ready] is false while card details are missing.
 * The key is lib/winodds.ts winKey (the same one the server uses, so they agree).
 */
export function useWinOdds(deckId: number, cards: StatsCard[], identity: string, saltCard: string | null, ready: boolean, kept: KeptWin = null): WinState {
  const [state, setState] = useState<WinState>({ status: "off" });
  useEffect(() => {
    if (!ready || !cards.some((c) => c.commander)) {
      setState({ status: "off" });
      return;
    }
    const input = winInput(cards);
    const key = winKey(input, identity, saltCard);
    if (kept?.key === key) {
      setState({ status: "done", win: kept.win });
      return;
    }
    try {
      const kept = JSON.parse(localStorage.getItem(KEY + deckId) ?? "null") as { key: string; win: WinResult | null } | null;
      if (kept?.key === key) {
        setState({ status: "done", win: kept.win });
        return;
      }
    } catch {
      // Not kept.
    }
    setState({ status: "working", started: 0, games: GAMES });
    const worker = new Worker(new URL("../lib/winodds.worker.ts", import.meta.url), { type: "module" });
    worker.onmessage = (e: MessageEvent<{ started?: number; result?: string; error?: string }>) => {
      if (e.data.started !== undefined && e.data.result === undefined && e.data.error === undefined) {
        setState({ status: "working", started: e.data.started, games: GAMES });
        return;
      }
      worker.terminate();
      if (e.data.error !== undefined || e.data.result === undefined) {
        setState({ status: "failed", error: e.data.error ?? "No answer." });
        return;
      }
      const win = JSON.parse(e.data.result) as WinResult | null;
      // Kept on the website too, for the Decks list, the rule-zero cards and other devices (best effort).
      void api(`/api/decks/${deckId}/winturn`, { key, result: e.data.result }).catch(() => undefined);
      try {
        localStorage.setItem(KEY + deckId, JSON.stringify({ key, win }));
      } catch {
        // Not kept: worked out again next time.
      }
      setState({ status: "done", win });
    };
    worker.onerror = (e) => {
      worker.terminate();
      setState({ status: "failed", error: e.message || "The worker stopped." });
    };
    worker.postMessage({ cards: input, identity, saltCard });
    return () => worker.terminate();
  }, [deckId, cards, identity, saltCard, ready, kept]);
  return state;
}
