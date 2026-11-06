"use client";

import { useEffect, useState } from "react";
import type { StatsCard } from "@/lib/deckstats";
import { fingerprint, winInput, type WinResult } from "@/lib/winodds";
import { WIN_ODDS_VERSION } from "@/lib/winodds-js/version";

/** The win turn's state: being worked out, worked out (null: no commander or too few cards), or failed. */
export type WinState = { status: "working" } | { status: "done"; win: WinResult | null } | { status: "failed"; error: string } | { status: "off" };

const KEY = "mtg.winOdds.";

/**
 * The deck's win turn (the app's WinOddsStore): kept in this browser per deck until its cards, colours or combos
 * change, else worked out in a Web Worker (half a minute or so). [ready] is false while card details are missing.
 */
export function useWinOdds(deckId: number, cards: StatsCard[], identity: string, saltCard: string | null, ready: boolean): WinState {
  const [state, setState] = useState<WinState>({ status: "off" });
  useEffect(() => {
    if (!ready || !cards.some((c) => c.commander)) {
      setState({ status: "off" });
      return;
    }
    const input = winInput(cards);
    const key = `${WIN_ODDS_VERSION}|${fingerprint(`${input}|${identity}|${saltCard ?? ""}`)}`;
    try {
      const kept = JSON.parse(localStorage.getItem(KEY + deckId) ?? "null") as { key: string; win: WinResult | null } | null;
      if (kept?.key === key) {
        setState({ status: "done", win: kept.win });
        return;
      }
    } catch {
      // Not kept.
    }
    setState({ status: "working" });
    const worker = new Worker(new URL("../lib/winodds.worker.ts", import.meta.url), { type: "module" });
    worker.onmessage = (e: MessageEvent<{ result?: string; error?: string }>) => {
      worker.terminate();
      if (e.data.error !== undefined || e.data.result === undefined) {
        setState({ status: "failed", error: e.data.error ?? "No answer." });
        return;
      }
      const win = JSON.parse(e.data.result) as WinResult | null;
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
  }, [deckId, cards, identity, saltCard, ready]);
  return state;
}
