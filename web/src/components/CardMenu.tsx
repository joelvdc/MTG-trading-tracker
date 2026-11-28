"use client";

import { Fragment, useEffect, useLayoutEffect, useRef, useState } from "react";
import { finishesOf } from "@/lib/cards";
import type { DeckView } from "@/lib/deckshared";
import type { Finish } from "@/lib/pricing";
import type { CardRef } from "@/lib/syncfile";
import { cardmarketUrlByName, cardmarketUrlFor, cardtraderUrlByName, cardtraderUrlFor, edhrecUrlFor } from "./cardLinks";
import { categoryTargets, deckAction } from "./DeckEditDialogs";

/** The deck's two special categories, by the names the sync file keeps. */
const COMMANDER = "Commander";
const MAYBEBOARD = "Maybeboard";
/** How close to the window's edge the menu is kept (px). */
const EDGE = 8;

/**
 * What the menu needs of a line: a deck page's row (DeckRow), or a shared deck's line (SharedDeckLine), which has
 * only the name, the copies and the finish.
 */
export interface MenuRow {
  name: string;
  /** The line in the deck (a shared page's line has none: its menu only copies the name and links to the card). */
  lineUid?: string;
  /** The printing shown: one you own when "my printings" is on, else the deck's. */
  card?: CardRef;
  /** The deck's own printing, when another one is shown (which "Switch to foil" keeps). */
  original?: CardRef | null;
  finish?: Finish;
  quantity?: number;
  commander?: boolean;
  maybeboard?: boolean;
}

/** The menu's items, by what they do. */
export type CardMenuItem =
  | "open"
  | "copy"
  | "scryfall"
  | "edhrec"
  | "cardmarket"
  | "cardtrader"
  | "more"
  | "fewer"
  | "printing"
  | "finish"
  | "commander"
  | "maybe"
  | "category"
  | "select"
  | "remove";

/** One line of the menu: its item, the label it shows and, for a link, where it goes. */
export interface CardMenuEntry {
  key: CardMenuItem;
  label: string;
  /** A card page or shop, opened in a new tab. */
  href?: string;
}

/** Scryfall's search for exactly this card's name. */
export function scryfallUrlFor(name: string): string {
  return "https://scryfall.com/search?q=" + encodeURIComponent(`!"${name}"`);
}

/** Cardmarket's page of the line's printing, or a search for its name when its printing isn't known. */
export function cardmarketUrlOfRow(row: MenuRow): string {
  return row.card ? cardmarketUrlFor(row.card, (row.finish ?? "NONFOIL") !== "NONFOIL") : cardmarketUrlByName(row.name);
}

/** CardTrader's search for the line's card. */
export function cardtraderUrlOfRow(row: MenuRow): string {
  return row.card ? cardtraderUrlFor(row.card) : cardtraderUrlByName(row.name);
}

/**
 * The finish the line can switch to (the other of foil and nonfoil), or null when its printing comes in one finish
 * only, so "Switch to foil" isn't offered where it couldn't work.
 */
export function otherFinishOf(row: MenuRow): Finish | null {
  const card = row.original ?? row.card;
  if (!card) return null;
  const want: Finish = (row.finish ?? "NONFOIL") === "NONFOIL" ? "FOIL" : "NONFOIL";
  return finishesOf(card).includes(want) ? want : null;
}

/**
 * The menu's lines, in order: what every card offers (opening it, copying its name, its Scryfall, EDHREC, Cardmarket
 * and CardTrader pages), then, where the deck can be edited here, what changes the line.
 * [openable] is false on a page without a card window (a shared deck), whose menu only copies and links.
 */
export function cardMenuEntries(row: MenuRow, editable: boolean, openable = true): CardMenuEntry[] {
  const list: CardMenuEntry[] = [];
  if (openable) list.push({ key: "open", label: "Open details" });
  list.push({ key: "copy", label: "Copy card name" });
  list.push({ key: "scryfall", label: "Scryfall", href: scryfallUrlFor(row.name) });
  list.push({ key: "edhrec", label: "EDHREC", href: edhrecUrlFor(row.name) });
  list.push({ key: "cardmarket", label: "Cardmarket", href: cardmarketUrlOfRow(row) });
  list.push({ key: "cardtrader", label: "CardTrader", href: cardtraderUrlOfRow(row) });
  if (!editable) return list;
  list.push({ key: "more", label: "+1 copy" });
  list.push({ key: "fewer", label: "−1 copy" });
  list.push({ key: "printing", label: "Change printing" });
  const other = otherFinishOf(row);
  if (other) list.push({ key: "finish", label: other === "FOIL" ? "Switch to foil" : "Switch to nonfoil" });
  list.push({ key: "commander", label: row.commander ? "Back to the deck" : "Make commander" });
  list.push({ key: "maybe", label: row.maybeboard ? "Back to the deck" : "Move to maybeboard" });
  list.push({ key: "category", label: "Move to category ›" });
  list.push({ key: "select", label: "Select" });
  list.push({ key: "remove", label: "Remove" });
  return list;
}

/** The menu's items in order (see [cardMenuEntries]), for the lines a card gets on a deck page. */
export function cardMenuItems(row: MenuRow, editable: boolean, openable = true): CardMenuItem[] {
  return cardMenuEntries(row, editable, openable).map((e) => e.key);
}

/**
 * The right-click menu of one of a deck's cards, at the pointer (a long press on a touch screen opens it too: the
 * browser sends the same event). It holds the card's name, its pages and, where the deck can be edited here, the
 * changes to its line, and it stays inside the window (flipping left or up). It closes on Esc, a click elsewhere,
 * scrolling or a choice; the arrow keys move between its items and Enter picks one.
 */
export function CardMenu({
  row,
  view = null,
  x,
  y,
  editable,
  onClose,
  onOpen,
  onChangePrinting,
  onSelect,
  onChanged = () => undefined,
  onNotice = () => undefined,
  onError = () => undefined,
}: {
  row: MenuRow;
  /** The deck: its categories and its id (null on a shared page, whose menu changes nothing). */
  view?: DeckView | null;
  /** Where the pointer was, in the window. */
  x: number;
  y: number;
  /** Whether the deck can be edited here (view.local && !view.readOnly), as the card window's own test. */
  editable: boolean;
  onClose: () => void;
  /** Opens the card's window, as a click does. */
  onOpen?: () => void;
  /** Opens the card's window on its printings. */
  onChangePrinting?: () => void;
  /** Leaves the menu in select mode with this card ticked. */
  onSelect?: () => void;
  /** The page reads the deck again after an edit, with the short notice to show. */
  onChanged?: (notice: string | null) => void;
  /** A short notice on the page ("Copied Sol Ring"). */
  onNotice?: (text: string) => void;
  /** Something that didn't work, for the page's error bar. */
  onError?: (text: string) => void;
}) {
  const ref = useRef<HTMLDivElement | null>(null);
  const subRef = useRef<HTMLDivElement | null>(null);
  // Where the menu sits, once its size is known: flipped left or up rather than running off the window.
  const [at, setAt] = useState({ left: x, top: y, leftward: false, placed: false });
  const [submenu, setSubmenu] = useState(false);
  // The "Move to category" item, and where its list sits: beside the menu (not inside it, which scrolls and would
  // clip it), at the item's height, flipped left or up rather than running off the window.
  const catRef = useRef<HTMLButtonElement | null>(null);
  const [subAt, setSubAt] = useState<{ left: number; top: number } | null>(null);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const box = el.getBoundingClientRect();
    const leftward = x > window.innerWidth / 2;
    const left = x + box.width + EDGE > window.innerWidth ? Math.max(EDGE, x - box.width) : x;
    const top = y + box.height + EDGE > window.innerHeight ? Math.max(EDGE, y - box.height) : y;
    setAt({ left, top, leftward, placed: true });
  }, [x, y]);

  useLayoutEffect(() => {
    const menu = ref.current?.getBoundingClientRect();
    const item = catRef.current?.getBoundingClientRect();
    const sub = subRef.current?.getBoundingClientRect();
    if (!submenu || !menu || !item || !sub) return setSubAt(null);
    const right = menu.right + sub.width + EDGE <= window.innerWidth;
    const left = !at.leftward && right ? menu.right : menu.left - sub.width >= EDGE ? menu.left - sub.width : right ? menu.right : EDGE;
    const top = Math.max(EDGE, Math.min(item.top - 4, window.innerHeight - sub.height - EDGE));
    setSubAt({ left, top });
  }, [submenu, at]);

  useEffect(() => {
    // The menu has the keyboard: the arrow keys work at once.
    ref.current?.focus();
  }, []);

  useEffect(() => {
    const inside = (e: Event) => !!(ref.current?.contains(e.target as Node) || subRef.current?.contains(e.target as Node));
    const away = (e: Event) => {
      if (!inside(e)) onClose();
    };
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    // Scrolling the page or turning the phone takes the menu out from under the pointer; its own scrolling doesn't.
    const moved = (e: Event) => {
      if (!inside(e)) onClose();
    };
    document.addEventListener("mousedown", away);
    document.addEventListener("touchstart", away, { passive: true });
    document.addEventListener("keydown", key);
    window.addEventListener("scroll", moved, true);
    window.addEventListener("resize", onClose);
    return () => {
      document.removeEventListener("mousedown", away);
      document.removeEventListener("touchstart", away);
      document.removeEventListener("keydown", key);
      window.removeEventListener("scroll", moved, true);
      window.removeEventListener("resize", onClose);
    };
  }, [onClose]);

  useEffect(() => {
    // The list of categories opens ready for the arrow keys.
    if (submenu) subRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
  }, [submenu]);

  /** Closes the menu first, then does what the chosen item does. */
  function pick(run: () => void) {
    onClose();
    run();
  }

  /** Runs one deck action, then has the page read the deck again (or show what went wrong). */
  async function edit(body: Record<string, unknown>, notice: string | null) {
    if (!view) return;
    const r = await deckAction(view.id, body);
    if (r.error) onError(r.error);
    else onChanged(notice);
  }

  /** Puts the card's name on the clipboard, with the page's short notice. */
  async function copyName() {
    try {
      await navigator.clipboard.writeText(row.name);
      onNotice(`Copied ${row.name}`);
    } catch {
      onError("The browser didn't allow copying.");
    }
  }

  /** What one item does: its deck action, or the page's own callback. */
  function actionOf(key: CardMenuItem): () => void {
    const uid = row.lineUid ?? "";
    const quantity = row.quantity ?? 1;
    switch (key) {
      case "open":
        return () => onOpen?.();
      case "copy":
        return () => void copyName();
      case "more":
        return () => void edit({ action: "set-quantity", uid, quantity: quantity + 1 }, null);
      case "fewer":
        return () => void edit({ action: "set-quantity", uid, quantity: quantity - 1 }, quantity > 1 ? null : `${row.name} removed.`);
      case "printing":
        return () => onChangePrinting?.();
      case "finish": {
        // The deck's own printing, which the line keeps (the one shown may be a printing you own instead).
        const card = row.original ?? row.card;
        const finish = otherFinishOf(row);
        return () => {
          if (card && finish) void edit({ action: "printing", uid, card, finish }, null);
        };
      }
      case "commander":
        return row.commander
          ? () => void edit({ action: "move", uids: [uid], category: null }, `${row.name} is back in the deck.`)
          : () => void edit({ action: "move", uids: [uid], category: COMMANDER }, `${row.name} is a commander now.`);
      case "maybe":
        return row.maybeboard
          ? () => void edit({ action: "move", uids: [uid], category: null }, `${row.name} is back in the deck.`)
          : () => void edit({ action: "move", uids: [uid], category: MAYBEBOARD }, `${row.name} went to the maybeboard.`);
      case "select":
        return () => onSelect?.();
      case "remove":
        return () => void edit({ action: "remove", uids: [uid] }, `${row.name} removed.`);
      default:
        return () => undefined;
    }
  }

  /** The arrow keys move between a menu's items, Enter picks (the browser's own doing), Esc closes. */
  function onKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    if (e.key === "Escape") {
      e.preventDefault();
      e.stopPropagation();
      onClose();
      return;
    }
    if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(e.key)) return;
    const items = [...e.currentTarget.querySelectorAll<HTMLElement>('[role="menuitem"]')];
    if (items.length === 0) return;
    const here = items.indexOf(document.activeElement as HTMLElement);
    const to = e.key === "Home" ? 0 : e.key === "End" ? items.length - 1 : e.key === "ArrowDown" ? (here < 0 ? 0 : here + 1) : here < 0 ? items.length - 1 : here - 1;
    e.preventDefault();
    e.stopPropagation();
    items[Math.max(0, Math.min(items.length - 1, to))]?.focus();
  }

  const entries = cardMenuEntries(row, editable, !!onOpen);
  const firstEdit = entries.findIndex((e) => e.key === "more");
  const categories = view ? categoryTargets(view) : [];

  return (
    <>
    <div
      ref={ref}
      className="card-menu"
      role="menu"
      aria-label={row.name}
      tabIndex={-1}
      style={{ left: at.left, top: at.top, visibility: at.placed ? "visible" : "hidden" }}
      onKeyDown={onKeyDown}
      onContextMenu={(e) => e.preventDefault()}
    >
      <span className="card-menu-head">{row.name}</span>
      {entries.map((entry, i) => (
        <Fragment key={entry.key}>
          {i === firstEdit && <hr className="menu-sep" />}
          {entry.href ? (
            <a role="menuitem" tabIndex={-1} href={entry.href} target="_blank" rel="noreferrer" onMouseEnter={() => setSubmenu(false)} onClick={() => pick(() => undefined)}>
              {entry.label}
            </a>
          ) : (
            <button
              ref={entry.key === "category" ? catRef : undefined}
              type="button"
              role="menuitem"
              tabIndex={-1}
              className={entry.key === "remove" ? "danger-text" : undefined}
              aria-haspopup={entry.key === "category" ? "menu" : undefined}
              aria-expanded={entry.key === "category" ? submenu : undefined}
              onMouseEnter={() => setSubmenu(entry.key === "category")}
              onClick={entry.key === "category" ? () => setSubmenu(true) : () => pick(actionOf(entry.key))}
            >
              {entry.label}
            </button>
          )}
        </Fragment>
      ))}
    </div>
      {submenu && view && (
        <div
          ref={subRef}
          className="card-menu"
          role="menu"
          aria-label="Move to category"
          style={{ left: subAt?.left ?? 0, top: subAt?.top ?? 0, visibility: subAt ? "visible" : "hidden" }}
          onKeyDown={onKeyDown}
          onContextMenu={(e) => e.preventDefault()}
        >
          {categories.map((name) => (
            <button key={name} type="button" role="menuitem" tabIndex={-1} onClick={() => pick(() => void edit({ action: "move", uids: [row.lineUid ?? ""], category: name }, null))}>
              {name}
            </button>
          ))}
        </div>
      )}
    </>
  );
}
