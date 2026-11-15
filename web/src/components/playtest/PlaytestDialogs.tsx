"use client";

import { useState, type JSX } from "react";
import { Dialog } from "@/components/Dialog";
import type { DeckToken } from "@/lib/deckstats";
import type { Action, CardDef, Game } from "@/lib/playtest";

/**
 * The playtest page's dialogs. This file is the placeholder the page already calls: every dialog shows its name and
 * "Coming soon" for now, except the one for a number of cards (draw, look, scry, surveil, mill, exile), which works,
 * and the keyboard shortcuts, which are only text. Another job fills in the rest (the zone lists, search, peek,
 * counters, the token picker, dice and coin, the log and the odds).
 */
export type PlaytestDialog =
  | { kind: "zone"; zone: "graveyard" | "exile" | "library" }
  | { kind: "number"; purpose: "draw" | "look" | "scry" | "surveil" | "mill" | "exile" }
  | { kind: "search" }
  | { kind: "peek" } // shown automatically while state.peek is set
  | { kind: "counter"; id: string }
  | { kind: "libraryPosition"; id: string }
  | { kind: "token" }
  | { kind: "dice" }
  | { kind: "log" }
  | { kind: "odds" }
  | { kind: "help" };

const KIND_TITLES: Record<PlaytestDialog["kind"], string> = {
  zone: "Zone",
  number: "Cards",
  search: "Search",
  peek: "Look at the top",
  counter: "Counter",
  libraryPosition: "Library position",
  token: "Create token",
  dice: "Dice and coin",
  log: "Game log",
  odds: "Odds",
  help: "Keyboard shortcuts",
};

const PURPOSE_TITLES: Record<"draw" | "look" | "scry" | "surveil" | "mill" | "exile", string> = {
  draw: "Draw cards",
  look: "Look at the top",
  scry: "Scry",
  surveil: "Surveil",
  mill: "Mill",
  exile: "Exile the top",
};

const ZONE_TITLES: Record<"graveyard" | "exile" | "library", string> = {
  graveyard: "Graveyard",
  exile: "Exile",
  library: "Library",
};

const SHORTCUTS: [string, string][] = [
  ["D", "Draw a card"],
  ["N", "Next turn"],
  ["U", "Untap all"],
  ["S", "Shuffle the library"],
  ["M", "Mulligan (opening hand)"],
  ["K", "Keep the opening hand"],
  ["Z or Ctrl+Z", "Undo"],
  ["T", "Create a token"],
  ["?", "These shortcuts"],
  ["Esc", "Close a menu or dialog"],
];

export function PlaytestDialogs({
  dialog,
  game,
  dispatch,
  onClose,
  deckTokens,
  cards,
}: {
  dialog: PlaytestDialog | null;
  game: Game;
  dispatch: (a: Action) => void;
  onClose: () => void;
  deckTokens: DeckToken[];
  cards: { def: CardDef; quantity: number }[];
}): JSX.Element | null {
  if (dialog === null) return null;
  if (dialog.kind === "number") return <NumberDialog purpose={dialog.purpose} dispatch={dispatch} onClose={onClose} />;
  if (dialog.kind === "help") return <HelpDialog onClose={onClose} />;

  const title = dialog.kind === "zone" ? ZONE_TITLES[dialog.zone] : KIND_TITLES[dialog.kind];
  // The other dialogs are built by another job; until then this says so (and the props they will need stay unused).
  void game;
  void deckTokens;
  void cards;
  return (
    <Dialog title={title} onClose={onClose}>
      <div className="stack">
        <p className="muted">Coming soon.</p>
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </Dialog>
  );
}

/** How many cards: one to twenty, then the action that draws, looks, scries, surveils, mills or exiles them. */
function NumberDialog({
  purpose,
  dispatch,
  onClose,
}: {
  purpose: "draw" | "look" | "scry" | "surveil" | "mill" | "exile";
  dispatch: (a: Action) => void;
  onClose: () => void;
}) {
  const [count, setCount] = useState(1);
  const run = () => {
    const n = Math.min(20, Math.max(1, Math.round(Number.isFinite(count) ? count : 1)));
    dispatch(
      purpose === "draw"
        ? { type: "draw", count: n }
        : purpose === "mill"
          ? { type: "mill", count: n }
          : purpose === "exile"
            ? { type: "exileTop", count: n }
            : { type: "peek", count: n, mode: purpose },
    );
    onClose();
  };
  return (
    <Dialog title={PURPOSE_TITLES[purpose]} onClose={onClose}>
      <div className="stack">
        <label className="inline">
          <span className="muted">Cards</span>
          <input
            type="number"
            min={1}
            max={20}
            value={count}
            autoFocus
            onChange={(e) => setCount(Number(e.target.value))}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                run();
              }
            }}
          />
        </label>
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn primary" onClick={run}>
            OK
          </button>
        </div>
      </div>
    </Dialog>
  );
}

function HelpDialog({ onClose }: { onClose: () => void }) {
  return (
    <Dialog title="Keyboard shortcuts" onClose={onClose}>
      <div className="stack">
        <ul className="plain-list">
          {SHORTCUTS.map(([key, what]) => (
            <li key={key} className="small">
              <span className="pt-key">{key}</span> <span className="muted">{what}</span>
            </li>
          ))}
        </ul>
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </Dialog>
  );
}
