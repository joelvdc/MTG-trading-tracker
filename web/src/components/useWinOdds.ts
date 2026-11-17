"use client";

import { useEffect, useRef, useState } from "react";
import type { StatsCard } from "@/lib/deckstats";
import { winInput, winKey, type WinResult } from "@/lib/winodds";
import { api } from "./api";

/**
 * The win turn's state: being worked out (with the first estimate, once the first games are in), worked out (null: no
 * commander or too few cards), or failed.
 */
export type WinState =
  | { status: "working"; started: number; games: number; estimate?: WinResult }
  | { status: "done"; win: WinResult | null }
  | { status: "failed"; error: string }
  | { status: "off" };

/** What the worker (lib/winodds.worker.ts) answers: how many games started, a batch's tally after a few of them, a batch's tally, a merged result, or an error. */
type Reply = { started?: number; snapshot?: string; tally?: string; result?: string; error?: string };

const KEY = "mtg.winOdds.";
/** The games the app plays (WinOddsCalc.GAMES). */
const GAMES = 10_000;
/** The games' batches, played side by side here (one worker each), as the app plays them. */
const BATCHES = 4;
/** The games of each batch the first estimate comes from: 4 × 250 = 1,000 games. */
const SNAPSHOT_AT = 250;

/** A win turn the website kept for the deck (lib/winturns.ts), with the key of the list it belongs to. */
export type KeptWin = { key: string; win: WinResult | null } | null;

/**
 * The deck's win turn (the app's WinOddsStore): the one the website worked out for this list ([kept], when its key is
 * this list's), or kept in this browser until the deck's cards, colours or combos change, else worked out in Web
 * Workers (about ten seconds): the app's 4 batches side by side, a worker each, and one more that merges their tallies
 * (winMergeJson), so the first estimate comes after 1,000 games and the answer after all 10,000. [ready] is false while
 * card details are missing. The key is lib/winodds.ts winKey (the same one the server uses, so they agree). [save]
 * keeps a finished result somewhere other than the saved deck's own answer (a precon); without it the result goes to
 * the deck's winturn address.
 */
export function useWinOdds(deckId: number, cards: StatsCard[], identity: string, saltCard: string | null, ready: boolean, kept: KeptWin = null, save?: (key: string, result: string) => void): WinState {
  const [state, setState] = useState<WinState>({ status: "off" });
  // The latest [save], so a page passing a new function each render doesn't start the work again.
  const saveRef = useRef(save);
  saveRef.current = save;
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

    // Every worker this effect starts, so cleanup (or a failure) stops them all: the 4 batches and the merges.
    const workers: Worker[] = [];
    // Each batch's latest games started (the state's started is their sum), its tally after a few games, its full tally.
    const counts = Array<number>(BATCHES).fill(0);
    const snapshots = Array<string | null>(BATCHES).fill(null);
    const tallies = Array<string | null>(BATCHES).fill(null);
    let estimate: WinResult | null = null;
    let estimating = false;
    // False once the answer is in, the work failed or the page moved on: later messages are ignored.
    let live = true;

    const stop = () => {
      live = false;
      for (const w of workers) w.terminate();
    };

    /** Working, with the batches' games added up, and the first estimate once there is one. */
    const progress = (): WinState => {
      const started = counts.reduce((a, b) => a + b, 0);
      return estimate === null ? { status: "working", started, games: GAMES } : { status: "working", started, games: GAMES, estimate };
    };

    const fail = (error: string) => {
      if (!live) return;
      stop();
      setState({ status: "failed", error });
    };

    /** The answer: kept on the website (best effort) and in this browser, then shown. */
    const finish = (result: string) => {
      stop();
      const win = JSON.parse(result) as WinResult | null;
      // A precon under its own name, a saved deck for the Decks list, the rule-zero cards and other devices.
      if (saveRef.current) saveRef.current(key, result);
      else void api(`/api/decks/${deckId}/winturn`, { key, result }).catch(() => undefined);
      try {
        localStorage.setItem(KEY + deckId, JSON.stringify({ key, win }));
      } catch {
        // Not kept: worked out again next time.
      }
      setState({ status: "done", win });
    };

    const startWorker = (message: object, onMessage: (data: Reply, worker: Worker) => void) => {
      const worker = new Worker(new URL("../lib/winodds.worker.ts", import.meta.url), { type: "module" });
      workers.push(worker);
      worker.onmessage = (e: MessageEvent<Reply>) => {
        if (!live) return;
        if (e.data.error !== undefined) {
          fail(e.data.error);
          return;
        }
        onMessage(e.data, worker);
      };
      worker.onerror = (e) => fail(e.message || "The worker stopped.");
      worker.postMessage(message);
    };

    /** A worker that merges tallies: the estimate (after a few games of each batch), or the answer (the 4 full tallies). */
    const merge = (list: string[], answer: boolean) => {
      // Each tally is already JSON text: joined into one JSON list (not JSON.stringify, which would quote them).
      startWorker({ mode: "merge", cards: input, identity, saltCard, tallies: `[${list.join(",")}]` }, (data, worker) => {
        worker.terminate();
        if (data.result === undefined) {
          fail("No answer.");
          return;
        }
        if (answer) {
          finish(data.result);
          return;
        }
        // An estimate ("null": a deck that can't be played, whose answer says so).
        const win = JSON.parse(data.result) as WinResult | null;
        if (win === null) return;
        estimate = win;
        setState(progress());
      });
    };

    for (let batch = 0; batch < BATCHES; batch++) {
      const index = batch;
      startWorker({ mode: "batch", cards: input, identity, saltCard, batch: index, snapshotAt: SNAPSHOT_AT }, (data, worker) => {
        if (data.started !== undefined) {
          counts[index] = data.started;
          setState(progress());
          return;
        }
        if (data.snapshot !== undefined) {
          snapshots[index] = data.snapshot;
          // All 4 snapshots: the first estimate, from the first 1,000 games.
          if (!estimating && snapshots.every((s) => s !== null)) {
            estimating = true;
            merge(snapshots.filter((s): s is string => s !== null), false);
          }
          return;
        }
        if (data.tally === undefined) {
          fail("No answer.");
          return;
        }
        tallies[index] = data.tally;
        worker.terminate();
        if (!tallies.every((t) => t !== null)) return;
        // A deck that can't be played: no answer, as before.
        if (tallies.some((t) => t === "null")) {
          finish("null");
          return;
        }
        merge(tallies.filter((t): t is string => t !== null), true);
      });
    }
    return () => stop();
  }, [deckId, cards, identity, saltCard, ready, kept]);
  return state;
}
