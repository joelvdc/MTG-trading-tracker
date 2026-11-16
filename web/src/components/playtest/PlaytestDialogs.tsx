"use client";

import { useEffect, useRef, useState, type JSX } from "react";
import { Dialog } from "@/components/Dialog";
import { CardRulesTabs } from "@/components/CardRules";
import { FlipPicture } from "@/components/FlipPicture";
import { useHoverCard, useHoverSetting } from "@/components/HoverCard";
import { api } from "@/components/api";
import { scryfallImage, type DeckToken } from "@/lib/deckstats";
import {
  chanceByTurn,
  landOdds,
  ZONE_IDS,
  type Action,
  type CardDef,
  type CardInstance,
  type Game,
  type ZoneId,
} from "@/lib/playtest";
import { CustomFace } from "./PlaytestBoard";

/**
 * The playtest page's dialogs: the zone lists, the library search, the look/scry/surveil window, counters, putting a
 * card into the library, the token picker, dice and coin, the game log, the odds and the keyboard shortcuts. Every
 * dialog closes with Esc, its Close/Cancel button or a click beside it; the page owns which one is open.
 */
export type PlaytestDialog =
  | { kind: "zone"; zone: "graveyard" | "exile" | "library" }
  | { kind: "number"; purpose: "draw" | "look" | "scry" | "surveil" | "mill" | "exile" }
  | { kind: "search" }
  | { kind: "peek" } // shown automatically while state.peek is set
  | { kind: "counter"; id: string }
  | { kind: "card"; id: string }
  | { kind: "libraryPosition"; id: string }
  | { kind: "token"; custom?: boolean }
  | { kind: "dice" }
  | { kind: "log" }
  | { kind: "odds" }
  | { kind: "help" };

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

const PEEK_TITLES: Record<"look" | "scry" | "surveil", string> = {
  look: "Look at the top",
  scry: "Scry",
  surveil: "Surveil",
};

/** The card types the search offers as chips (one word each, as they appear in a type line). */
const CARD_TYPES = ["Land", "Creature", "Artifact", "Enchantment", "Instant", "Sorcery", "Planeswalker", "Battle"];

/** The counters the counter dialog suggests (a card may get any name typed in). */
const COUNTER_SUGGESTIONS = [
  "+1/+1",
  "-1/-1",
  "Loyalty",
  "Charge",
  "Shield",
  "Stun",
  "Oil",
  "Lore",
  "Time",
  "Fade",
  "Age",
  "Quest",
  "Experience",
  "Poison",
];

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

/** A card's picture: the face that is up, and nothing when it is face down. */
function faceUrl(card: CardInstance): string | null {
  if (card.faceDown) return null;
  return card.flipped && card.def.backImageUrl ? card.def.backImageUrl : card.def.imageUrl;
}

function ordinal(n: number): string {
  const mod100 = n % 100;
  if (mod100 >= 11 && mod100 <= 13) return `${n}th`;
  const mod10 = n % 10;
  if (mod10 === 1) return `${n}st`;
  if (mod10 === 2) return `${n}nd`;
  if (mod10 === 3) return `${n}rd`;
  return `${n}th`;
}

/** A whole number kept between [min] and [max]. */
function whole(n: number, min: number, max: number): number {
  const value = Number.isFinite(n) ? Math.round(n) : min;
  return Math.min(max, Math.max(min, value));
}

function percent(chance: number): string {
  return `${(chance * 100).toFixed(1)}%`;
}

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
  switch (dialog.kind) {
    case "number":
      return <NumberDialog purpose={dialog.purpose} dispatch={dispatch} onClose={onClose} />;
    case "zone":
      return <ZoneDialog zone={dialog.zone} game={game} dispatch={dispatch} onClose={onClose} />;
    case "search":
      return <SearchDialog game={game} dispatch={dispatch} onClose={onClose} />;
    case "peek":
      return <PeekDialog game={game} dispatch={dispatch} onClose={onClose} />;
    case "counter":
      return <CounterDialog id={dialog.id} game={game} dispatch={dispatch} onClose={onClose} />;
    case "card":
      return <CardViewDialog id={dialog.id} game={game} onClose={onClose} />;
    case "libraryPosition":
      return <LibraryPositionDialog id={dialog.id} game={game} dispatch={dispatch} onClose={onClose} />;
    case "token":
      return <TokenDialog deckTokens={deckTokens} dispatch={dispatch} onClose={onClose} custom={dialog.custom} />;
    case "dice":
      return <DiceDialog onClose={onClose} />;
    case "log":
      return <LogDialog game={game} onClose={onClose} />;
    case "odds":
      return <OddsDialog game={game} cards={cards} onClose={onClose} />;
    case "help":
      return <HelpDialog onClose={onClose} />;
  }
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
    const n = whole(count, 1, 20);
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

/** The graveyard, exile or library as a grid of pictures, each with the places it can go. */
function ZoneDialog({
  zone,
  game,
  dispatch,
  onClose,
}: {
  zone: "graveyard" | "exile" | "library";
  game: Game;
  dispatch: (a: Action) => void;
  onClose: () => void;
}) {
  const [filter, setFilter] = useState("");
  const [hoverOn] = useHoverSetting();
  const hover = useHoverCard(hoverOn);
  const state = game.state;
  const ids = state.zones[zone];
  const text = filter.trim().toLowerCase();

  // The library reads from the top; the graveyard and exile show the newest first, as the piles do.
  const entries =
    zone === "library"
      ? ids.map((id, i) => ({ id, position: `${ordinal(i + 1)} from the top` as string | null }))
      : ids
          .slice()
          .reverse()
          .map((id) => ({ id, position: null as string | null }));
  const shown = text === "" ? entries : entries.filter(({ id }) => state.cards[id].def.name.toLowerCase().includes(text));

  /** Moves a card out of this zone: a search shuffles the library, except when the card stays in it (top or bottom). */
  const send = (id: string, to: ZoneId, position?: "top" | "bottom") => {
    if (zone === "library" && to !== "library") dispatch({ type: "tutor", id, to });
    else dispatch({ type: "move", id, to, ...(position ? { position } : {}) });
  };

  const buttons = (id: string) => (
    <div className="pt-zone-btns">
      <button className="btn" onClick={() => send(id, "hand")} title="Move it to your hand">
        Hand
      </button>
      <button className="btn" onClick={() => send(id, "battlefield")} title="Move it to the battlefield">
        Battlefield
      </button>
      <button className="btn" onClick={() => send(id, "library", "top")} title="Put it on top of the library">
        Top of library
      </button>
      <button className="btn" onClick={() => send(id, "library", "bottom")} title="Put it on the bottom of the library">
        Bottom of library
      </button>
      {zone !== "graveyard" && (
        <button className="btn" onClick={() => send(id, "graveyard")} title="Move it to the graveyard">
          Graveyard
        </button>
      )}
      {zone !== "exile" && (
        <button className="btn" onClick={() => send(id, "exile")} title="Exile it">
          Exile
        </button>
      )}
    </div>
  );

  return (
    <Dialog title={`${ZONE_TITLES[zone]} (${ids.length})`} onClose={onClose}>
      <div className="stack">
        <div className="pt-filter">
          <input type="search" value={filter} autoFocus placeholder="Filter by name" onChange={(e) => setFilter(e.target.value)} />
        </div>
        {entries.length === 0 ? (
          <p className="muted">{zone === "library" ? "The library is empty." : `${ZONE_TITLES[zone]} is empty.`}</p>
        ) : shown.length === 0 ? (
          <p className="muted">No cards match that name.</p>
        ) : (
          <div className="pt-zone-grid">
            {shown.map(({ id, position }) => {
              const card = state.cards[id];
              const url = faceUrl(card);
              return (
                <div key={id} className="pt-zone-item" onMouseMove={hover.follow(url)} onMouseLeave={hover.hide}>
                  <span className="pt-zone-pic">
                    {url ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={url} alt="" loading="lazy" />
                    ) : (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img className="pt-zone-back" src="/card-back.jpg" alt="" loading="lazy" />
                    )}
                  </span>
                  <span className="pt-zone-name">{card.faceDown ? `${card.def.name} (face down)` : card.def.name}</span>
                  {position && <span className="pt-zone-pos muted">{position}</span>}
                  {buttons(id)}
                </div>
              );
            })}
          </div>
        )}
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
      {hover.picture}
    </Dialog>
  );
}

/** Search a library by name and type; one card goes where it is told, then the window closes. */
function SearchDialog({
  game,
  dispatch,
  onClose,
}: {
  game: Game;
  dispatch: (a: Action) => void;
  onClose: () => void;
}) {
  const [filter, setFilter] = useState("");
  const [types, setTypes] = useState<string[]>([]);
  const [hoverOn] = useHoverSetting();
  const hover = useHoverCard(hoverOn);
  const picked = useRef(false);
  const state = game.state;
  const text = filter.trim().toLowerCase();

  // Alphabetical (not library order), so the order of the library is not revealed.
  const shown = state.zones.library
    .filter((id) => {
      const def = state.cards[id].def;
      if (text !== "" && !def.name.toLowerCase().includes(text)) return false;
      return types.length === 0 || types.some((t) => new RegExp(`\\b${t}\\b`, "i").test(def.typeLine));
    })
    .sort((a, b) => state.cards[a].def.name.localeCompare(state.cards[b].def.name, "en", { sensitivity: "base" }));

  const take = (id: string, to: ZoneId) => {
    picked.current = true;
    dispatch({ type: "tutor", id, to, ...(to === "library" ? { position: "top" } : {}) });
    onClose();
  };

  // Closing without a pick still shuffles: a search is a shuffle even when nothing is found.
  const close = () => {
    if (!picked.current) dispatch({ type: "shuffle" });
    onClose();
  };

  const toggleType = (type: string) =>
    setTypes((old) => (old.includes(type) ? old.filter((t) => t !== type) : [...old, type]));

  return (
    <Dialog title="Search the library" onClose={close}>
      <div className="stack">
        <div className="pt-filter">
          <input type="search" value={filter} autoFocus placeholder="Filter by name" onChange={(e) => setFilter(e.target.value)} />
        </div>
        <div className="chips">
          {CARD_TYPES.map((type) => (
            <button
              key={type}
              className={"chip" + (types.includes(type) ? " on" : "")}
              aria-pressed={types.includes(type)}
              onClick={() => toggleType(type)}
            >
              {type}
            </button>
          ))}
        </div>
        {state.zones.library.length === 0 ? (
          <p className="muted">The library is empty.</p>
        ) : shown.length === 0 ? (
          <p className="muted">No cards match those filters.</p>
        ) : (
          <div className="pt-pick-list">
            {shown.map((id) => {
              const card = state.cards[id];
              const url = faceUrl(card);
              return (
                <div key={id} className="pt-pick-row" onMouseMove={hover.follow(url)} onMouseLeave={hover.hide}>
                  <span className="pt-pick-pic">
                    {url ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={url} alt="" loading="lazy" />
                    ) : (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img className="pt-zone-back" src="/card-back.jpg" alt="" loading="lazy" />
                    )}
                  </span>
                  <div className="pt-pick-info">
                    <span className="pt-pick-name">{card.def.name}</span>
                    <span className="muted small">{card.def.typeLine}</span>
                  </div>
                  <div className="pt-pick-btns">
                    <button className="btn" onClick={() => take(id, "hand")}>
                      To hand
                    </button>
                    <button className="btn" onClick={() => take(id, "battlefield")}>
                      To battlefield
                    </button>
                    <button className="btn" onClick={() => take(id, "library")}>
                      To top of library
                    </button>
                    <button className="btn" onClick={() => take(id, "graveyard")}>
                      To graveyard
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
        <div className="dialog-actions">
          <span className="muted small">Closing shuffles the library.</span>
          <span className="spacer" />
          <button className="btn" onClick={close}>
            Close
          </button>
        </div>
      </div>
      {hover.picture}
    </Dialog>
  );
}

type PeekWhere = "top" | "bottom" | "graveyard";

/** The look, scry or surveil window: the peeked cards, each one going to the top, the bottom or the graveyard. */
function PeekDialog({
  game,
  dispatch,
  onClose,
}: {
  game: Game;
  dispatch: (a: Action) => void;
  onClose: () => void;
}) {
  const peek = game.state.peek;
  const [items, setItems] = useState<{ id: string; where: PeekWhere }[]>(() =>
    (peek?.ids ?? []).map((id) => ({ id, where: "top" as PeekWhere })),
  );
  const [hoverOn] = useHoverSetting();
  const hover = useHoverCard(hoverOn);
  if (!peek) return null;

  const grave = peek.mode === "surveil";
  const choices: { where: PeekWhere; label: string }[] = grave
    ? [
        { where: "top", label: "Top" },
        { where: "graveyard", label: "Graveyard" },
      ]
    : [
        { where: "top", label: "Top" },
        { where: "bottom", label: "Bottom" },
      ];

  /** Puts a card in its group (top first, then bottom or graveyard), keeping the order of each group. */
  const setWhere = (id: string, where: PeekWhere) =>
    setItems((old) => {
      const rest = old.filter((x) => x.id !== id);
      if (where !== "top") return [...rest, { id, where }];
      let at = 0;
      for (let i = 0; i < rest.length; i += 1) if (rest[i].where === "top") at = i + 1;
      return [...rest.slice(0, at), { id, where }, ...rest.slice(at)];
    });

  /** Moves a card one place among the cards that go to the same place. */
  const nudge = (id: string, dir: -1 | 1) =>
    setItems((old) => {
      const i = old.findIndex((x) => x.id === id);
      const j = i + dir;
      if (i < 0 || j < 0 || j >= old.length || old[j].where !== old[i].where) return old;
      const next = old.slice();
      const swap = next[i];
      next[i] = next[j];
      next[j] = swap;
      return next;
    });

  const done = () => {
    dispatch({
      type: "peekDone",
      top: items.filter((x) => x.where === "top").map((x) => x.id),
      bottom: items.filter((x) => x.where === "bottom").map((x) => x.id),
      graveyard: items.filter((x) => x.where === "graveyard").map((x) => x.id),
    });
    onClose();
  };

  const cancel = () => {
    dispatch({ type: "peekCancel" });
    onClose();
  };

  return (
    <Dialog title={PEEK_TITLES[peek.mode]} onClose={cancel} wide>
      <div className="stack">
        <div className="pt-peek-row">
          {items.map(({ id, where }) => {
            const card = game.state.cards[id];
            const url = faceUrl(card);
            const group = items.filter((x) => x.where === where);
            const place = group.findIndex((x) => x.id === id);
            const movable = peek.mode !== "look" && where !== "graveyard";
            return (
              <div key={id} className="pt-peek-card" onMouseMove={hover.follow(url)} onMouseLeave={hover.hide}>
                <span className="pt-zone-pic">
                  {url ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={url} alt="" loading="lazy" />
                  ) : (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img className="pt-zone-back" src="/card-back.jpg" alt="" loading="lazy" />
                  )}
                </span>
                <span className="pt-zone-name">{card.faceDown ? `${card.def.name} (face down)` : card.def.name}</span>
                {peek.mode !== "look" && (
                  <div className="pt-zone-btns">
                    {choices.map((choice) => (
                      <button
                        key={choice.where}
                        className={"btn" + (where === choice.where ? " on" : "")}
                        aria-pressed={where === choice.where}
                        onClick={() => setWhere(id, choice.where)}
                      >
                        {choice.label}
                      </button>
                    ))}
                  </div>
                )}
                {movable && (
                  <div className="pt-peek-arrows">
                    <button className="btn" disabled={place <= 0} title="Earlier" onClick={() => nudge(id, -1)}>
                      ←
                    </button>
                    <button
                      className="btn"
                      disabled={place >= group.length - 1}
                      title="Later"
                      onClick={() => nudge(id, 1)}
                    >
                      →
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
        <p className="muted small">
          {peek.mode === "look"
            ? "They stay on top of the library in this order."
            : "Left to right is the order they go back in."}
        </p>
        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={cancel}>
            Cancel
          </button>
          <button className="btn primary" onClick={done}>
            Done
          </button>
        </div>
      </div>
      {hover.picture}
    </Dialog>
  );
}

/** The counters on one battlefield card, plus a new kind of counter to add. */
function CounterDialog({
  id,
  game,
  dispatch,
  onClose,
}: {
  id: string;
  game: Game;
  dispatch: (a: Action) => void;
  onClose: () => void;
}) {
  const [kind, setKind] = useState("");
  const [amount, setAmount] = useState(1);
  const card = game.state.cards[id];

  const add = () => {
    const name = kind.trim();
    if (name === "") return;
    dispatch({ type: "counter", id, kind: name, delta: whole(amount, 1, 99) });
    setKind("");
    setAmount(1);
  };

  return (
    <Dialog title={card ? `Counters on ${card.def.name}` : "Counters"} onClose={onClose}>
      <div className="stack">
        {!card ? (
          <p className="muted">That card is no longer on the battlefield.</p>
        ) : (
          <>
            {card.counters.length === 0 ? (
              <p className="muted">No counters on it yet.</p>
            ) : (
              <div>
                {card.counters.map((counter) => (
                  <div key={counter.kind} className="pt-counter-row">
                    <span>{counter.kind}</span>
                    <span className="muted">{counter.amount}</span>
                    <button
                      className="pt-menu-mini"
                      title={`One ${counter.kind} counter less`}
                      onClick={() => dispatch({ type: "counter", id, kind: counter.kind, delta: -1 })}
                    >
                      −
                    </button>
                    <button
                      className="pt-menu-mini"
                      title={`One ${counter.kind} counter more`}
                      onClick={() => dispatch({ type: "counter", id, kind: counter.kind, delta: 1 })}
                    >
                      +
                    </button>
                  </div>
                ))}
              </div>
            )}
            <div className="inline">
              <input
                type="text"
                list="pt-counter-kinds"
                value={kind}
                placeholder="New counter, e.g. +1/+1"
                aria-label="Kind of counter"
                onChange={(e) => setKind(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    add();
                  }
                }}
              />
              <datalist id="pt-counter-kinds">
                {COUNTER_SUGGESTIONS.map((s) => (
                  <option key={s} value={s} />
                ))}
              </datalist>
              <input
                type="number"
                min={1}
                max={99}
                className="pt-counter-amount"
                value={amount}
                aria-label="How many"
                onChange={(e) => setAmount(Number(e.target.value))}
              />
              <button className="btn" disabled={kind.trim() === ""} onClick={add}>
                Add
              </button>
            </div>
          </>
        )}
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

/** Where a card is, as the card view says it. */
const PLACE_LABELS: Record<ZoneId, string> = {
  library: "In the library",
  hand: "In your hand",
  battlefield: "On the battlefield",
  graveyard: "In the graveyard",
  exile: "In exile",
  command: "In the command zone",
};

/** One card of the game: its picture, what it is doing there, its rules text and the rulings. */
function CardViewDialog({ id, game, onClose }: { id: string; game: Game; onClose: () => void }) {
  const card = game.state.cards[id];
  if (!card) return null;
  const zone = ZONE_IDS.find((z) => game.state.zones[z].includes(id)) ?? null;
  const facts = [
    zone ? PLACE_LABELS[zone] : null,
    card.tapped ? "tapped" : null,
    card.token ? "token" : null,
    ...card.counters
      .filter((counter) => counter.amount !== 0)
      .map((counter) => `${counter.kind} ×${counter.amount}`),
  ].filter((part): part is string => part !== null);

  return (
    <Dialog title={card.def.name} onClose={onClose}>
      <div className="stack">
        {card.def.custom ? (
          <div className="pt-custom-big">
            <CustomFace def={card.def} />
          </div>
        ) : (
          card.def.imageUrl && (
            <FlipPicture
              url={card.def.imageUrl}
              className="line-picture"
              typeLine={card.def.typeLine}
              startFlipped={card.flipped}
            />
          )
        )}
        {/* A made-up token has no Scryfall card behind it: the facts (and nothing to look up). */}
        {card.def.scryfallId === "" ? (
          <p className="muted small">{facts.join(" · ")}</p>
        ) : (
          <CardRulesTabs scryfallId={card.def.scryfallId}>
            <p className="muted small">{facts.join(" · ")}</p>
          </CardRulesTabs>
        )}
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

/** Where in the library a card should go: the top (0), the bottom, or a number of cards from the top. */
function LibraryPositionDialog({
  id,
  game,
  dispatch,
  onClose,
}: {
  id: string;
  game: Game;
  dispatch: (a: Action) => void;
  onClose: () => void;
}) {
  const [fromTop, setFromTop] = useState(0);
  const card = game.state.cards[id];

  const put = (position: "top" | "bottom" | number) => {
    dispatch({ type: "move", id, to: "library", position });
    onClose();
  };

  return (
    <Dialog title={card ? `Put ${card.def.name} into the library` : "Put into the library"} onClose={onClose}>
      <div className="stack">
        {!card ? (
          <p className="muted">That card is no longer in the game.</p>
        ) : (
          <>
            <label className="inline">
              <span className="muted">Cards from the top</span>
              <input
                type="number"
                min={0}
                max={Math.max(0, game.state.zones.library.length)}
                value={fromTop}
                autoFocus
                onChange={(e) => setFromTop(Number(e.target.value))}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    put(whole(fromTop, 0, Math.max(0, game.state.zones.library.length)));
                  }
                }}
              />
            </label>
            <p className="muted small">0 is the top of the library.</p>
            <div className="dialog-actions">
              <span className="spacer" />
              <button className="btn" onClick={() => put("top")}>
                Top
              </button>
              <button className="btn" onClick={() => put("bottom")}>
                Bottom
              </button>
              <button
                className="btn primary"
                onClick={() => put(whole(fromTop, 0, Math.max(0, game.state.zones.library.length)))}
              >
                Put it there
              </button>
            </div>
          </>
        )}
        {!card && (
          <div className="dialog-actions">
            <span className="spacer" />
            <button className="btn" onClick={onClose}>
              Close
            </button>
          </div>
        )}
      </div>
    </Dialog>
  );
}

interface TokenResult {
  id: string;
  name: string;
  typeLine: string;
  power: string | null;
  toughness: string | null;
  imageUrl: string | null;
}

/** A found token's label: its name, with power and toughness when Scryfall knows them. */
function tokenResultLabel(token: TokenResult): string {
  return token.power !== null && token.toughness !== null
    ? `${token.name} ${token.power}/${token.toughness}`
    : token.name;
}

/** The five colours a made-up token can have, in the rules' order. */
const TOKEN_COLOURS: { letter: string; label: string }[] = [
  { letter: "W", label: "White" },
  { letter: "U", label: "Blue" },
  { letter: "B", label: "Black" },
  { letter: "R", label: "Red" },
  { letter: "G", label: "Green" },
];

/** The deck's own tokens and a Scryfall token search, each creating the chosen token in the chosen quantity; plus a
 *  token the player makes up, drawn by the board itself. "Custom token…" puts that last section first. */
function TokenDialog({
  deckTokens,
  dispatch,
  onClose,
  custom,
}: {
  deckTokens: DeckToken[];
  dispatch: (a: Action) => void;
  onClose: () => void;
  custom?: boolean;
}) {
  const [quantity, setQuantity] = useState(1);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<TokenResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hoverOn] = useHoverSetting();
  const hover = useHoverCard(hoverOn);
  const [ownName, setOwnName] = useState("");
  const [ownType, setOwnType] = useState("Token Creature");
  const [ownColours, setOwnColours] = useState<string[]>([]);
  const [ownPower, setOwnPower] = useState("");
  const [ownToughness, setOwnToughness] = useState("");
  const [ownText, setOwnText] = useState("");
  const nameRef = useRef<HTMLInputElement>(null);

  // The Dialog moves the focus to itself as it opens, so "Custom token…" gives it to the Name field a moment later.
  useEffect(() => {
    if (!custom) return;
    const timer = window.setTimeout(() => nameRef.current?.focus(), 0);
    return () => window.clearTimeout(timer);
  }, [custom]);

  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) {
      setResults(null);
      setSearching(false);
      setError(null);
      return;
    }
    let cancelled = false;
    setSearching(true);
    setError(null);
    const timer = window.setTimeout(async () => {
      const answer = await api<{ results?: TokenResult[] }>(`/api/cards/tokensearch?q=${encodeURIComponent(q)}`);
      if (cancelled) return;
      setSearching(false);
      if (!answer.ok) {
        setError(answer.data.error ?? "Could not search for tokens.");
        setResults([]);
        return;
      }
      setResults(answer.data.results ?? []);
    }, 400);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [query]);

  const create = (def: CardDef) => {
    dispatch({ type: "token", def, count: whole(quantity, 1, 20) });
    onClose();
  };

  const createDeckToken = (token: DeckToken) =>
    create({
      scryfallId: token.id,
      name: token.label,
      imageUrl: scryfallImage(token.id),
      backImageUrl: null,
      typeLine: "Token",
      manaValue: 0,
      isLand: false,
      commander: false,
    });

  const createFound = (token: TokenResult) =>
    create({
      scryfallId: token.id,
      name: token.name,
      imageUrl: token.imageUrl ?? scryfallImage(token.id),
      backImageUrl: null,
      typeLine: token.typeLine || "Token",
      manaValue: 0,
      isLand: false,
      commander: false,
    });

  const toggleColour = (letter: string) =>
    setOwnColours((old) => (old.includes(letter) ? old.filter((c) => c !== letter) : [...old, letter]));

  /** A token the player makes up: no Scryfall card, so the board draws it from what is typed here. */
  const createOwn = () => {
    const name = ownName.trim();
    if (name === "") return;
    const typeLine = ownType.trim() === "" ? "Token Creature" : ownType.trim();
    create({
      scryfallId: "",
      name,
      imageUrl: null,
      backImageUrl: null,
      typeLine,
      manaValue: 0,
      isLand: /\bland\b/i.test(typeLine),
      commander: false,
      custom: {
        colors: TOKEN_COLOURS.map((colour) => colour.letter).filter((letter) => ownColours.includes(letter)),
        power: ownPower.trim(),
        toughness: ownToughness.trim(),
        text: ownText.trim(),
      },
    });
  };

  /** The "How many" field: at the top of the dialog, or in the "Make your own" form just above its Create button. */
  const quantityField = (
    <label className="inline">
      <span className="muted pt-nowrap">How many</span>
      <input
        type="number"
        min={1}
        max={20}
        className="pt-counter-amount"
        value={quantity}
        onChange={(e) => setQuantity(Number(e.target.value))}
      />
    </label>
  );

  /** The "Make your own" form: the name, the type line, the colours, power and toughness, and the rules text. */
  const ownSection = (
    <>
      <h3 className="pt-dialog-head">Make your own</h3>
      <label className="inline">
        <span className="muted">Name</span>
        <input
          ref={nameRef}
          type="text"
          value={ownName}
          maxLength={60}
          placeholder="e.g. Saproling"
          onChange={(e) => setOwnName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              createOwn();
            }
          }}
        />
      </label>
      <label className="inline">
        <span className="muted pt-nowrap">Type line</span>
        <input type="text" value={ownType} maxLength={80} onChange={(e) => setOwnType(e.target.value)} />
      </label>
      <div className="pt-custom-colours">
        {TOKEN_COLOURS.map(({ letter, label }) => (
          <label key={letter} className="pt-custom-colour">
            <input type="checkbox" checked={ownColours.includes(letter)} onChange={() => toggleColour(letter)} />
            <span>{label}</span>
          </label>
        ))}
      </div>
      <div className="inline">
        <label className="inline">
          <span className="muted">Power</span>
          <input
            type="text"
            className="pt-counter-amount"
            value={ownPower}
            maxLength={4}
            aria-label="Power"
            onChange={(e) => setOwnPower(e.target.value)}
          />
        </label>
        <label className="inline">
          <span className="muted">Toughness</span>
          <input
            type="text"
            className="pt-counter-amount"
            value={ownToughness}
            maxLength={4}
            aria-label="Toughness"
            onChange={(e) => setOwnToughness(e.target.value)}
          />
        </label>
      </div>
      <textarea
        value={ownText}
        maxLength={400}
        rows={3}
        aria-label="Rules text"
        placeholder="Rules text"
        onChange={(e) => setOwnText(e.target.value)}
      />
      <div className="inline">
        {custom && quantityField}
        <button className="btn primary" disabled={ownName.trim() === ""} onClick={createOwn}>
          Create
        </button>
      </div>
    </>
  );

  return (
    <Dialog title="Create a token" onClose={onClose} wide>
      <div className="stack">
        {custom ? ownSection : quantityField}

        <h3 className="pt-dialog-head">This deck&apos;s tokens</h3>
        {deckTokens.length === 0 ? (
          <p className="muted">This deck&apos;s cards make no tokens.</p>
        ) : (
          <div className="pt-token-grid">
            {deckTokens.map((token) => (
              <button
                key={token.key}
                type="button"
                className="pt-token-pick"
                title={`Create ${whole(quantity, 1, 20)} ${token.label}`}
                onClick={() => createDeckToken(token)}
                onMouseMove={hover.follow(scryfallImage(token.id))}
                onMouseLeave={hover.hide}
              >
                <span className="pt-zone-pic">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={scryfallImage(token.id, "small")} alt="" loading="lazy" />
                </span>
                <span className="pt-zone-name">{token.label}</span>
              </button>
            ))}
          </div>
        )}

        <h3 className="pt-dialog-head">Any token</h3>
        <input
          type="search"
          value={query}
          placeholder="Search tokens by name"
          aria-label="Search tokens by name"
          onChange={(e) => setQuery(e.target.value)}
        />
        {query.trim().length < 2 ? (
          <p className="muted small">Type at least two letters.</p>
        ) : error ? (
          <div className="bar error">{error}</div>
        ) : searching ? (
          <p className="muted small">Searching…</p>
        ) : results && results.length === 0 ? (
          <p className="muted small">No tokens found.</p>
        ) : results ? (
          <div className="pt-token-results">
            <div className="pt-token-grid">
              {results.map((token) => (
                <button
                  key={token.id}
                  type="button"
                  className="pt-token-pick"
                  title={`Create ${whole(quantity, 1, 20)} ${tokenResultLabel(token)}`}
                  onClick={() => createFound(token)}
                  onMouseMove={hover.follow(token.imageUrl ?? scryfallImage(token.id))}
                  onMouseLeave={hover.hide}
                >
                  <span className="pt-zone-pic">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={scryfallImage(token.id, "small")} alt="" loading="lazy" />
                  </span>
                  <span className="pt-zone-name">{tokenResultLabel(token)}</span>
                  <span className="muted small">{token.typeLine}</span>
                </button>
              ))}
            </div>
          </div>
        ) : null}

        {!custom && ownSection}

        <div className="dialog-actions">
          <span className="spacer" />
          <button className="btn" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
      {hover.picture}
    </Dialog>
  );
}

interface DiceRoll {
  key: number;
  label: string;
  result: string;
}

/** A fair die roll: 1 to [sides], with crypto.getRandomValues (rejecting the values that would favour a face). */
function rollDie(sides: number): number {
  const range = 0x100000000;
  const limit = range - (range % sides);
  const buffer = new Uint32Array(1);
  let value = 0;
  do {
    crypto.getRandomValues(buffer);
    value = buffer[0];
  } while (value >= limit);
  return (value % sides) + 1;
}

function DiceDialog({ onClose }: { onClose: () => void }) {
  const [rolls, setRolls] = useState<DiceRoll[]>([]);
  const [dice, setDice] = useState(1);
  const nextKey = useRef(1);

  const add = (label: string, result: string) =>
    setRolls((old) => [{ key: nextKey.current++, label, result }, ...old].slice(0, 10));

  const roll = (sides: number) => add(`d${sides}`, String(rollDie(sides)));
  const coin = () => add("Coin", rollDie(2) === 1 ? "Heads" : "Tails");
  const many = () => {
    const n = whole(dice, 1, 20);
    const values = Array.from({ length: n }, () => rollDie(6));
    const total = values.reduce((sum, value) => sum + value, 0);
    add(`${n}d6`, n === 1 ? String(total) : `${values.join(" + ")} = ${total}`);
  };

  const latest = rolls[0];

  return (
    <Dialog title="Dice and coin" onClose={onClose}>
      <div className="stack">
        <div className="pt-dice-buttons">
          <button className="btn" onClick={coin}>
            Coin
          </button>
          {[4, 6, 8, 10, 12, 20].map((sides) => (
            <button key={sides} className="btn" onClick={() => roll(sides)}>
              d{sides}
            </button>
          ))}
        </div>
        <div className="pt-dice-buttons">
          <button className="btn" onClick={many}>
            Roll {whole(dice, 1, 20)} d6
          </button>
          <input
            type="number"
            min={1}
            max={20}
            className="pt-counter-amount"
            value={dice}
            aria-label="How many dice"
            onChange={(e) => setDice(Number(e.target.value))}
          />
        </div>
        <div className="pt-dice-big" aria-live="polite">
          <span className="pt-dice-value">{latest ? latest.result : "—"}</span>
          <span className="muted small">{latest ? latest.label : "Roll something"}</span>
        </div>
        {rolls.length > 0 && (
          <>
            <h3 className="pt-dialog-head">Recent rolls</h3>
            <ul className="pt-dice-history">
              {rolls.map((entry) => (
                <li key={entry.key}>
                  <span className="muted">{entry.label}</span>
                  <span>{entry.result}</span>
                </li>
              ))}
            </ul>
          </>
        )}
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

/** The game log, newest first, gathered under the turn it happened in. */
function LogDialog({ game, onClose }: { game: Game; onClose: () => void }) {
  const log = game.state.log;
  const groups: { turn: number; lines: string[] }[] = [];
  for (let i = log.length - 1; i >= 0; i -= 1) {
    const entry = log[i];
    const last = groups[groups.length - 1];
    if (last && last.turn === entry.turn) last.lines.push(entry.text);
    else groups.push({ turn: entry.turn, lines: [entry.text] });
  }

  return (
    <Dialog title="Game log" onClose={onClose}>
      <div className="pt-log">
        {log.length === 0 ? (
          <p className="muted">Nothing has happened yet.</p>
        ) : (
          groups.map((group) => (
            <section key={group.turn}>
              <h3 className="pt-log-turn">{group.turn === 0 ? "Opening hand" : `Turn ${group.turn}`}</h3>
              <ul className="plain-list">
                {group.lines.map((line, i) => (
                  <li key={i} className="small">
                    {line}
                  </li>
                ))}
              </ul>
            </section>
          ))
        )}
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

/** The odds: lands in the opening hand, one card by a turn, and the next draw. */
function OddsDialog({
  game,
  cards,
  onClose,
}: {
  game: Game;
  cards: { def: CardDef; quantity: number }[];
  onClose: () => void;
}) {
  const [pick, setPick] = useState(0);
  const [turn, setTurn] = useState(3);
  const state = game.state;

  // The deck as dealt: everything except the commanders (the command zone).
  const deck = cards.filter((entry) => !entry.def.commander && entry.quantity > 0);
  const size = deck.reduce((sum, entry) => sum + entry.quantity, 0);
  const lands = deck.reduce((sum, entry) => sum + (entry.def.isLand ? entry.quantity : 0), 0);
  const opening = Array.from({ length: 8 }, (_, k) => ({ k, chance: landOdds(size, lands, 7, k) }));

  const chosen = deck[Math.min(pick, Math.max(0, deck.length - 1))];
  const shownTurn = whole(turn, 1, 15);
  const cardsSeen = 7 + shownTurn - (state.drawOnFirstTurn ? 0 : 1);
  const byTurn = chosen ? chanceByTurn(size, chosen.quantity, cardsSeen) : 0;

  const library = state.zones.library;
  const libraryLands = library.filter((id) => state.cards[id].def.isLand).length;
  const nextLand = library.length > 0 ? libraryLands / library.length : 0;

  return (
    <Dialog title="Odds" onClose={onClose}>
      <div className="stack">
        <h3 className="pt-dialog-head">Opening hand</h3>
        <p className="muted small">
          {size} cards, {lands} of them lands.
        </p>
        <table className="odds-table pt-odds-table">
          <thead>
            <tr>
              <th>Lands</th>
              <th>Chance</th>
              <th aria-label="Bar" />
            </tr>
          </thead>
          <tbody>
            {opening.map(({ k, chance }) => (
              <tr key={k} className={k >= 2 && k <= 4 ? "pt-odds-good" : undefined}>
                <th>{k}</th>
                <td>{percent(chance)}</td>
                <td className="pt-odds-bar">
                  <span className="pt-odds-track">
                    <span className="pt-odds-fill" style={{ width: percent(chance) }} />
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <h3 className="pt-dialog-head">Draw a card by a turn</h3>
        {deck.length === 0 ? (
          <p className="muted">There are no cards in the deck.</p>
        ) : (
          <>
            <div className="inline">
              <select
                value={pick}
                aria-label="Card"
                onChange={(e) => setPick(Number(e.target.value))}
              >
                {deck.map((entry, index) => (
                  <option key={index} value={index}>
                    {entry.def.name} ×{entry.quantity}
                  </option>
                ))}
              </select>
              <label className="inline">
                <span className="muted">Turn</span>
                <input
                  type="number"
                  min={1}
                  max={15}
                  className="pt-counter-amount"
                  value={turn}
                  onChange={(e) => setTurn(Number(e.target.value))}
                />
              </label>
            </div>
            <p className="small">
              Chance to have <strong>{chosen?.def.name}</strong> by turn {shownTurn} (on the{" "}
              {state.drawOnFirstTurn ? "draw" : "play"}): {percent(byTurn)}.
            </p>
            <p className="muted small">
              {cardsSeen} cards seen by then ({chosen ? `${chosen.quantity} cop${chosen.quantity === 1 ? "y" : "ies"}` : "no copies"}).
            </p>
          </>
        )}

        <h3 className="pt-dialog-head">Now</h3>
        {library.length === 0 ? (
          <p className="muted">The library is empty.</p>
        ) : (
          <>
            <p className="small">
              {library.length} card{library.length === 1 ? "" : "s"} left in the library, {libraryLands} of them land
              {libraryLands === 1 ? "" : "s"}.
            </p>
            <p className="small">Next draw is a land: {percent(nextLand)}.</p>
          </>
        )}

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
