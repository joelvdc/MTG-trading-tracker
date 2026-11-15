"use client";

import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
  type RefObject,
} from "react";
import { useHoverCard, useHoverSetting } from "@/components/HoverCard";
import { ZONE_IDS, type Action, type CardInstance, type Game, type ZoneId } from "@/lib/playtest";
import type { PlaytestDialog } from "./PlaytestDialogs";

/** Where a dragged card can land. */
type DropTarget = "battlefield" | "hand" | "library" | "graveyard" | "exile" | "command";

const DROP_TARGETS: DropTarget[] = ["battlefield", "hand", "library", "graveyard", "exile", "command"];

/** How far (px) the pointer moves before a press on a card becomes a drag. */
const START_PX = 6;
/** How long (ms) a finger rests on a card before its menu opens. */
const LONG_PRESS_MS = 500;

/** The counters the card menu offers with − and + (the engine's kinds). */
const COUNTER_KINDS: { kind: string; label: string }[] = [
  { kind: "+1/+1", label: "+1/+1" },
  { kind: "-1/-1", label: "−1/−1" },
  { kind: "loyalty", label: "Loyalty" },
  { kind: "charge", label: "Charge" },
];

type Menu = { kind: "card"; id: string; x: number; y: number } | { kind: "library"; x: number; y: number };

/** A press that may still become a drag, a long press or a click. Kept in a ref (it changes on every move). */
interface Press {
  id: string;
  zone: ZoneId;
  pointerId: number;
  pointerType: string;
  startX: number;
  startY: number;
  /** Where the pointer is on the card, so the ghost doesn't jump. */
  offsetX: number;
  offsetY: number;
  width: number;
  height: number;
  dragging: boolean;
  longPress: boolean;
  timer: number;
  el: HTMLElement;
}

interface DragView {
  id: string;
  x: number;
  y: number;
  offsetX: number;
  offsetY: number;
  width: number;
  height: number;
}

/**
 * The playtester's playing field: the battlefield, the hand and the piles (library, graveyard, exile, command zone),
 * with dragging (mouse and touch), the card and library menus, tapping, flipping and counters. The page keeps the game
 * and dispatches what happens here; nothing in this file changes a game by itself.
 */
export function PlaytestBoard({
  game,
  dispatch,
  openDialog,
}: {
  game: Game;
  dispatch: (a: Action) => void;
  openDialog: (d: PlaytestDialog) => void;
}) {
  const state = game.state;
  const [hoverOn] = useHoverSetting();
  const hover = useHoverCard(hoverOn);
  const boardRef = useRef<HTMLDivElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const [menu, setMenu] = useState<Menu | null>(null);
  const [drag, setDrag] = useState<DragView | null>(null);
  const [dropAt, setDropAt] = useState<DropTarget | null>(null);
  const press = useRef<Press | null>(null);

  const zoneOf = (id: string): ZoneId | null => ZONE_IDS.find((z) => state.zones[z].includes(id)) ?? null;

  // A menu closes on a click anywhere else (the one that opens it comes first) or on Esc.
  useEffect(() => {
    if (!menu) return;
    const away = (e: Event) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenu(null);
    };
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMenu(null);
    };
    document.addEventListener("pointerdown", away, true);
    document.addEventListener("keydown", key);
    return () => {
      document.removeEventListener("pointerdown", away, true);
      document.removeEventListener("keydown", key);
    };
  }, [menu]);

  useEffect(() => () => window.clearTimeout(press.current?.timer), []);

  const openMenuAt = (id: string, x: number, y: number) => {
    hover.hide();
    setMenu({ kind: "card", id, x, y });
  };

  const contextMenu = (id: string) => (e: ReactMouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    openMenuAt(id, e.clientX, e.clientY);
  };

  /** What is under the pointer, if it takes a dropped card. The ghost lets pointer events through. */
  function targetAt(x: number, y: number): DropTarget | null {
    const el = document.elementFromPoint(x, y)?.closest<HTMLElement>("[data-drop]");
    const found = el?.dataset.drop as DropTarget | undefined;
    return found && DROP_TARGETS.includes(found) ? found : null;
  }

  function onCardPointerDown(e: ReactPointerEvent<HTMLElement>, id: string) {
    if (e.button !== 0 || press.current) return;
    const zone = zoneOf(id);
    if (zone === null) return;
    const el = e.currentTarget;
    const rect = el.getBoundingClientRect();
    const p: Press = {
      id,
      zone,
      pointerId: e.pointerId,
      pointerType: e.pointerType,
      startX: e.clientX,
      startY: e.clientY,
      offsetX: e.clientX - rect.left,
      offsetY: e.clientY - rect.top,
      width: el.offsetWidth || rect.width,
      height: el.offsetHeight || rect.height,
      dragging: false,
      longPress: false,
      timer: 0,
      el,
    };
    try {
      el.setPointerCapture(e.pointerId);
    } catch {
      // The pointer is already gone: the press then ends on the next move.
    }
    if (e.pointerType === "touch") {
      const opensAt = { x: e.clientX, y: e.clientY };
      p.timer = window.setTimeout(() => {
        if (press.current !== p || p.dragging) return;
        p.longPress = true;
        setMenu({ kind: "card", id, x: opensAt.x, y: opensAt.y });
      }, LONG_PRESS_MS);
    }
    press.current = p;
  }

  function onCardPointerMove(e: ReactPointerEvent<HTMLElement>) {
    const p = press.current;
    if (!p || p.pointerId !== e.pointerId || p.longPress) return;
    if (!p.dragging) {
      if (Math.hypot(e.clientX - p.startX, e.clientY - p.startY) <= START_PX) return;
      p.dragging = true;
      window.clearTimeout(p.timer);
      hover.hide();
      setMenu(null);
    }
    setDrag({ id: p.id, x: e.clientX, y: e.clientY, offsetX: p.offsetX, offsetY: p.offsetY, width: p.width, height: p.height });
    setDropAt(targetAt(e.clientX, e.clientY));
  }

  function endPress(e: ReactPointerEvent<HTMLElement>) {
    const p = press.current;
    if (!p || p.pointerId !== e.pointerId) return null;
    press.current = null;
    window.clearTimeout(p.timer);
    try {
      if (p.el.hasPointerCapture(e.pointerId)) p.el.releasePointerCapture(e.pointerId);
    } catch {
      // Already released.
    }
    setDrag(null);
    setDropAt(null);
    return p;
  }

  function onCardPointerUp(e: ReactPointerEvent<HTMLElement>) {
    const p = endPress(e);
    if (!p || p.longPress) return;
    if (p.dragging) {
      const target = targetAt(e.clientX, e.clientY);
      if (target) dropCard(p, target, e);
      return;
    }
    clickCard(p, e);
  }

  function onCardPointerCancel(e: ReactPointerEvent<HTMLElement>) {
    endPress(e);
  }

  /** A click (no drag, no long press) on a card, which means different things in each zone. */
  function clickCard(p: Press, e: ReactPointerEvent<HTMLElement>) {
    if (p.zone === "battlefield") {
      // With a mouse a tap toggles; with a finger it opens the card's menu (a tap is too easy to miss otherwise).
      if (p.pointerType === "touch") openMenuAt(p.id, e.clientX, e.clientY);
      else dispatch({ type: "tap", id: p.id });
      return;
    }
    if (p.zone === "hand") {
      if (state.phase === "bottom") {
        dispatch({ type: "bottom", id: p.id });
        return;
      }
      openMenuAt(p.id, e.clientX, e.clientY);
      return;
    }
    openMenuAt(p.id, e.clientX, e.clientY);
  }

  /** Puts the dragged card where it was let go. */
  function dropCard(p: Press, target: DropTarget, e: ReactPointerEvent<HTMLElement>) {
    if (target === "battlefield") {
      const board = boardRef.current;
      if (!board) return;
      const rect = board.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      // The card lands with its top left corner where it was held, kept inside the board.
      const left = e.clientX - p.offsetX - rect.left;
      const top = e.clientY - p.offsetY - rect.top;
      const maxX = Math.max(0, rect.width - p.width);
      const maxY = Math.max(0, rect.height - p.height);
      const x = (Math.max(0, Math.min(maxX, left)) / rect.width) * 100;
      const y = (Math.max(0, Math.min(maxY, top)) / rect.height) * 100;
      if (p.zone === "battlefield") dispatch({ type: "position", id: p.id, x, y });
      else dispatch({ type: "move", id: p.id, to: "battlefield", x, y });
      return;
    }
    if (target === "library") {
      dispatch({ type: "move", id: p.id, to: "library", position: e.shiftKey ? "bottom" : "top" });
      return;
    }
    if (p.zone === target) return;
    dispatch({ type: "move", id: p.id, to: target });
  }

  /** Plays a hand card straight to the battlefield (a double-click). */
  function playFromHand(id: string) {
    setMenu(null);
    dispatch({ type: "move", id, to: "battlefield" });
  }

  const hand = state.zones.hand;
  const lands = hand.filter((id) => state.cards[id].def.isLand).length;
  // Cards overlap a little when the hand gets long, so a full hand still fits on one row.
  const overlap = hand.length > 6 ? Math.min(52, (hand.length - 6) * 8) : 0;
  const ghost = drag ? state.cards[drag.id] : null;
  const libraryTopId = state.zones.library[0];
  const libraryTop = libraryTopId ? state.cards[libraryTopId] : null;
  const graveyardTop = state.zones.graveyard.length > 0 ? state.cards[state.zones.graveyard[state.zones.graveyard.length - 1]] : null;
  const exileTop = state.zones.exile.length > 0 ? state.cards[state.zones.exile[state.zones.exile.length - 1]] : null;

  /** The picture to show on hover: the face that is up, and nothing for a face-down card. */
  const faceUrl = (card: CardInstance | null): string | null => {
    if (!card || card.faceDown) return null;
    return card.flipped && card.def.backImageUrl ? card.def.backImageUrl : card.def.imageUrl;
  };

  const dropClass = (target: DropTarget) => (dropAt === target ? " drop-on" : "");

  /** A card wherever it is on the board, in the hand or in the command zone. */
  const cardBox = (card: CardInstance, extra: string) => (
    <div
      className={"pt-card" + extra + (drag?.id === card.id ? " dragging" : "")}
      role="button"
      tabIndex={0}
      aria-label={card.def.name}
      onPointerDown={(e) => onCardPointerDown(e, card.id)}
      onPointerMove={onCardPointerMove}
      onPointerUp={onCardPointerUp}
      onPointerCancel={onCardPointerCancel}
      onContextMenu={contextMenu(card.id)}
      onMouseMove={hover.follow(faceUrl(card))}
      onMouseLeave={hover.hide}
      onKeyDown={(e) => {
        if (e.key !== "Enter" && e.key !== " ") return;
        e.preventDefault();
        // The keyboard opens the card's menu, as a finger does (with a mouse, a click toggles).
        const rect = e.currentTarget.getBoundingClientRect();
        openMenuAt(card.id, rect.right, rect.top);
      }}
    >
      <CardFace card={card} />
    </div>
  );

  return (
    <div className={drag ? "pt-body dragging" : "pt-body"}>
      <div className={"pt-board" + dropClass("battlefield")} ref={boardRef} data-drop="battlefield" onContextMenu={(e) => e.preventDefault()}>
        {state.zones.battlefield.map((id) => {
          const card = state.cards[id];
          return (
            <div key={id} className="pt-board-card" style={{ left: `${card.x}%`, top: `${card.y}%` }}>
              {cardBox(card, card.tapped ? " tapped" : "")}
            </div>
          );
        })}
        {state.phase === "mulligan" && (
          <div className="pt-banner">
            <span>
              Opening hand: {lands} land{lands === 1 ? "" : "s"}. Keep or mulligan?
            </span>
            {state.mulligans > 0 && <span className="muted small">Mulligans: {state.mulligans}</span>}
            <button className="btn small-btn primary" onClick={() => dispatch({ type: "keep" })}>
              Keep
            </button>
            <button className="btn small-btn" onClick={() => dispatch({ type: "mulligan" })}>
              Mulligan
            </button>
          </div>
        )}
        {state.phase === "bottom" && (
          <div className="pt-banner">
            <span>
              Choose {state.toBottom} card{state.toBottom === 1 ? "" : "s"} to put on the bottom
            </span>
          </div>
        )}
      </div>

      <aside className="pt-side">
        <div
          className={"pt-pile" + dropClass("library")}
          data-drop="library"
          role="button"
          tabIndex={0}
          title="Click for the library menu, double-click to draw"
          onClick={(e) => {
            hover.hide();
            setMenu({ kind: "library", x: e.clientX, y: e.clientY });
          }}
          onDoubleClick={() => {
            setMenu(null);
            dispatch({ type: "draw" });
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              setMenu({ kind: "library", x: 40, y: 80 });
            }
          }}
        >
          <div className="pt-pile-head">
            <span>Library</span>
            <span className="muted">{state.zones.library.length}</span>
          </div>
          <div
            className="pt-pile-card"
            onMouseMove={hover.follow(state.topRevealed ? faceUrl(libraryTop) : null)}
            onMouseLeave={hover.hide}
          >
            {libraryTop && state.topRevealed ? <CardFace card={libraryTop} /> : <CardBack />}
          </div>
        </div>

        <div
          className={"pt-pile" + dropClass("graveyard")}
          data-drop="graveyard"
          role="button"
          tabIndex={0}
          title="See the graveyard"
          onClick={() => openDialog({ kind: "zone", zone: "graveyard" })}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              openDialog({ kind: "zone", zone: "graveyard" });
            }
          }}
        >
          <div className="pt-pile-head">
            <span>Graveyard</span>
            <span className="muted">{state.zones.graveyard.length}</span>
          </div>
          <div className="pt-pile-card" onMouseMove={hover.follow(faceUrl(graveyardTop))} onMouseLeave={hover.hide}>
            {graveyardTop ? <CardFace card={graveyardTop} /> : <span className="pt-pile-empty" />}
          </div>
        </div>

        <div
          className={"pt-pile" + dropClass("exile")}
          data-drop="exile"
          role="button"
          tabIndex={0}
          title="See what is exiled"
          onClick={() => openDialog({ kind: "zone", zone: "exile" })}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              openDialog({ kind: "zone", zone: "exile" });
            }
          }}
        >
          <div className="pt-pile-head">
            <span>Exile</span>
            <span className="muted">{state.zones.exile.length}</span>
          </div>
          <div className="pt-pile-card" onMouseMove={hover.follow(faceUrl(exileTop))} onMouseLeave={hover.hide}>
            {exileTop ? <CardFace card={exileTop} /> : <span className="pt-pile-empty" />}
          </div>
        </div>

        <div className={"pt-pile" + dropClass("command")} data-drop="command">
          <div className="pt-pile-head">
            <span>Command zone</span>
            <span className="muted">{state.zones.command.length}</span>
          </div>
          <div className="pt-command">
            {state.zones.command.map((id) => {
              const card = state.cards[id];
              return (
                <div key={id} className="pt-command-card">
                  {cardBox(card, "")}
                  <span className="pt-tax muted small">Tax +{state.player.commanderTax[id] ?? 0}</span>
                </div>
              );
            })}
          </div>
        </div>
      </aside>

      <div className={"pt-hand" + dropClass("hand")} data-drop="hand">
        {hand.length === 0 && <span className="pt-hand-empty muted small">Your hand is empty.</span>}
        {hand.map((id, i) => {
          const card = state.cards[id];
          return (
            <div
              key={id}
              className="pt-hand-card"
              style={i === 0 || overlap === 0 ? undefined : { marginLeft: -overlap }}
              onDoubleClick={() => playFromHand(id)}
            >
              {cardBox(card, "")}
            </div>
          );
        })}
      </div>

      {hover.picture}

      {drag && ghost && (
        <div
          className="pt-ghost"
          aria-hidden="true"
          style={{ left: drag.x - drag.offsetX, top: drag.y - drag.offsetY, width: drag.width, height: drag.height }}
        >
          <div className="pt-card">
            <CardFace card={ghost} />
          </div>
        </div>
      )}

      {menu && menu.kind === "library" && (
        <FloatMenu menuRef={menuRef} x={menu.x} y={menu.y} title="Library">
          <button
            onClick={() => {
              setMenu(null);
              dispatch({ type: "draw" });
            }}
          >
            Draw 1
          </button>
          <button
            onClick={() => {
              setMenu(null);
              openDialog({ kind: "number", purpose: "draw" });
            }}
          >
            Draw…
          </button>
          <button
            onClick={() => {
              setMenu(null);
              openDialog({ kind: "number", purpose: "look" });
            }}
          >
            Look at the top…
          </button>
          <button
            onClick={() => {
              setMenu(null);
              openDialog({ kind: "number", purpose: "scry" });
            }}
          >
            Scry…
          </button>
          <button
            onClick={() => {
              setMenu(null);
              openDialog({ kind: "number", purpose: "surveil" });
            }}
          >
            Surveil…
          </button>
          <button
            onClick={() => {
              setMenu(null);
              openDialog({ kind: "number", purpose: "mill" });
            }}
          >
            Mill…
          </button>
          <button
            onClick={() => {
              setMenu(null);
              openDialog({ kind: "number", purpose: "exile" });
            }}
          >
            Exile the top…
          </button>
          <button
            onClick={() => {
              setMenu(null);
              openDialog({ kind: "search" });
            }}
          >
            Search the library…
          </button>
          <span className="pt-menu-sep" />
          <button
            onClick={() => {
              setMenu(null);
              dispatch({ type: "shuffle" });
            }}
          >
            Shuffle
          </button>
          <button
            onClick={() => {
              setMenu(null);
              dispatch({ type: "revealTop", on: !state.topRevealed });
            }}
          >
            {state.topRevealed ? "Stop revealing the top card" : "Reveal the top card"}
          </button>
        </FloatMenu>
      )}

      {menu && menu.kind === "card" && state.cards[menu.id] && (
        <CardMenu
          menuRef={menuRef}
          x={menu.x}
          y={menu.y}
          card={state.cards[menu.id]}
          zone={zoneOf(menu.id)}
          tax={state.player.commanderTax[menu.id] ?? 0}
          close={() => setMenu(null)}
          dispatch={dispatch}
          openDialog={openDialog}
        />
      )}
    </div>
  );
}

/** The floating card menu: what a card can do depends on where it is. */
function CardMenu({
  menuRef,
  x,
  y,
  card,
  zone,
  tax,
  close,
  dispatch,
  openDialog,
}: {
  menuRef: RefObject<HTMLDivElement | null>;
  x: number;
  y: number;
  card: CardInstance;
  zone: ZoneId | null;
  tax: number;
  close: () => void;
  dispatch: (a: Action) => void;
  openDialog: (d: PlaytestDialog) => void;
}) {
  /** An item that closes the menu after it runs. */
  const item = (label: string, run: () => void) => (
    <button
      onClick={() => {
        close();
        run();
      }}
    >
      {label}
    </button>
  );
  const to = (target: ZoneId, label: string, position?: "top" | "bottom") =>
    item(label, () => dispatch({ type: "move", id: card.id, to: target, ...(position ? { position } : {}) }));

  return (
    <FloatMenu menuRef={menuRef} x={x} y={y} title={card.faceDown ? "Face-down card" : card.def.name}>
      {zone === "battlefield" && (
        <>
          {item(card.tapped ? "Untap" : "Tap", () => dispatch({ type: "tap", id: card.id }))}
          {card.def.backImageUrl && item("Flip", () => dispatch({ type: "flip", id: card.id }))}
          {item(card.faceDown ? "Turn face up" : "Turn face down", () => dispatch({ type: "faceDown", id: card.id }))}
          <span className="pt-menu-sep" />
          <span className="pt-menu-title">Counters</span>
          {COUNTER_KINDS.map(({ kind, label }) => (
            <span key={kind} className="pt-menu-row">
              <span>{label}</span>
              <button
                className="pt-menu-mini"
                title={`One ${label} counter less`}
                onClick={() => dispatch({ type: "counter", id: card.id, kind, delta: -1 })}
              >
                −
              </button>
              <button
                className="pt-menu-mini"
                title={`One ${label} counter more`}
                onClick={() => dispatch({ type: "counter", id: card.id, kind, delta: 1 })}
              >
                +
              </button>
            </span>
          ))}
          {item("Other…", () => openDialog({ kind: "counter", id: card.id }))}
          <span className="pt-menu-sep" />
          {item("Copy", () => dispatch({ type: "copy", id: card.id }))}
          <span className="pt-menu-title">Move to</span>
          {to("hand", "Hand")}
          {to("graveyard", "Graveyard")}
          {to("exile", "Exile")}
          {to("library", "Top of library", "top")}
          {to("library", "Bottom of library", "bottom")}
          {card.def.commander && to("command", "Command zone")}
          {item("Library position…", () => openDialog({ kind: "libraryPosition", id: card.id }))}
        </>
      )}

      {zone === "hand" && (
        <>
          {item("Play", () => dispatch({ type: "move", id: card.id, to: "battlefield" }))}
          {item("Play face down", () => {
            dispatch({ type: "move", id: card.id, to: "battlefield" });
            dispatch({ type: "faceDown", id: card.id });
          })}
          {item("Discard (graveyard)", () => dispatch({ type: "move", id: card.id, to: "graveyard" }))}
          {item("Exile", () => dispatch({ type: "move", id: card.id, to: "exile" }))}
          {to("library", "Top of library", "top")}
          {to("library", "Bottom of library", "bottom")}
          {card.def.commander && to("command", "Command zone")}
        </>
      )}

      {zone === "command" && (
        <>
          {item("Cast (to battlefield)", () => dispatch({ type: "move", id: card.id, to: "battlefield" }))}
          {to("hand", "Hand")}
          {to("graveyard", "Graveyard")}
          {to("exile", "Exile")}
          <span className="pt-menu-sep" />
          <span className="pt-menu-row">
            <span>Tax {tax}</span>
            <button className="pt-menu-mini" title="Two less commander tax" onClick={() => dispatch({ type: "tax", id: card.id, delta: -2 })}>
              −
            </button>
            <button className="pt-menu-mini" title="Two more commander tax" onClick={() => dispatch({ type: "tax", id: card.id, delta: 2 })}>
              +
            </button>
          </span>
        </>
      )}

      {zone !== "battlefield" && zone !== "hand" && zone !== "command" && (
        <>
          {to("battlefield", "To the battlefield")}
          {to("hand", "Hand")}
          {to("graveyard", "Graveyard")}
          {to("exile", "Exile")}
          {to("library", "Top of library", "top")}
          {to("library", "Bottom of library", "bottom")}
        </>
      )}
    </FloatMenu>
  );
}

/** A small menu that floats where the pointer was, kept inside the window. */
function FloatMenu({
  menuRef,
  x,
  y,
  title,
  children,
}: {
  menuRef: RefObject<HTMLDivElement | null>;
  x: number;
  y: number;
  title: string;
  children: ReactNode;
}) {
  // Once it is in the page (and again whenever its items change), measure it and move it so that every item can be
  // reached: shifted left and up if it would run off the window, or scrolling from the top if it is taller than the
  // window. This runs before the browser paints, so the menu never shows at the wrong place.
  useLayoutEffect(() => {
    const el = menuRef.current;
    if (!el) return;
    const margin = 8;
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    // Measure the menu as it would be at the pointer, without a height cap from an earlier fit.
    el.style.maxHeight = "";
    el.style.overflowY = "";
    el.style.left = `${x}px`;
    el.style.top = `${y}px`;
    const rect = el.getBoundingClientRect();
    const width = Math.min(rect.width, vw - margin * 2);
    const left = Math.max(margin, Math.min(x, vw - margin - width));

    if (rect.height > vh - margin * 2) {
      // Taller than the window: scroll it, from the top margin, so no item stays out of reach.
      el.style.maxHeight = `calc(100vh - ${margin * 2}px)`;
      el.style.overflowY = "auto";
      el.style.left = `${left}px`;
      el.style.top = `${margin}px`;
      return;
    }

    el.style.left = `${left}px`;
    el.style.top = `${Math.max(margin, Math.min(y, vh - margin - rect.height))}px`;
  }, [menuRef, x, y, children]);

  return (
    <div className="pt-menu" ref={menuRef} style={{ left: x, top: y }} role="menu" aria-label={title}>
      <span className="pt-menu-name">{title}</span>
      {children}
    </div>
  );
}

/** A card's picture, or its back when it is face down; with the counters and the token badge on top. */
function CardFace({ card }: { card: CardInstance }) {
  const url = card.faceDown ? null : card.flipped && card.def.backImageUrl ? card.def.backImageUrl : card.def.imageUrl;
  const badges = card.counters.filter((c) => c.amount !== 0);
  return (
    <>
      {url ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt={card.faceDown ? "Face-down card" : card.def.name} loading="lazy" draggable={false} />
      ) : (
        <CardBack />
      )}
      {(badges.length > 0 || card.token) && (
        <span className="pt-badges">
          {card.token && <span className="pt-badge">Token</span>}
          {badges.map((c) => (
            <span key={c.kind} className="pt-badge">
              {counterText(c.kind, c.amount)}
            </span>
          ))}
        </span>
      )}
    </>
  );
}

function counterText(kind: string, amount: number): string {
  if (kind === "loyalty") return `Loyalty ${amount}`;
  if (kind === "charge") return `Charge ${amount}`;
  return `${kind} ×${amount}`;
}

/** The card back: drawn with CSS, as there is no picture for it. */
function CardBack() {
  return (
    <span className="pt-back">
      <span className="pt-back-oval" aria-hidden="true" />
      <span className="pt-back-label">MTG Trader</span>
    </span>
  );
}
