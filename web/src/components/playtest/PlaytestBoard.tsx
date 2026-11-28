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
import { ZONE_IDS, currentPT, type Action, type CardDef, type CardInstance, type Game, type Tracker, type ZoneId } from "@/lib/playtest";
import { counterLabel } from "@/lib/playtestcounters";
import { useCornerPreview } from "./CornerPreview";
import type { PlaytestDialog } from "./PlaytestDialogs";
import { useQuickCounters } from "./useQuickCounters";

/** Where a dragged card can land. */
type DropTarget = "battlefield" | "hand" | "library" | "graveyard" | "exile" | "command";

const DROP_TARGETS: DropTarget[] = ["battlefield", "hand", "library", "graveyard", "exile", "command"];

/** How far (px) the pointer moves before a press on a card becomes a drag. */
const START_PX = 6;
/** How long (ms) a finger rests on a card before its menu opens. */
const LONG_PRESS_MS = 500;
/** Where the browser remembers whether dropped cards snap to the grid. */
const SNAP_KEY = "mtg.playtest.snap";

/** The power and toughness steps the menus offer, the same for both sides and for the pair: −3 to −1, then +1 to +6. */
const PT_STEPS = [-3, -2, -1, 1, 2, 3, 4, 5, 6];

/** A step of a power and toughness submenu as a label: "+2", "−3" (the proper minus sign, as the rest of the board has). */
function stepLabel(step: number): string {
  return step > 0 ? `+${step}` : `−${-step}`;
}

type Menu =
  | { kind: "card"; id: string; x: number; y: number }
  | { kind: "library"; x: number; y: number }
  | { kind: "pile"; zone: "graveyard" | "exile"; x: number; y: number }
  | { kind: "board"; x: number; y: number }
  | { kind: "group"; x: number; y: number }
  | { kind: "tracker"; id: string; x: number; y: number };

/** The zones whose pile shows one card on top: that card can be dragged out while playing. */
function isPileZone(zone: ZoneId | null): boolean {
  return zone === "library" || zone === "graveyard" || zone === "exile";
}

/** A selection box in progress: where the press started on the board, in the board's own pixels. */
interface BoxStart {
  x: number;
  y: number;
  pointerId: number;
  /** Ctrl, Meta or Shift: the box adds its cards to the selection instead of replacing it. */
  additive: boolean;
  /** The board's rectangle, which stays the same while the box is drawn. */
  rect: DOMRect;
}

/** A box being dragged on the board, in board pixels. */
interface SelectBox {
  left: number;
  top: number;
  width: number;
  height: number;
}

/** A board position in percent, kept on the board (as the engine's own clamp does). */
function clampPercent(n: number): number {
  return Math.min(100, Math.max(0, n));
}

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
  /** The other selected cards dragged along, measured when the drag starts. */
  others: DragOther[];
}

/** A selected card dragged along with the held one: where its top left corner is from the held card's (px). */
interface DragOther {
  id: string;
  dx: number;
  dy: number;
}

interface DragView {
  id: string;
  x: number;
  y: number;
  offsetX: number;
  offsetY: number;
  width: number;
  height: number;
  others: DragOther[];
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
  dialogOpen,
}: {
  game: Game;
  dispatch: (a: Action) => void;
  openDialog: (d: PlaytestDialog) => void;
  /** A dialog is on screen, so the board's own keys stay quiet. */
  dialogOpen: boolean;
}) {
  const state = game.state;
  const hover = useCornerPreview();
  const boardRef = useRef<HTMLDivElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const [menu, setMenu] = useState<Menu | null>(null);
  const [drag, setDrag] = useState<DragView | null>(null);
  const [dropAt, setDropAt] = useState<DropTarget | null>(null);
  const [snap, setSnap] = useState(true);
  // The battlefield cards that are selected (their ids), for acting on several at once.
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [box, setBox] = useState<SelectBox | null>(null);
  const boxStart = useRef<BoxStart | null>(null);
  const press = useRef<Press | null>(null);
  // A drag or long press on a pile's top card still ends with a click on the pile. This remembers such a click, so the
  // pile does not also open its menu or its list (the list would land on top of the menu a long press just opened).
  const justDragged = useRef(false);
  // When the last drag or long press on a pile card ended: two of them in a row are not the double-click that draws.
  const draggedAt = useRef(0);

  const zoneOf = (id: string): ZoneId | null => ZONE_IDS.find((z) => state.zones[z].includes(id)) ?? null;

  /** The selected cards that are still on the battlefield, in board order. */
  const selectedIds = (): string[] => state.zones.battlefield.filter((id) => selected.has(id));

  /** Sends several actions as one undo step. */
  const group = (actions: Action[]) => dispatch({ type: "group", actions });

  /** The same action for every selected card, as one group. */
  const eachSelected = (action: (id: string) => Action) => group(selectedIds().map(action));

  /** Taps (or untaps) the selected cards that have to change, as one group. */
  const setTapped = (tapped: boolean) => {
    const ids = selectedIds().filter((id) => state.cards[id].tapped !== tapped);
    if (ids.length > 0) group(ids.map((id) => ({ type: "tap", id })));
  };

  /** A click (or T) on the selection: tap the untapped cards, or untap them all when every one is tapped. */
  const tapSelection = () => {
    const ids = selectedIds();
    setTapped(ids.some((id) => !state.cards[id].tapped));
  };

  /** Moves every selected card to a zone as one group, then lets the selection go. */
  const moveSelected = (to: ZoneId, position?: "top" | "bottom") => {
    const ids = selectedIds();
    if (ids.length > 0) group(ids.map((id) => ({ type: "move", id, to, ...(position ? { position } : {}) })));
    setSelected(new Set());
  };

  // A card that leaves the battlefield (or the game) is no longer selected.
  useEffect(() => {
    setSelected((old) => {
      if (old.size === 0) return old;
      const onBoard = new Set(state.zones.battlefield);
      const kept = new Set([...old].filter((id) => onBoard.has(id)));
      return kept.size === old.size ? old : kept;
    });
  }, [state.zones.battlefield]);

  // Esc lets go of the selection, unless a menu is open (Esc closes that one instead).
  useEffect(() => {
    if (selected.size === 0 || menu) return;
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") setSelected(new Set());
    };
    document.addEventListener("keydown", key);
    return () => document.removeEventListener("keydown", key);
  }, [selected, menu]);

  // T taps or untaps the selection, as a click on it does (the page's T opens the token dialog when nothing is selected).
  useEffect(() => {
    if (selected.size === 0) return;
    const key = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT" || target.isContentEditable)) return;
      if (dialogOpen || state.peek || e.ctrlKey || e.metaKey || e.altKey || e.key.toLowerCase() !== "t") return;
      e.preventDefault();
      tapSelection();
    };
    document.addEventListener("keydown", key);
    return () => document.removeEventListener("keydown", key);
    // tapSelection reads the selected cards and their tapped state, both of which are in these dependencies.
  }, [selected, state, dialogOpen]);

  // The "Snap to grid" choice, remembered per browser; on unless switched off.
  useEffect(() => {
    try {
      if (localStorage.getItem(SNAP_KEY) === "off") setSnap(false);
    } catch {
      // Storage unavailable: stays on.
    }
  }, []);

  const changeSnap = (on: boolean) => {
    setSnap(on);
    try {
      localStorage.setItem(SNAP_KEY, on ? "on" : "off");
    } catch {
      // Not remembered; fine.
    }
  };

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
    // A right-click on a card of a bigger selection acts on the whole selection.
    if (selected.has(id) && selected.size > 1) {
      hover.hide();
      setMenu({ kind: "group", x: e.clientX, y: e.clientY });
    } else openMenuAt(id, e.clientX, e.clientY);
  };

  /** A right-click on the empty battlefield: the board's own menu (a card's menu stops the event first). */
  const boardMenu = (e: ReactMouseEvent) => {
    e.preventDefault();
    hover.hide();
    setMenu({ kind: "board", x: e.clientX, y: e.clientY });
  };

  /** A counter tile where the board's menu was opened (its corner at the pointer, kept on the board). */
  const addTracker = (clientX: number, clientY: number) => {
    const board = boardRef.current;
    if (!board) return;
    const rect = board.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    const x = clampPercent(((clientX - rect.left) / rect.width) * 100);
    const y = clampPercent(((clientY - rect.top) / rect.height) * 100);
    dispatch({ type: "trackerAdd", x: Math.min(x, 90), y: Math.min(y, 85) });
  };

  /** An item of one of the board's menus that closes the menu after it runs. */
  const menuItem = (label: string, run: () => void) => (
    <button
      onClick={() => {
        setMenu(null);
        run();
      }}
    >
      {label}
    </button>
  );

  /** What is under the pointer, if it takes a dropped card. The ghost lets pointer events through. */
  function targetAt(x: number, y: number): DropTarget | null {
    const el = document.elementFromPoint(x, y)?.closest<HTMLElement>("[data-drop]");
    const found = el?.dataset.drop as DropTarget | undefined;
    return found && DROP_TARGETS.includes(found) ? found : null;
  }

  function onCardPointerDown(e: ReactPointerEvent<HTMLElement>, id: string) {
    hover.hide();
    // A new press starts over: the click that follows it is a real one (see justDragged).
    justDragged.current = false;
    if (e.button !== 0 || press.current) return;
    const zone = zoneOf(id);
    if (zone === null) return;
    const el = e.currentTarget;
    // A tapped battlefield card is drawn turned 90°, so its own rectangle is the rotated box and a drop measured from it
    // would jump. Its unrotated wrapper (.pt-board-card) is the card's normal box: measure the press from that instead.
    const box = (zone === "battlefield" ? el.closest<HTMLElement>(".pt-board-card") : null) ?? el;
    const rect = box.getBoundingClientRect();
    const p: Press = {
      id,
      zone,
      pointerId: e.pointerId,
      pointerType: e.pointerType,
      startX: e.clientX,
      startY: e.clientY,
      offsetX: e.clientX - rect.left,
      offsetY: e.clientY - rect.top,
      width: box.offsetWidth || rect.width,
      height: box.offsetHeight || rect.height,
      dragging: false,
      longPress: false,
      timer: 0,
      el,
      others: [],
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
        // A long press on a card of a bigger selection opens the selection's menu, as a right-click does.
        if (selected.has(id) && selected.size > 1) setMenu({ kind: "group", x: opensAt.x, y: opensAt.y });
        // A pile's top card opens the pile's own menu, as a right-click on the pile does.
        else if (zone === "library") setMenu({ kind: "library", x: opensAt.x, y: opensAt.y });
        else if (zone === "graveyard" || zone === "exile") setMenu({ kind: "pile", zone, x: opensAt.x, y: opensAt.y });
        else setMenu({ kind: "card", id, x: opensAt.x, y: opensAt.y });
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
      // Dragging a card that is not part of the selection works on its own, so the selection goes.
      if (!selected.has(p.id) && selected.size > 0) setSelected(new Set());
      // A selection dragged on the battlefield: the other cards follow the pointer too, at their spacing on the board.
      else if (p.zone === "battlefield" && selected.size > 1) p.others = measureOthers(p);
    }
    setDrag({ id: p.id, x: e.clientX, y: e.clientY, offsetX: p.offsetX, offsetY: p.offsetY, width: p.width, height: p.height, others: p.others });
    setDropAt(targetAt(e.clientX, e.clientY));
  }

  /** Where each other selected battlefield card sits from the held card, measured on the board's unrotated wrappers. */
  function measureOthers(p: Press): DragOther[] {
    const board = boardRef.current;
    if (!board) return [];
    const heldLeft = p.startX - p.offsetX;
    const heldTop = p.startY - p.offsetY;
    const out: DragOther[] = [];
    for (const id of selected) {
      if (id === p.id || zoneOf(id) !== "battlefield") continue;
      const box = board.querySelector<HTMLElement>(`.pt-board-card[data-card-id="${CSS.escape(id)}"]`);
      if (!box) continue;
      const r = box.getBoundingClientRect();
      out.push({ id, dx: r.left - heldLeft, dy: r.top - heldTop });
    }
    return out;
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
    if (!p) return;
    if (p.longPress) {
      // The long press already opened the pile's menu: the click that follows must not open it again.
      if (isPileZone(p.zone)) {
        justDragged.current = true;
        draggedAt.current = Date.now();
      }
      return;
    }
    if (p.dragging) {
      // Letting go of a pile card fires a click on the pile too; it must not open the pile's menu as well.
      if (isPileZone(p.zone)) {
        justDragged.current = true;
        draggedAt.current = Date.now();
      }
      const target = targetAt(e.clientX, e.clientY);
      if (target) dropCard(p, target, e);
      return;
    }
    clickCard(p, e);
  }

  function onCardPointerCancel(e: ReactPointerEvent<HTMLElement>) {
    endPress(e);
  }

  /** A press on the empty battlefield starts a selection box (a finger works too). */
  function onBoardPointerDown(e: ReactPointerEvent<HTMLDivElement>) {
    if (e.button !== 0 || state.phase !== "playing") return;
    // A press on a card is the card's own (tapping, dragging); only the bare board starts a box.
    if ((e.target as HTMLElement).closest(".pt-card, .pt-tracker")) return;
    const board = boardRef.current;
    if (!board) return;
    const rect = board.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    boxStart.current = {
      x,
      y,
      pointerId: e.pointerId,
      additive: e.ctrlKey || e.metaKey || e.shiftKey,
      rect,
    };
    setBox({ left: x, top: y, width: 0, height: 0 });
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      // The pointer is already gone: the box then ends on the next move.
    }
  }

  function onBoardPointerMove(e: ReactPointerEvent<HTMLDivElement>) {
    const start = boxStart.current;
    if (!start || start.pointerId !== e.pointerId) return;
    const x = e.clientX - start.rect.left;
    const y = e.clientY - start.rect.top;
    setBox({
      left: Math.min(start.x, x),
      top: Math.min(start.y, y),
      width: Math.abs(x - start.x),
      height: Math.abs(y - start.y),
    });
  }

  /** Ends the box: every card it touches becomes selected (a plain click on the board selects nothing). */
  function onBoardPointerUp(e: ReactPointerEvent<HTMLDivElement>) {
    const start = boxStart.current;
    if (!start || start.pointerId !== e.pointerId) return;
    boxStart.current = null;
    setBox(null);
    try {
      if (e.currentTarget.hasPointerCapture(e.pointerId)) e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {
      // Already released.
    }
    const x = e.clientX - start.rect.left;
    const y = e.clientY - start.rect.top;
    // The box in the window's coordinates, to compare with the cards' own rectangles.
    const left = start.rect.left + Math.min(start.x, x);
    const top = start.rect.top + Math.min(start.y, y);
    const width = Math.abs(x - start.x);
    const height = Math.abs(y - start.y);
    if (width < 4 && height < 4) {
      // A click on the empty board lets the selection go.
      setSelected(new Set());
      return;
    }
    const inside = new Set<string>();
    const board = boardRef.current;
    if (board) {
      for (const el of board.querySelectorAll<HTMLElement>("[data-card-id]")) {
        const r = el.getBoundingClientRect();
        if (r.right < left || r.left > left + width || r.bottom < top || r.top > top + height) continue;
        const id = el.dataset.cardId;
        if (id) inside.add(id);
      }
    }
    setSelected((old) => (start.additive ? new Set([...old, ...inside]) : inside));
  }

  function onBoardPointerCancel(e: ReactPointerEvent<HTMLDivElement>) {
    const start = boxStart.current;
    if (!start || start.pointerId !== e.pointerId) return;
    boxStart.current = null;
    setBox(null);
  }

  /** A click (no drag, no long press) on a card, which means different things in each zone. */
  function clickCard(p: Press, e: ReactPointerEvent<HTMLElement>) {
    if (p.zone === "battlefield") {
      // Ctrl, Cmd or Shift adds or removes one card from the selection instead of tapping it.
      if (e.ctrlKey || e.metaKey || e.shiftKey) {
        setSelected((old) => {
          const next = new Set(old);
          if (next.has(p.id)) next.delete(p.id);
          else next.add(p.id);
          return next;
        });
        return;
      }
      // With a mouse a tap toggles; with a finger it opens the card's menu (a tap is too easy to miss otherwise).
      if (p.pointerType === "touch") openMenuAt(p.id, e.clientX, e.clientY);
      // A selected card taps or untaps the whole selection instead, as one undo step.
      else if (selected.has(p.id)) tapSelection();
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
    // A pile's top card is only there to be dragged while playing: a plain click is the pile's own (its menu or its
    // list), and letting it open the card's menu too would hide the card's place in the pile.
    if (isPileZone(p.zone)) return;
    openMenuAt(p.id, e.clientX, e.clientY);
  }

  /** Puts the dragged card where it was let go. */
  function dropCard(p: Press, target: DropTarget, e: ReactPointerEvent<HTMLElement>) {
    // A card of a bigger selection moves (or is dropped) together with the rest of it.
    const together = p.zone === "battlefield" && selected.has(p.id) && selected.size > 1;
    if (target === "battlefield") {
      const board = boardRef.current;
      if (!board) return;
      const rect = board.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      // The card lands with its top left corner where it was held, kept inside the board.
      const left = e.clientX - p.offsetX - rect.left;
      const top = e.clientY - p.offsetY - rect.top;
      // With the grid on, the corner goes to the nearest dotted line first (the same step the board draws).
      let x0 = left;
      let y0 = top;
      if (snap) {
        const zoom = parseFloat(getComputedStyle(board).getPropertyValue("--pt-zoom")) || 1;
        const step = (window.matchMedia("(max-width: 760px)").matches ? 16 : 23) * zoom;
        if (step > 0) {
          x0 = Math.round(left / step) * step;
          y0 = Math.round(top / step) * step;
        }
      }
      const maxX = Math.max(0, rect.width - p.width);
      const maxY = Math.max(0, rect.height - p.height);
      const x = (Math.max(0, Math.min(maxX, x0)) / rect.width) * 100;
      const y = (Math.max(0, Math.min(maxY, y0)) / rect.height) * 100;
      if (p.zone === "battlefield" && together) {
        // The held card lands where it would alone; the others move by the same amount of the board.
        const from = state.cards[p.id];
        const dx = x - from.x;
        const dy = y - from.y;
        eachSelected((id) => {
          const card = state.cards[id];
          return { type: "position", id, x: clampPercent(card.x + dx), y: clampPercent(card.y + dy) };
        });
        return;
      }
      if (p.zone === "battlefield") dispatch({ type: "position", id: p.id, x, y });
      else dispatch({ type: "move", id: p.id, to: "battlefield", x, y });
      return;
    }
    // Letting a card go back on its own pile changes nothing (a library card on the library pile, say).
    if (p.zone === target) return;
    if (target === "library") {
      const position = e.shiftKey ? "bottom" : "top";
      if (together) moveSelected("library", position);
      else dispatch({ type: "move", id: p.id, to: "library", position });
      return;
    }
    if (together) moveSelected(target);
    else dispatch({ type: "move", id: p.id, to: target });
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
  // A card dragged out of the library stays hidden while the top card is not revealed (it lands face up all the same).
  const ghostHidden = drag !== null && zoneOf(drag.id) === "library" && !state.topRevealed;
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

  /** A press anywhere on a pile starts over, as one on a card does (so an old drag cannot swallow this click). */
  const freshPress = () => {
    justDragged.current = false;
  };

  /** The press-and-drag handlers of a pile's top card, which it has only while the game is being played. */
  const pileDrag = (card: CardInstance | null) =>
    state.phase === "playing" && card
      ? {
          onPointerDown: (e: ReactPointerEvent<HTMLElement>) => onCardPointerDown(e, card.id),
          onPointerMove: onCardPointerMove,
          onPointerUp: onCardPointerUp,
          onPointerCancel: onCardPointerCancel,
        }
      : {};

  /** A card wherever it is on the board, in the hand or in the command zone. */
  const cardBox = (card: CardInstance, extra: string) => (
    <div
      // The card held fades, and so do the other selected cards while the whole selection is dragged.
      className={
        "pt-card" +
        extra +
        (selected.has(card.id) ? " selected" : "") +
        (drag && (drag.id === card.id || selected.has(card.id)) ? " dragging" : "")
      }
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
      <div
        className={"pt-board" + (snap ? " snap" : "") + dropClass("battlefield")}
        ref={boardRef}
        data-drop="battlefield"
        onContextMenu={boardMenu}
        onPointerDown={onBoardPointerDown}
        onPointerMove={onBoardPointerMove}
        onPointerUp={onBoardPointerUp}
        onPointerCancel={onBoardPointerCancel}
      >
        {hover.picture}
        {state.zones.battlefield.map((id) => {
          const card = state.cards[id];
          return (
            <div key={id} className="pt-board-card" data-card-id={id} style={{ left: `${card.x}%`, top: `${card.y}%` }}>
              {cardBox(card, card.tapped ? " tapped" : "")}
            </div>
          );
        })}
        {state.trackers.map((tracker) => (
          <TrackerTile
            key={tracker.id}
            tracker={tracker}
            boardRef={boardRef}
            dispatch={dispatch}
            openMenu={(x, y) => {
              hover.hide();
              setMenu({ kind: "tracker", id: tracker.id, x, y });
            }}
          />
        ))}
        {box && (
          <div className="pt-select-box" style={{ left: box.left, top: box.top, width: box.width, height: box.height }} />
        )}
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
          title="Click for the library menu, double-click to draw, right-click for more, drag the top card"
          onPointerDown={freshPress}
          onClick={(e) => {
            if (justDragged.current) {
              justDragged.current = false;
              return;
            }
            hover.hide();
            setMenu({ kind: "library", x: e.clientX, y: e.clientY });
          }}
          onContextMenu={(e) => {
            e.preventDefault();
            hover.hide();
            setMenu({ kind: "library", x: e.clientX, y: e.clientY });
          }}
          onDoubleClick={() => {
            setMenu(null);
            // Two dragged drops in a row also count as a double-click on the pile: that is not a draw.
            if (Date.now() - draggedAt.current < 500) return;
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
            {...pileDrag(libraryTop)}
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
          title="Click to see the graveyard, right-click for more, drag the top card"
          onPointerDown={freshPress}
          onClick={() => {
            if (justDragged.current) {
              justDragged.current = false;
              return;
            }
            openDialog({ kind: "zone", zone: "graveyard" });
          }}
          onContextMenu={(e) => {
            e.preventDefault();
            hover.hide();
            setMenu({ kind: "pile", zone: "graveyard", x: e.clientX, y: e.clientY });
          }}
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
          <div
            className="pt-pile-card"
            {...pileDrag(graveyardTop)}
            onMouseMove={hover.follow(faceUrl(graveyardTop))}
            onMouseLeave={hover.hide}
          >
            {graveyardTop ? <CardFace card={graveyardTop} /> : <span className="pt-pile-empty" />}
          </div>
        </div>

        <div
          className={"pt-pile" + dropClass("exile")}
          data-drop="exile"
          role="button"
          tabIndex={0}
          title="Click to see what is exiled, right-click for more, drag the top card"
          onPointerDown={freshPress}
          onClick={() => {
            if (justDragged.current) {
              justDragged.current = false;
              return;
            }
            openDialog({ kind: "zone", zone: "exile" });
          }}
          onContextMenu={(e) => {
            e.preventDefault();
            hover.hide();
            setMenu({ kind: "pile", zone: "exile", x: e.clientX, y: e.clientY });
          }}
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
          <div
            className="pt-pile-card"
            {...pileDrag(exileTop)}
            onMouseMove={hover.follow(faceUrl(exileTop))}
            onMouseLeave={hover.hide}
          >
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
        <div className="pt-hand-head">
          <span>Hand</span>
          <span className="muted">{hand.length}</span>
        </div>
        <div className="pt-hand-cards">
          {hand.length === 0 && <span className="pt-hand-empty muted small">Your hand is empty.</span>}
          {hand.map((id, i) => {
            const card = state.cards[id];
            return (
              <div
                key={id}
                className="pt-hand-card"
                style={i === 0 || overlap === 0 ? undefined : { marginLeft: `calc(${-overlap}px * var(--pt-zoom, 1))` }}
                onDoubleClick={() => playFromHand(id)}
              >
                {cardBox(card, "")}
              </div>
            );
          })}
        </div>
      </div>

      {drag &&
        ghost &&
        drag.others.map((o) => {
          const card = state.cards[o.id];
          if (!card) return null;
          return (
            <div
              key={o.id}
              className="pt-ghost"
              aria-hidden="true"
              style={{ left: drag.x - drag.offsetX + o.dx, top: drag.y - drag.offsetY + o.dy, width: drag.width, height: drag.height }}
            >
              <div className={"pt-card" + (card.tapped ? " tapped" : "")}>
                <CardFace card={card} />
              </div>
            </div>
          );
        })}
      {drag && ghost && (
        <div
          className="pt-ghost"
          aria-hidden="true"
          style={{ left: drag.x - drag.offsetX, top: drag.y - drag.offsetY, width: drag.width, height: drag.height }}
        >
          <div className={"pt-card" + (ghost.tapped && zoneOf(ghost.id) === "battlefield" ? " tapped" : "")}>
            {ghostHidden ? <CardBack /> : <CardFace card={ghost} />}
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

      {menu && menu.kind === "board" && (
        <FloatMenu menuRef={menuRef} x={menu.x} y={menu.y} title="Battlefield">
          {menuItem("Untap all (U)", () => dispatch({ type: "untapAll" }))}
          {menuItem("Tap all", () => dispatch({ type: "tapAll" }))}
          {menuItem("Next turn: untap and draw (N)", () => dispatch({ type: "nextTurn" }))}
          {menuItem("Draw a card (D)", () => dispatch({ type: "draw" }))}
          {menuItem("Proliferate", () => dispatch({ type: "proliferate" }))}
          <span className="pt-menu-sep" />
          {menuItem("Create token… (T)", () => openDialog({ kind: "token" }))}
          {menuItem("Custom token…", () => openDialog({ kind: "token", custom: true }))}
          {menuItem("Add counter", () => addTracker(menu.x, menu.y))}
          {menuItem("Search the library…", () => openDialog({ kind: "search" }))}
          {menuItem("Shuffle library (S)", () => dispatch({ type: "shuffle" }))}
          <span className="pt-menu-sep" />
          {menuItem(`Snap to grid: ${snap ? "on" : "off"}`, () => changeSnap(!snap))}
          <span className="pt-menu-sep" />
          {menuItem("Undo (Z)", () => dispatch({ type: "undo" }))}
        </FloatMenu>
      )}

      {menu && menu.kind === "group" && (
        <FloatMenu menuRef={menuRef} x={menu.x} y={menu.y} title={`${selected.size} cards`}>
          {menuItem("Tap", () => setTapped(true))}
          {menuItem("Untap", () => setTapped(false))}
          {menuItem("+1/+1 counter", () => eachSelected((id) => ({ type: "counter", id, kind: "+1/+1", delta: 1 })))}
          {menuItem("Remove +1/+1 counter", () => eachSelected((id) => ({ type: "counter", id, kind: "+1/+1", delta: -1 })))}
          <span className="pt-menu-sep" />
          <PtMenus ids={selectedIds()} close={() => setMenu(null)} dispatch={dispatch} openDialog={openDialog} />
          <span className="pt-menu-sep" />
          {menuItem("Turn face down / face up", () => eachSelected((id) => ({ type: "faceDown", id })))}
          <span className="pt-menu-sep" />
          {menuItem("To graveyard", () => moveSelected("graveyard"))}
          {menuItem("To exile", () => moveSelected("exile"))}
          {menuItem("To hand", () => moveSelected("hand"))}
          {menuItem("To top of library", () => moveSelected("library", "top"))}
          {menuItem("To bottom of library", () => moveSelected("library", "bottom"))}
          {menuItem("To bottom of library, random order", () => {
            const ids = selectedIds();
            if (ids.length > 0) dispatch({ type: "bottomRandom", ids });
            setSelected(new Set());
          })}
          <span className="pt-menu-sep" />
          {menuItem("Clear selection", () => setSelected(new Set()))}
        </FloatMenu>
      )}

      {menu && menu.kind === "tracker" && state.trackers.some((t) => t.id === menu.id) && (
        <FloatMenu
          menuRef={menuRef}
          x={menu.x}
          y={menu.y}
          title={state.trackers.find((t) => t.id === menu.id)?.label ?? "Counter"}
        >
          {menuItem("Rename…", () => openDialog({ kind: "trackerRename", id: menu.id }))}
          {menuItem("Remove", () => dispatch({ type: "trackerRemove", id: menu.id }))}
        </FloatMenu>
      )}

      {menu && menu.kind === "pile" && (
        <PileMenu
          menuRef={menuRef}
          x={menu.x}
          y={menu.y}
          zone={menu.zone}
          topId={menu.zone === "graveyard" ? (graveyardTop?.id ?? null) : (exileTop?.id ?? null)}
          ids={state.zones[menu.zone]}
          playing={state.phase === "playing"}
          close={() => setMenu(null)}
          dispatch={dispatch}
          openDialog={openDialog}
        />
      )}

      {menu && menu.kind === "card" && state.cards[menu.id] && (
        <CardMenu
          menuRef={menuRef}
          x={menu.x}
          y={menu.y}
          card={state.cards[menu.id]}
          zone={zoneOf(menu.id)}
          tax={state.player.commanderTax[menu.id] ?? 0}
          // A card of a bigger selection takes the power and toughness items for the whole selection, as the board's
          // own menu does.
          ptIds={selected.has(menu.id) ? selectedIds() : [menu.id]}
          close={() => setMenu(null)}
          dispatch={dispatch}
          openDialog={openDialog}
        />
      )}
    </div>
  );
}

/**
 * The menu of the graveyard or exile pile (right-click, or a long press on a finger): the whole list, the top card and
 * every card in the pile. Each "All cards" item is one undo step, so the whole pile comes back at once.
 */
function PileMenu({
  menuRef,
  x,
  y,
  zone,
  topId,
  ids,
  playing,
  close,
  dispatch,
  openDialog,
}: {
  menuRef: RefObject<HTMLDivElement | null>;
  x: number;
  y: number;
  /** The pile this menu belongs to. */
  zone: "graveyard" | "exile";
  /** The card the pile shows (its newest), or null when the pile is empty. */
  topId: string | null;
  /** Every card of the pile, in the pile's own order. */
  ids: string[];
  /** Cards only move while the game is being played (not in the opening hand, say). */
  playing: boolean;
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

  /** One card of the pile to another zone. */
  const to = (id: string, target: ZoneId, label: string, position?: "top" | "bottom") =>
    item(label, () => dispatch({ type: "move", id, to: target, ...(position ? { position } : {}) }));

  /** The whole pile at once, as one step; the shuffle goes last, so the cards land in a fresh library. */
  const all = (label: string, target: ZoneId, position?: "bottom", shuffle = false) =>
    item(label, () => {
      const actions: Action[] = ids.map(
        (id): Action => ({
          type: "move",
          id,
          to: target,
          ...(position ? { position } : {}),
        }),
      );
      if (shuffle) actions.push({ type: "shuffle" });
      dispatch({ type: "group", actions });
    });

  return (
    <FloatMenu menuRef={menuRef} x={x} y={y} title={zone === "graveyard" ? "Graveyard" : "Exile"}>
      {item("See all…", () => openDialog({ kind: "zone", zone }))}
      {playing && topId && (
        <>
          <span className="pt-menu-sep" />
          <span className="pt-menu-title">Top card</span>
          {to(topId, "battlefield", "To the battlefield")}
          {to(topId, "hand", "To hand")}
          {to(topId, "library", "To top of library", "top")}
          {to(topId, "library", "To bottom of library", "bottom")}
          {zone === "graveyard" ? to(topId, "exile", "To exile") : to(topId, "graveyard", "To graveyard")}
          {zone === "graveyard" && item("Token copy", () => dispatch({ type: "copy", id: topId }))}
        </>
      )}
      {playing && ids.length > 0 && (
        <>
          <span className="pt-menu-sep" />
          <span className="pt-menu-title">All cards</span>
          {all("All to hand", "hand")}
          {zone === "graveyard" ? all("All to exile", "exile") : all("All to graveyard", "graveyard")}
          {all("Shuffle all into the library", "library", "bottom", true)}
        </>
      )}
    </FloatMenu>
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
  ptIds,
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
  /** The battlefield cards the power and toughness items act on: the whole selection when this card is part of it. */
  ptIds: string[];
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
  // The quick list, then any other kind this card already has, so whatever is on it can be changed from here.
  const [quick] = useQuickCounters();
  const kinds = [
    ...quick,
    ...card.counters.filter((c) => c.amount > 0 && !quick.includes(c.kind)).map((c) => c.kind),
  ];

  return (
    <FloatMenu menuRef={menuRef} x={x} y={y} title={card.faceDown ? "Face-down card" : card.def.name}>
      {!card.faceDown && (
        <>
          {item("View card", () => openDialog({ kind: "card", id: card.id }))}
          <span className="pt-menu-sep" />
        </>
      )}
      {zone === "battlefield" && (
        <>
          {item(card.tapped ? "Untap" : "Tap", () => dispatch({ type: "tap", id: card.id }))}
          {card.def.backImageUrl && item("Flip", () => dispatch({ type: "flip", id: card.id }))}
          {item(card.faceDown ? "Turn face up" : "Turn face down", () => dispatch({ type: "faceDown", id: card.id }))}
          <span className="pt-menu-sep" />
          <span className="pt-menu-title">Counters</span>
          {kinds.map((kind) => {
            const label = counterLabel(kind);
            // How many the card has now, between − and + (faint at 0); since 2026-11-25.
            const amount = card.counters.find((c) => c.kind === kind)?.amount ?? 0;
            return (
            <span key={kind} className="pt-menu-row">
              <span>{label}</span>
              <button
                className="pt-menu-mini"
                title={`One ${label} counter less`}
                onClick={() => dispatch({ type: "counter", id: card.id, kind, delta: -1 })}
              >
                −
              </button>
              <span className={"pt-menu-count" + (amount === 0 ? " none" : "")} aria-label={`${amount} ${label} counters`}>
                {amount}
              </span>
              <button
                className="pt-menu-mini"
                title={`One ${label} counter more`}
                onClick={() => dispatch({ type: "counter", id: card.id, kind, delta: 1 })}
              >
                +
              </button>
            </span>
            );
          })}
          {item("Other…", () => openDialog({ kind: "counter", id: card.id }))}
          {item("Edit list…", () => openDialog({ kind: "counterList" }))}
          <span className="pt-menu-sep" />
          <PtMenus ids={ptIds} close={close} dispatch={dispatch} openDialog={openDialog} />
          <span className="pt-menu-sep" />
          {item("Create token copy", () => dispatch({ type: "copy", id: card.id }))}
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
          {zone === "graveyard" && item("Create token copy", () => dispatch({ type: "copy", id: card.id }))}
        </>
      )}
    </FloatMenu>
  );
}

/**
 * The three power and toughness sections a card menu shows (Power, Toughness and +X/+X): the same items on every card
 * in [ids], as one undo step. A card's own menu and the selection's menu both show them.
 */
function PtMenus({
  ids,
  close,
  dispatch,
  openDialog,
}: {
  /** The battlefield cards to change: one card, or the whole selection. */
  ids: string[];
  /** Closes the menu the sections are in, after an item has run. */
  close: () => void;
  dispatch: (a: Action) => void;
  openDialog: (d: PlaytestDialog) => void;
}) {
  /** An item that closes the menu after it runs; [key] is for the ones a list makes. */
  const item = (label: string, run: () => void, key?: string) => (
    <button
      key={key}
      onClick={() => {
        close();
        run();
      }}
    >
      {label}
    </button>
  );

  /** The player's own change, on every card, as one undo step. */
  const change = (power: number, toughness: number) =>
    dispatch({ type: "group", actions: ids.map((id): Action => ({ type: "pt", id, power, toughness })) });

  /** Back to the printed value, on every card, as one undo step. */
  const clear = () => dispatch({ type: "group", actions: ids.map((id): Action => ({ type: "ptClear", id })) });

  /** One section: Clear, the fixed steps, then the item that asks for a number (−99 to 99). */
  const section = (which: "power" | "toughness" | "both") => (
    <>
      {item("Clear", clear)}
      {PT_STEPS.map((step) =>
        item(
          which === "both" ? `${stepLabel(step)}/${stepLabel(step)}` : stepLabel(step),
          () => (which === "power" ? change(step, 0) : which === "toughness" ? change(0, step) : change(step, step)),
          `pt${which}${step}`,
        ),
      )}
      {item(which === "both" ? "±X…" : "+X…", () => openDialog({ kind: "pt", ids, which }))}
    </>
  );

  return (
    <>
      <SubMenu label="Power ›">{section("power")}</SubMenu>
      <SubMenu label="Toughness ›">{section("toughness")}</SubMenu>
      <SubMenu label="+X/+X ›">{section("both")}</SubMenu>
    </>
  );
}

/**
 * One of a card menu's submenus ("Power ›"): its items unfold under the label while the pointer is over it, or stay open
 * after a click (a finger cannot hover).
 */
function SubMenu({ label, children }: { label: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <div className={"pt-sub" + (open ? " open" : "")}>
      <button className="pt-sub-toggle" aria-expanded={open} onClick={() => setOpen(!open)}>
        {label}
      </button>
      <div className="pt-sub-items">{children}</div>
    </div>
  );
}

/**
 * A counter tile on the battlefield: its name, its number, − and +. Dragged by its body; a click (or a right-click)
 * on it opens its menu.
 */
function TrackerTile({
  tracker,
  boardRef,
  dispatch,
  openMenu,
}: {
  tracker: Tracker;
  boardRef: RefObject<HTMLDivElement | null>;
  dispatch: (a: Action) => void;
  openMenu: (x: number, y: number) => void;
}) {
  // While dragged: how far it has moved from where the press started (in pixels), drawn before it is dropped.
  const [shift, setShift] = useState<{ dx: number; dy: number } | null>(null);
  const start = useRef<{ x: number; y: number; pointerId: number; moved: boolean } | null>(null);

  const onPointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (e.button !== 0 || (e.target as HTMLElement).closest("button")) return;
    e.stopPropagation();
    start.current = { x: e.clientX, y: e.clientY, pointerId: e.pointerId, moved: false };
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      // Gone already: the press simply ends.
    }
  };
  const onPointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    const s = start.current;
    if (!s || s.pointerId !== e.pointerId) return;
    const dx = e.clientX - s.x;
    const dy = e.clientY - s.y;
    if (!s.moved && Math.hypot(dx, dy) < 5) return;
    s.moved = true;
    setShift({ dx, dy });
  };
  const onPointerUp = (e: ReactPointerEvent<HTMLDivElement>) => {
    const s = start.current;
    if (!s || s.pointerId !== e.pointerId) return;
    start.current = null;
    setShift(null);
    if (!s.moved) {
      openMenu(e.clientX, e.clientY);
      return;
    }
    const board = boardRef.current;
    if (!board) return;
    const rect = board.getBoundingClientRect();
    const tile = e.currentTarget.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    // The tile's corner where it was let go (it is drawn shifted already), kept on the board.
    const left = Math.max(0, Math.min(rect.width - tile.width, tile.left - rect.left));
    const top = Math.max(0, Math.min(rect.height - tile.height, tile.top - rect.top));
    dispatch({ type: "trackerPosition", id: tracker.id, x: (left / rect.width) * 100, y: (top / rect.height) * 100 });
  };
  const onPointerCancel = () => {
    start.current = null;
    setShift(null);
  };
  const step = (delta: number) => (e: ReactMouseEvent) => {
    e.stopPropagation();
    dispatch({ type: "tracker", id: tracker.id, delta });
  };

  return (
    <div
      className={"pt-tracker" + (shift ? " dragging" : "")}
      style={{
        left: `${tracker.x}%`,
        top: `${tracker.y}%`,
        ...(shift ? { transform: `translate(${shift.dx}px, ${shift.dy}px)` } : {}),
      }}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      onContextMenu={(e) => {
        e.preventDefault();
        e.stopPropagation();
        openMenu(e.clientX, e.clientY);
      }}
    >
      <span className="pt-tracker-label">{tracker.label}</span>
      <span className="pt-tracker-row">
        <button className="pt-menu-mini" title="One less" onPointerDown={(e) => e.stopPropagation()} onClick={step(-1)}>
          −
        </button>
        <span className="pt-tracker-value">{tracker.value}</span>
        <button className="pt-menu-mini" title="One more" onPointerDown={(e) => e.stopPropagation()} onClick={step(1)}>
          +
        </button>
      </span>
    </div>
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
    let seen = -1;
    fit();
    // A list unfolding inside the menu (Power ›, on hover or a click) changes its size without new children here:
    // fit it again. Only a change of its content's height counts, so the fit's own height cap doesn't start another.
    if (typeof ResizeObserver === "undefined") return;
    const watch = new ResizeObserver(() => {
      if (el.scrollHeight !== seen) fit();
    });
    watch.observe(el);
    return () => watch.disconnect();

    function fit() {
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
        seen = el.scrollHeight;
        return;
      }

      el.style.left = `${left}px`;
      el.style.top = `${Math.max(margin, Math.min(y, vh - margin - rect.height))}px`;
      seen = el.scrollHeight;
    }
  }, [menuRef, x, y, children]);

  return (
    <div className="pt-menu" ref={menuRef} style={{ left: x, top: y }} role="menu" aria-label={title}>
      <span className="pt-menu-name">{title}</span>
      {children}
    </div>
  );
}

/** A card's picture, or its back when it is face down; with the counters, the token badge and its power and toughness on top. */
function CardFace({ card }: { card: CardInstance }) {
  const url = card.faceDown ? null : card.flipped && card.def.backImageUrl ? card.def.backImageUrl : card.def.imageUrl;
  const custom = !card.faceDown && card.def.custom ? card.def.custom : null;
  const badges = card.counters.filter((c) => c.amount !== 0);
  // The power and toughness a badge is needed for: the player's own change, or the counters that move it. Without either,
  // the printed value on the picture (or the made-up token's own box) says it already.
  const moves = card.pt !== undefined || card.counters.some((c) => (c.kind === "+1/+1" || c.kind === "-1/-1") && c.amount !== 0);
  const pt = moves ? currentPT(card) : null;
  return (
    <>
      {custom ? (
        <CustomFace def={card.def} />
      ) : url ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt={card.faceDown ? "Face-down card" : card.def.name} loading="lazy" draggable={false} />
      ) : (
        <CardBack />
      )}
      {(badges.length > 0 || card.token || pt) && (
        <span className="pt-badges">
          {card.token && <span className="pt-badge">Token</span>}
          {badges.map((c) => (
            <span key={c.kind} className="pt-badge">
              {counterText(c.kind, c.amount)}
            </span>
          ))}
          {pt && (
            // Green when either side ends up bigger than printed, red when smaller; both at once keeps the plain colour.
            <span className={"pt-badge pt-pt" + (pt.up === pt.down ? "" : pt.up ? " up" : " down")}>
              {pt.power}/{pt.toughness}
            </span>
          )}
        </span>
      )}
    </>
  );
}

/** The frame colours of a made-up token: one colour of its own, gold for two or more, grey for none. */
const CUSTOM_FRAMES: Record<string, string> = { W: "w", U: "u", B: "b", R: "r", G: "g" };

/**
 * A made-up token's own card, drawn to fill its box: the frame in its colours, the name, the type line, the rules text
 * and the power and toughness. The board and the card view share it.
 */
export function CustomFace({ def }: { def: CardDef }) {
  const custom = def.custom;
  const colors = custom?.colors ?? [];
  const frame = colors.length === 1 ? (CUSTOM_FRAMES[colors[0]] ?? "gold") : colors.length > 1 ? "gold" : "none";
  const power = (custom?.power ?? "").trim();
  const toughness = (custom?.toughness ?? "").trim();
  return (
    <span className={`pt-custom pt-custom-${frame}`}>
      <span className="pt-custom-name">{def.name}</span>
      <span className="pt-custom-type">{def.typeLine}</span>
      <span className="pt-custom-text">{custom?.text ?? ""}</span>
      {(power !== "" || toughness !== "") && (
        <span className="pt-custom-pt">
          {power === "" ? "—" : power}/{toughness === "" ? "—" : toughness}
        </span>
      )}
    </span>
  );
}

function counterText(kind: string, amount: number): string {
  if (kind === "loyalty") return `Loyalty ${amount}`;
  if (kind === "charge") return `Charge ${amount}`;
  if (kind === "lore") return `Lore ${amount}`;
  if (kind === "defense") return `Defense ${amount}`;
  return `${kind} ×${amount}`;
}

/** The card back: the site's card-back picture, filling the card. */
function CardBack() {
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img className="pt-back" src="/card-back.jpg" alt="Card back" draggable={false} />
  );
}
