"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { api } from "@/components/api";
import { PlaytestBoard } from "@/components/playtest/PlaytestBoard";
import { PlaytestDialogs, type PlaytestDialog } from "@/components/playtest/PlaytestDialogs";
import { scryfallImage, tokens, type DeckToken, type StatsCard } from "@/lib/deckstats";
import { isSaga, startCounters } from "@/lib/entercounters";
import { backImageUrl, hasBackFace } from "@/lib/decklayout";
import type { DeckView } from "@/lib/deckshared";
import { act, newGame, type Action, type CardDef, type Game, type PlayerKey } from "@/lib/playtest";
import "../../../../components/playtest/playtest.css";

type Answer = DeckView & {
  stats: StatsCard[];
  layouts: Record<string, string>;
  tokenInfo: Record<string, { oi: string | null; pt: string | null }>;
};

/** The game's options, remembered per browser; both on by default (multiplayer Commander: everyone draws first turn). */
interface Options {
  freeMulligan: boolean;
  drawOnFirstTurn: boolean;
}

const DEFAULTS: Options = { freeMulligan: true, drawOnFirstTurn: true };
const OPTIONS_KEY = "mtg.playtestOptions";

function loadOptions(): Options {
  try {
    const raw = localStorage.getItem(OPTIONS_KEY);
    if (!raw) return DEFAULTS;
    const saved = JSON.parse(raw) as Partial<Options>;
    return {
      freeMulligan: typeof saved.freeMulligan === "boolean" ? saved.freeMulligan : DEFAULTS.freeMulligan,
      drawOnFirstTurn: typeof saved.drawOnFirstTurn === "boolean" ? saved.drawOnFirstTurn : DEFAULTS.drawOnFirstTurn,
    };
  } catch {
    return DEFAULTS;
  }
}

function saveOptions(options: Options): void {
  try {
    localStorage.setItem(OPTIONS_KEY, JSON.stringify(options));
  } catch {
    // Not remembered; fine.
  }
}

/** A fresh seed for a new game (the engine repeats a game exactly from the same seed). */
function newSeed(): number {
  return Date.now() % 2147483647;
}

const ZOOM_KEY = "mtg.ptZoom";
const ZOOM_DEFAULT = 100;
const ZOOM_MIN = 50;
const ZOOM_MAX = 200;
const ZOOM_STEP = 10;

/** The card size remembered per browser: a whole percent from 50 to 200 in steps of 10; 100 otherwise. */
function loadZoom(): number {
  try {
    const saved = Number(localStorage.getItem(ZOOM_KEY));
    if (Number.isInteger(saved) && saved >= ZOOM_MIN && saved <= ZOOM_MAX && saved % ZOOM_STEP === 0) return saved;
  } catch {
    // Storage unavailable: the default.
  }
  return ZOOM_DEFAULT;
}

function saveZoom(zoom: number): void {
  try {
    localStorage.setItem(ZOOM_KEY, String(zoom));
  } catch {
    // Not remembered; fine.
  }
}

/**
 * A deck statistic's power and toughness (such as "3/3", or one side a star) split at its first "/" and trimmed, for
 * the card's printed values. Each side stays undefined when it is missing or empty.
 */
function printedPT(text: string | null | undefined): { power?: string; toughness?: string } {
  if (!text) return {};
  const at = text.indexOf("/");
  if (at < 0) return {};
  const power = text.slice(0, at).trim();
  const toughness = text.slice(at + 1).trim();
  return { power: power === "" ? undefined : power, toughness: toughness === "" ? undefined : toughness };
}

/**
 * The deck's cards as the playtester's engine wants them: one entry per line (its quantity copies), with the type line
 * from the stats, the back face for a double-faced card, whether the front is a land, the counters it enters with and its
 * printed power and toughness.
 */
function buildCards(view: Answer): { def: CardDef; quantity: number }[] {
  const out: { def: CardDef; quantity: number }[] = [];
  for (const row of view.rows) {
    if (row.maybeboard) continue;
    const scryfallId = row.card.scryfallId;
    const imageUrl = row.card.imageUrl ?? scryfallImage(scryfallId);
    const stat = view.stats?.find((s) => s.name === row.name);
    const typeLine = stat ? (stat.typeLine ?? stat.types) : row.types;
    // A land is a land on its front face: before " — ", and the first face before " // ".
    const front = typeLine.split(" // ")[0].split(" — ")[0];
    out.push({
      def: {
        scryfallId,
        name: row.name,
        imageUrl,
        backImageUrl: hasBackFace(view.layouts?.[scryfallId], typeLine) ? backImageUrl(imageUrl) : null,
        typeLine,
        manaValue: row.cmc,
        isLand: front.split(/\s+/).includes("Land"),
        commander: row.commander,
        ...printedPT(stat?.powerToughness),
        startCounters: startCounters({
          name: row.name,
          typeLine,
          oracleText: stat?.oracleText,
          loyalty: stat?.loyalty,
          defense: stat?.defense,
        }),
        saga: isSaga(typeLine),
      },
      quantity: row.quantity,
    });
  }
  return out;
}

const COUNTER_KEYS: { key: PlayerKey; label: string }[] = [
  { key: "poison", label: "Poison" },
  { key: "energy", label: "Energy" },
  { key: "experience", label: "Experience" },
];

/** The playtester for one deck: the header (turns, counters, shortcuts) over the board and the hand. */
export default function PlaytestPage() {
  const params = useParams<{ id: string }>();
  const [view, setView] = useState<Answer | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [game, setGame] = useState<Game | null>(null);
  const [options, setOptions] = useState<Options>(() => (typeof window === "undefined" ? DEFAULTS : loadOptions()));
  const [dialog, setDialog] = useState<PlaytestDialog | null>(null);
  const [zoom, setZoom] = useState(ZOOM_DEFAULT);
  // The options in force when the first game was dealt (the toggles only apply at the next Restart).
  const optionsRef = useRef(options);
  const dealt = useRef(false);

  // The card size is only known in the browser, so it is read once mounted.
  useEffect(() => {
    setZoom(loadZoom());
  }, []);

  useEffect(() => {
    (async () => {
      const [s, d] = await Promise.all([
        api<{ loggedIn: boolean; folder?: string }>("/api/session"),
        api<Answer & { noFolder?: boolean }>(`/api/decks/${encodeURIComponent(params.id)}`),
      ]);
      if (!s.data.loggedIn || d.status === 401 || d.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || d.data.noFolder) return window.location.replace("/settings");
      if (d.ok && Array.isArray(d.data.rows)) {
        setView(d.data);
        setError(null);
      } else setError(d.data.error ?? "Could not load the deck.");
    })();
  }, [params.id]);

  const cards = useMemo(() => (view ? buildCards(view) : []), [view]);
  const deckTokens = useMemo<DeckToken[]>(() => {
    if (!view) return [];
    const oi: Record<string, string | undefined> = {};
    const pt: Record<string, string | null> = {};
    for (const [id, t] of Object.entries(view.tokenInfo ?? {})) {
      if (t.oi) oi[id] = t.oi;
      pt[id] = t.pt;
    }
    return tokens(view.stats ?? [], oi, pt, view.tokenPicks ?? {});
  }, [view]);

  // The deck is dealt once it is loaded: one game in React state from then on.
  useEffect(() => {
    if (!view || dealt.current) return;
    dealt.current = true;
    setGame(newGame(cards, { seed: newSeed(), ...optionsRef.current }));
  }, [view, cards]);

  const dispatch = (action: Action) => setGame((g) => (g ? act(g, action) : g));

  /** Deals the same cards again, with the options as they are now. */
  function restart() {
    optionsRef.current = options;
    setDialog(null);
    setGame((g) => (g ? newGame(g.state.deck, { seed: newSeed(), ...options }) : g));
  }

  function changeOptions(patch: Partial<Options>) {
    setOptions((old) => {
      const next = { ...old, ...patch };
      saveOptions(next);
      return next;
    });
  }

  /** Sets the card size, kept between 50% and 200%, and remembers it. */
  function changeZoom(next: number) {
    const value = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, next));
    setZoom(value);
    saveZoom(value);
  }

  /** Closes a dialog; closing the "look at the top" window (a placeholder for now) stops the look. */
  function closeDialog() {
    setDialog(null);
    if (game?.state.peek) dispatch({ type: "peekCancel" });
  }

  // The keyboard shortcuts, unless a dialog is open or the player is typing.
  useEffect(() => {
    if (!game) return;
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT" || target.isContentEditable)) return;
      if (dialog || game.state.peek) return;
      const key = e.key.toLowerCase();
      if ((e.ctrlKey || e.metaKey) && key === "z") {
        e.preventDefault();
        dispatch({ type: "undo" });
        return;
      }
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      if (e.key === "?") {
        e.preventDefault();
        setDialog({ kind: "help" });
        return;
      }
      switch (key) {
        case "d":
          e.preventDefault();
          dispatch({ type: "draw" });
          break;
        case "n":
          e.preventDefault();
          dispatch({ type: "nextTurn" });
          break;
        case "u":
          e.preventDefault();
          dispatch({ type: "untapAll" });
          break;
        case "s":
          e.preventDefault();
          dispatch({ type: "shuffle" });
          break;
        case "z":
          e.preventDefault();
          dispatch({ type: "undo" });
          break;
        case "t":
          // With cards selected, T taps or untaps them (the board's own key does that); otherwise it makes a token.
          if (document.querySelector(".pt-card.selected")) break;
          e.preventDefault();
          setDialog({ kind: "token" });
          break;
        case "m":
          if (game.state.phase === "mulligan") {
            e.preventDefault();
            dispatch({ type: "mulligan" });
          }
          break;
        case "k":
          if (game.state.phase === "mulligan") {
            e.preventDefault();
            dispatch({ type: "keep" });
          }
          break;
        default:
          break;
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [game, dialog]);

  if (!view || !game) {
    return (
      <main className="page">
        <Link href={`/decks/${encodeURIComponent(params.id)}`} className="small">
          ← Back to deck
        </Link>
        {error ? <div className="bar error">{error}</div> : <p className="muted center">Loading…</p>}
      </main>
    );
  }

  const state = game.state;
  const player = state.player;
  const peek = state.peek;

  return (
    <div className="pt-page" style={{ "--pt-zoom": zoom / 100 } as CSSProperties}>
      <header className="pt-bar">
        <Link href={`/decks/${encodeURIComponent(params.id)}`} className="btn small-btn pt-back-link">
          ← Back to deck
        </Link>
        <strong className="pt-deck-name">{view.deck.name}</strong>
        <span className="pt-turn">Turn {state.turn}</span>
        <div className="pt-actions">
          <button className="btn small-btn" onClick={() => dispatch({ type: "nextTurn" })} title="Next turn (N)">
            Next turn
          </button>
          <button className="btn small-btn" onClick={() => dispatch({ type: "untapAll" })} title="Untap all (U)">
            Untap all
          </button>
          <button className="btn small-btn" onClick={() => dispatch({ type: "draw" })} title="Draw a card (D)">
            Draw
          </button>
          <button className="btn small-btn" onClick={() => dispatch({ type: "shuffle" })} title="Shuffle the library (S)">
            Shuffle
          </button>
          <button className="btn small-btn" disabled={game.past.length === 0} onClick={() => dispatch({ type: "undo" })} title="Undo (Z)">
            Undo
          </button>
          <button className="btn small-btn" onClick={restart} title="Deal a new opening hand">
            Restart
          </button>
          <details className="menu">
            <summary className="btn small-btn">More ▾</summary>
            <div className="menu-items" onClick={(e) => ((e.currentTarget.parentElement as HTMLDetailsElement).open = false)}>
              <button onClick={() => setDialog({ kind: "token" })}>Create token…</button>
              <button onClick={() => setDialog({ kind: "dice" })}>Dice and coin…</button>
              <button onClick={() => setDialog({ kind: "log" })}>Game log</button>
              <button onClick={() => setDialog({ kind: "odds" })}>Odds</button>
              <button onClick={() => setDialog({ kind: "help" })}>Keyboard shortcuts</button>
              <span className="pt-menu-sep" />
              <label className="check small pt-toggle" title="Applies at the next Restart" onClick={(e) => e.stopPropagation()}>
                <input type="checkbox" checked={options.freeMulligan} onChange={(e) => changeOptions({ freeMulligan: e.target.checked })} />
                <span>Free first mulligan</span>
              </label>
              <label className="check small pt-toggle" title="Applies at the next Restart" onClick={(e) => e.stopPropagation()}>
                <input type="checkbox" checked={options.drawOnFirstTurn} onChange={(e) => changeOptions({ drawOnFirstTurn: e.target.checked })} />
                <span>Draw on turn 1</span>
              </label>
            </div>
          </details>
        </div>
        <div className="pt-player">
          <span className="pt-chip">
            <button className="pt-chip-btn" title="One life less (Shift: five)" onClick={(e) => dispatch({ type: "player", key: "life", delta: e.shiftKey ? -5 : -1 })}>
              −
            </button>
            <span className="pt-chip-value">Life {player.life}</span>
            <button className="pt-chip-btn" title="One life more (Shift: five)" onClick={(e) => dispatch({ type: "player", key: "life", delta: e.shiftKey ? 5 : 1 })}>
              +
            </button>
          </span>
          {COUNTER_KEYS.filter(({ key }) => player[key] !== 0).map(({ key, label }) => (
            <span key={key} className="pt-chip">
              <button className="pt-chip-btn" title={`One ${label.toLowerCase()} less`} onClick={() => dispatch({ type: "player", key, delta: -1 })}>
                −
              </button>
              <span className="pt-chip-value">
                {label} {player[key]}
              </span>
              <button className="pt-chip-btn" title={`One ${label.toLowerCase()} more`} onClick={() => dispatch({ type: "player", key, delta: 1 })}>
                +
              </button>
            </span>
          ))}
          <details className="menu">
            <summary className="btn small-btn">Counters ▾</summary>
            <div className="menu-items">
              {COUNTER_KEYS.map(({ key, label }) => (
                <span key={key} className="pt-menu-row">
                  <span>
                    {label} {player[key]}
                  </span>
                  <button className="pt-menu-mini" title={`One ${label.toLowerCase()} less`} onClick={() => dispatch({ type: "player", key, delta: -1 })}>
                    −
                  </button>
                  <button className="pt-menu-mini" title={`One ${label.toLowerCase()} more`} onClick={() => dispatch({ type: "player", key, delta: 1 })}>
                    +
                  </button>
                </span>
              ))}
            </div>
          </details>
        </div>
        <div className="pt-zoom" role="group" aria-label="Card size">
          <button className="btn small-btn" disabled={zoom <= 50} onClick={() => changeZoom(zoom - 10)} title="Smaller cards" aria-label="Smaller cards">
            −
          </button>
          <button className="btn small-btn pt-zoom-value" onClick={() => changeZoom(100)} title="Back to 100%">
            {zoom}%
          </button>
          <button className="btn small-btn" disabled={zoom >= 200} onClick={() => changeZoom(zoom + 10)} title="Bigger cards" aria-label="Bigger cards">
            +
          </button>
        </div>
      </header>

      <PlaytestBoard game={game} dispatch={dispatch} openDialog={(d) => setDialog(d)} dialogOpen={dialog !== null || peek !== null} />
      <PlaytestDialogs
        dialog={peek ? { kind: "peek" } : dialog}
        game={game}
        dispatch={dispatch}
        onClose={closeDialog}
        deckTokens={deckTokens}
        cards={cards}
      />
    </div>
  );
}
