import { cleanName, type Candidate } from "./orders";
import type { Finish } from "./pricing";
import type { ScannedCard } from "./scans";
import type { CardRef } from "./syncfile";

/**
 * Checks a scanned package against its order (the app's PackageCheck.kt, since 1.32): which ordered cards were
 * scanned, which came in another language or printing, which are missing, and what was scanned that nobody ordered.
 * The camera can't tell foil, condition, signed or altered; lines where the order says one of those are marked to
 * check by hand. Keep in step with the app.
 */
export type CheckStatus = "RECEIVED" | "BY_HAND" | "PRINTING_UNSURE" | "WRONG_LANGUAGE" | "WRONG_PRINTING" | "MISSING";

/** In the app's order (worst last), with labels and whether it's a problem. */
export const STATUSES: CheckStatus[] = ["RECEIVED", "BY_HAND", "PRINTING_UNSURE", "WRONG_LANGUAGE", "WRONG_PRINTING", "MISSING"];
export const STATUS_LABELS: Record<CheckStatus, string> = {
  RECEIVED: "Received",
  BY_HAND: "Marked as received",
  PRINTING_UNSURE: "Received, printing not sure",
  WRONG_LANGUAGE: "Wrong language",
  WRONG_PRINTING: "Wrong printing",
  MISSING: "Missing",
};
export const isProblem = (s: CheckStatus) => s === "WRONG_LANGUAGE" || s === "WRONG_PRINTING" || s === "MISSING";

/** [quantity] copies of an order line, and the scan (by uid) they were matched to (none for missing copies). */
export interface CheckPart {
  status: CheckStatus;
  quantity: number;
  scan: ScannedCard | null;
}

export interface CheckLine {
  cand: Candidate;
  parts: CheckPart[];
}

export interface CheckResult {
  lines: CheckLine[];
  /** Scanned copies that match no order line, with how many. */
  extras: { scan: ScannedCard; quantity: number }[];
}

/** The worst thing about the line, for sorting and its colour. */
export function lineStatus(l: CheckLine): CheckStatus {
  let worst = 0;
  for (const p of l.parts) worst = Math.max(worst, STATUSES.indexOf(p.status));
  return STATUSES[worst];
}

export const countOf = (r: CheckResult, s: CheckStatus) => r.lines.reduce((n, l) => n + l.parts.filter((p) => p.status === s).reduce((m, p) => m + p.quantity, 0), 0);
export const orderedCount = (r: CheckResult) => r.lines.reduce((n, l) => n + l.cand.line.quantity, 0);
export const extraCount = (r: CheckResult) => r.extras.reduce((n, x) => n + x.quantity, 0);
export const problemLines = (r: CheckResult) => r.lines.filter((l) => isProblem(lineStatus(l)));

/** What the camera can't see and the order says: worth a look at the card. */
export function byHand(l: CheckLine): string[] {
  const line = l.cand.line;
  return [
    line.foil && line.versionKnown ? "foil" : null,
    line.condition !== "NM" && line.versionKnown ? line.condition : null,
    line.signed ? "signed" : null,
    line.altered ? "altered" : null,
  ].filter((x): x is string => x !== null);
}

/** OrderFiles.normalName. */
export function normalName(name: string): string {
  return cleanName(cleanName(name))
    .toLowerCase()
    .replace(/æ/g, "ae")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

/** A card's name and each face's (OrderFiles.names): Cardmarket's older files write "A / B" where Scryfall has "A // B". */
export function names(name: string): Set<string> {
  return new Set([name, ...name.split(/ \/\/? /)].map(normalName).filter((n) => n !== ""));
}

function sameCard(cand: Candidate, scan: ScannedCard): boolean {
  const have = names(scan.card.name);
  return [...names(cand.line.name), ...(cand.card ? names(cand.card.name) : [])].some((n) => have.has(n));
}

const scanKey = (s: ScannedCard) => s.uid ?? `id:${s.id}`;

/**
 * Compares the order with the scanned cards. [accepted] holds the lines (by index) the owner marked as received by
 * hand. Exact matches are handed out first over the whole order, then same printing in another language, then same
 * card in another printing, so a wrong card can't take a right one's scan.
 */
export function checkPackage(candidates: Candidate[], scans: ScannedCard[], accepted: Set<number> = new Set()): CheckResult {
  const left = new Map(scans.map((s) => [scanKey(s), s.quantity ?? 1]));
  const need = new Map(candidates.map((c) => [c.index, c.line.quantity]));
  const parts = new Map<number, CheckPart[]>(candidates.map((c) => [c.index, []]));

  const pass = (status: (c: Candidate, s: ScannedCard) => CheckStatus | null) => {
    for (const cand of candidates) {
      for (const scan of scans) {
        const want = need.get(cand.index)!;
        const have = left.get(scanKey(scan))!;
        if (want <= 0 || have <= 0) continue;
        const st = status(cand, scan);
        if (!st) continue;
        const n = Math.min(want, have);
        parts.get(cand.index)!.push({ status: st, quantity: n, scan });
        need.set(cand.index, want - n);
        left.set(scanKey(scan), have - n);
      }
    }
  };
  // The monthly Cardmarket file and unknown language numbers don't say the language: nothing to compare.
  const languageSaid = (c: Candidate) => c.line.versionKnown && c.line.languageKnown;
  const samePrinting = (c: Candidate, s: ScannedCard) => c.card !== null && !c.guessed && s.exactPrinting !== false && c.card.scryfallId === s.card.scryfallId;
  pass((c, s) => (samePrinting(c, s) && (!languageSaid(c) || c.line.language === (s.language ?? "EN")) ? "RECEIVED" : null));
  pass((c, s) => (samePrinting(c, s) ? "WRONG_LANGUAGE" : null));
  pass((c, s) => {
    if (!sameCard(c, s)) return null;
    return c.card === null || c.guessed || s.exactPrinting === false ? "PRINTING_UNSURE" : "WRONG_PRINTING";
  });

  const lines = candidates.map((cand): CheckLine => {
    const found = parts.get(cand.index)!;
    const missing = need.get(cand.index)!;
    const all: CheckPart[] = missing > 0 ? [...found, { status: "MISSING", quantity: missing, scan: null }] : found;
    return {
      cand,
      parts: accepted.has(cand.index) ? all.map((p) => ({ ...p, status: p.status === "RECEIVED" ? "RECEIVED" : "BY_HAND" })) : all,
    };
  });
  const extras = scans.flatMap((s) => {
    const n = left.get(scanKey(s))!;
    return n > 0 ? [{ scan: s, quantity: n }] : [];
  });
  return { lines, extras };
}

/** Copies to add to the collection: the card that is physically there, with the order's details. */
export interface Addition {
  card: CardRef;
  finish: Finish;
  condition: string;
  language: string;
  quantity: number;
  price: number | null;
  signed: boolean;
  altered: boolean;
}

const scanFinish = (s: ScannedCard): Finish => (s.etched ? "ETCHED" : s.foil ? "FOIL" : "NONFOIL");

/**
 * What goes into the collection after a check (PackageCheck.additions): every scanned copy, with what the order knows
 * and the camera can't (price paid, condition, finish, signed, altered) for copies that matched a line. A copy in the
 * wrong language or printing is added as scanned, since that is the card in hand; a line marked as received by hand is
 * added as ordered. [extras] adds the scanned cards that weren't in the order too. The finish is resolved against the
 * printing by whoever adds it.
 */
export function additions(result: CheckResult, extras: boolean): Addition[] {
  const out: Addition[] = [];
  for (const l of result.lines) {
    for (const p of l.parts) {
      const line = l.cand.line;
      const scan = p.scan;
      const ordered = l.cand.guessed ? null : l.cand.card;
      const asOrdered = p.status === "RECEIVED" || p.status === "BY_HAND" || p.status === "PRINTING_UNSURE";
      const card = asOrdered ? (ordered ?? scan?.card ?? l.cand.card) : scan?.card;
      if (!card || p.status === "MISSING") continue;
      const known = line.versionKnown;
      out.push({
        card,
        finish: known ? (line.foil ? "FOIL" : "NONFOIL") : scan ? scanFinish(scan) : "NONFOIL",
        condition: known ? line.condition : (scan?.condition ?? "NM"),
        language: asOrdered && known && line.languageKnown ? line.language : (scan?.language ?? line.language),
        quantity: p.quantity,
        price: line.price,
        signed: line.signed,
        altered: line.altered,
      });
    }
  }
  if (extras) {
    for (const { scan, quantity } of result.extras) {
      out.push({ card: scan.card, finish: scanFinish(scan), condition: scan.condition ?? "NM", language: scan.language ?? "EN", quantity, price: null, signed: false, altered: false });
    }
  }
  return out;
}

/** The scanned copies an "add" uses, by scan uid: every matched part, and the extras when they're added too. */
export function scanUse(result: CheckResult, extras: boolean): Map<string, number> {
  const used = new Map<string, number>();
  const add = (s: ScannedCard, n: number) => {
    if (s.uid) used.set(s.uid, (used.get(s.uid) ?? 0) + n);
  };
  for (const l of result.lines) for (const p of l.parts) if (p.scan) add(p.scan, p.quantity);
  if (extras) for (const x of result.extras) add(x.scan, x.quantity);
  return used;
}

const eur = (v: number | null) => (v == null ? "?" : `${v.toFixed(2)} EUR`);

function describe(l: CheckLine): string {
  const line = l.cand.line;
  const c = l.cand.guessed ? null : l.cand.card;
  const where = c
    ? `${c.setName} (${c.setCode.toUpperCase()}) #${c.collectorNumber}`
    : [line.setName, line.setCode ? `(${line.setCode.toUpperCase()})` : "", line.number ? `#${line.number}` : ""].filter((x) => x).join(" ");
  const version = [
    line.versionKnown && line.languageKnown ? line.language : null,
    line.versionKnown ? line.condition : null,
    line.foil ? "foil" : null,
    line.signed ? "signed" : null,
    line.altered ? "altered" : null,
  ]
    .filter((x): x is string => !!x)
    .join(", ");
  return [lineLabel(line), where, version].filter((x) => x).join(", ");
}

/** OrderLine.label: the name, or the Cardmarket product number when the file had no name. */
export function lineLabel(line: Candidate["line"]): string {
  return line.name || (line.productId != null ? `Cardmarket product ${line.productId}` : "Unknown card");
}

/** A plain-text list of what's wrong, to send to the shop (PackageCheck.report). Prices in EUR, as the shops bill. */
export function report(result: CheckResult, orderName: string): string {
  const out: string[] = [`Problems with ${orderName}`];
  const section = (title: string, status: CheckStatus, detail: (p: CheckPart) => string = () => "") => {
    const rows = result.lines.flatMap((l) => l.parts.filter((p) => p.status === status).map((p) => [l, p] as const));
    if (rows.length === 0) return;
    out.push("", `${title} (${rows.reduce((n, [, p]) => n + p.quantity, 0)}):`);
    for (const [l, p] of rows) out.push(`- ${p.quantity}x ${describe(l)}, paid ${eur(l.cand.line.price)} each${detail(p)}`);
  };
  section("Missing", "MISSING");
  section("Wrong language", "WRONG_LANGUAGE", (p) => `; received in ${p.scan?.language}`);
  section("Wrong printing", "WRONG_PRINTING", (p) => (p.scan ? `; received ${p.scan.card.setName} (${p.scan.card.setCode.toUpperCase()}) #${p.scan.card.collectorNumber}` : ""));
  if (problemLines(result).length === 0) out.push("", "Nothing: every ordered card was received.");
  return out.join("\n").trim();
}
