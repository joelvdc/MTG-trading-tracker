// The server side of shared links: making one (the website's Share buttons and the phones), the Settings list, and
// the public page's answer.
import type { AccountStore, ShareKind, ShareLink } from "./accounts";
import { loadCardKingdom } from "./cardkingdom";
import { loadDetails } from "./carddetails";
import { syncDecks } from "./decks";
import { deckLegality } from "./legalstore";
import { loadPrices } from "./pricestore";
import { hasTarget, newShareCode, shareTitle, SHARE_KINDS, sharedView, type SharedView } from "./shares";
import { decode, type SyncFile } from "./syncfile";
import { EditError } from "./wishlist";

/** The address of a shared page on the site that answered [req]. */
export function shareUrl(req: Request, code: string): string {
  return `${new URL(req.url).origin}/s/${code}`;
}

/** The item's link (made if it has none, switched on again if it was off). */
export async function makeShare(store: AccountStore, accountId: number, file: SyncFile, kind: string, target: string, now: number): Promise<{ link: ShareLink; turnedOn: boolean }> {
  if (!SHARE_KINDS.includes(kind as ShareKind)) throw new EditError("That can't be shared.");
  const k = kind as ShareKind;
  const t = k === "collection" || k === "wishlist" ? "" : target.trim();
  if (!hasTarget(file, k, t)) throw new NotSynced();
  const had = await store.share(accountId, k, t);
  if (had) {
    if (!had.on) await store.setShareOn(accountId, had.code, true);
    return { link: { ...had, on: true }, turnedOn: !had.on };
  }
  const link = await store.addShare({ code: newShareCode(), accountId, kind: k, target: t, on: true, createdAt: now, opened: 0, lastOpened: null });
  return { link, turnedOn: false };
}

/** The item isn't in the account's synced data (gone, or not synced from the phone yet). */
export class NotSynced extends Error {
  constructor() {
    super("The website doesn't have that yet. Let the phone sync first, then share again.");
  }
}

/** A line of Settings → Shared links. */
export interface ShareLine {
  code: string;
  kind: ShareKind;
  /** "Deck: Atraxa"…; null when the item is gone. */
  title: string | null;
  on: boolean;
  createdAt: number;
  opened: number;
  lastOpened: number | null;
  url: string;
}

const KIND_LABELS: Record<ShareKind, string> = { deck: "Deck", game: "Game", binder: "Binder", collection: "Whole collection", wishlist: "Wishlist" };

export function shareLines(req: Request, links: ShareLink[], file: SyncFile | null): ShareLine[] {
  return links.map((l) => {
    const t = file ? shareTitle(file, l.kind, l.target) : null;
    const title = t === null ? null : l.kind === "collection" || l.kind === "wishlist" ? KIND_LABELS[l.kind] : l.kind === "game" ? t : `${KIND_LABELS[l.kind]}: ${t}`;
    return { code: l.code, kind: l.kind, title, on: l.on, createdAt: l.createdAt, opened: l.opened, lastOpened: l.lastOpened, url: shareUrl(req, l.code) };
  });
}

/** What the public page gets: the view, or why there is none. */
export type ShareAnswer = { state: "ok"; view: SharedView } | { state: "off" | "gone" | "unknown" };

export async function shareAnswer(store: AccountStore, code: string, now: number): Promise<ShareAnswer> {
  const link = await store.shareByCode(code);
  if (!link) return { state: "unknown" };
  if (!link.on) return { state: "off" };
  const [acc, got] = await Promise.all([store.byId(link.accountId), store.getSync(link.accountId)]);
  if (!acc || !got) return { state: "gone" };
  const file = decode(got.bytes);
  const [prices, details, ck] = await Promise.all([loadPrices(), loadDetails(), loadCardKingdom()]);
  let legality = null;
  if (link.kind === "deck") {
    const d = syncDecks(file).find((x) => String(x.deck.archidektId) === link.target);
    if (d) legality = await deckLegality(d, true, now, now + 8_000);
  }
  const view = sharedView(file, link, acc.username, prices, details, ck, legality);
  if (!view) return { state: "gone" };
  await store.openedShare(code, now);
  return { state: "ok", view };
}
