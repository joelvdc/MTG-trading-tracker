// Importing a collection CSV (ManaBox, Moxfield, Archidekt, the app's own export…), following the app's
// MtgRepository.importCollectionCsv and Csv.kt: the same columns, the same condition and language names, one Scryfall
// lookup per printing, binders found or made by name, a purchase price and notes only where the pile has none.
import { LANGUAGES, resolveFinish } from "./cards";
import { applyCollectionEdit } from "./collection";
import type { PriceFile } from "./prices";
import type { Finish } from "./pricing";
import { binders as bindersOf, collection, type CardRef, type SyncFile, newUid } from "./syncfile";

/** The delimiter of the first line: ";" when it has more of those than commas (Csv.delimiter). */
export function csvDelimiter(text: string): string {
  const first = text.split("\n", 1)[0] ?? "";
  const count = (c: string) => first.split(c).length - 1;
  return count(";") > count(",") ? ";" : ",";
}

/** RFC 4180-ish: quoted fields, doubled quotes, CRLF or LF (Csv.parse). */
export function parseCsv(text: string, delimiter = ","): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else quoted = false;
      } else field += c;
    } else if (c === '"') {
      // A quote only opens a quoted field at its start.
      if (field === "") quoted = true;
      else field += c;
    } else if (c === delimiter) {
      row.push(field);
      field = "";
    } else if (c === "\r") {
      // skipped
    } else if (c === "\n") {
      row.push(field);
      field = "";
      rows.push(row);
      row = [];
    } else field += c;
  }
  if (field !== "" || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

/** Condition names of ManaBox, Cardmarket and others onto Cardmarket's codes (Csv.kt parseCondition). */
export function parseCondition(raw: string | undefined): string {
  const s = raw?.trim().toLowerCase().replace(/-/g, "_").replace(/ /g, "_");
  if (!s) return "NM";
  if (s === "nm" || s.includes("near")) return "NM";
  if (s === "mt" || s === "m" || s.includes("mint")) return "MT";
  if (s === "ex" || s.includes("excellent")) return "EX";
  if (s === "gd" || s === "good") return "GD";
  if (s === "lp" || s.includes("light")) return "LP";
  if (s === "po" || s.includes("poor") || s.includes("damaged") || s.includes("heav")) return "PO";
  if (s === "pl" || s.includes("played") || s.includes("moderate")) return "PL";
  return "NM";
}

export function parseLanguage(raw: string | undefined): string {
  const s = raw?.trim().toLowerCase();
  if (s === undefined) return "EN";
  const hit = LANGUAGES.find(([code, name]) => s === code.toLowerCase() || s === name.toLowerCase());
  if (hit) return hit[0];
  if (s === "jp") return "JA";
  if (s === "zh" || s === "chinese") return "ZHS";
  return "EN";
}

/** A yes/no column ("true", "yes", "1", "x"…). */
export function csvFlag(raw: string | undefined): boolean {
  return ["true", "yes", "y", "1", "x", "signed", "altered"].includes(raw?.trim().toLowerCase() ?? "");
}

/** What Scryfall's /cards/collection takes for one card. */
export type Identifier = { id: string } | { set: string; collector_number: string } | { name: string };

export interface ImportLine {
  ident: Identifier;
  key: string;
  qty: number;
  finish: Finish;
  condition: string;
  language: string;
  binder: string | null;
  /** Euros for one copy. */
  paid: number | null;
  /** The price as typed in kroner or dollars (the app's export). */
  typed: { amount: number; currency: string } | null;
  notes: string | null;
  signed: boolean;
  altered: boolean;
}

/** The CSV's card lines; rows with no id, set+number or name are left out. */
export function readCsv(text: string): ImportLine[] {
  const clean = text.replace(/^﻿/, "");
  const rows = parseCsv(clean, csvDelimiter(clean)).filter((r) => r.some((x) => x.trim() !== ""));
  if (rows.length < 2) return [];
  const header = rows[0].map((h) => h.trim().toLowerCase());
  const col = (...names: string[]) => {
    for (const n of names) {
      const i = header.indexOf(n);
      if (i >= 0) return i;
    }
    return -1;
  };
  const iId = col("scryfall id", "scryfall_id", "scryfallid");
  const iSet = col("set code", "set_code", "edition code", "set");
  const iNum = col("collector number", "collector_number", "card number", "number");
  const iName = col("name", "card name", "card_name");
  const iQty = col("quantity", "qty", "count", "amount");
  const iFoil = col("foil", "finish", "printing");
  const iCond = col("condition");
  const iLang = col("language", "lang");
  const iBinder = col("binder name", "binder");
  const iPaid = col("purchase price", "purchase_price", "price paid");
  const iTyped = col("purchase price as typed");
  const iTypedCur = col("typed currency");
  const iNotes = col("notes", "note");
  const iSigned = col("signed");
  const iAltered = col("altered");
  const num = (s: string | undefined) => {
    if (s === undefined) return null;
    const n = Number(s.replace(",", "."));
    return Number.isFinite(n) ? n : null;
  };
  const out: ImportLine[] = [];
  for (const r of rows.slice(1)) {
    const v = (i: number) => {
      const s = i >= 0 ? r[i]?.trim() : undefined;
      return s ? s : undefined;
    };
    const q = Number.parseInt(v(iQty) ?? "", 10);
    const qty = Number.isFinite(q) && String(q) === v(iQty) ? q : 1;
    if (qty < 1) continue;
    const ft = v(iFoil)?.toLowerCase();
    const finish: Finish = ft === "etched" ? "ETCHED" : ft && ["foil", "true", "yes", "1"].includes(ft) ? "FOIL" : "NONFOIL";
    const paidRaw = num(v(iPaid));
    const paid = paidRaw !== null && paidRaw > 0 ? paidRaw : null;
    const typedAmount = num(v(iTyped));
    const typedCur = v(iTypedCur)?.toUpperCase();
    const typed = paid !== null && typedAmount !== null && (typedCur === "DKK" || typedCur === "USD") ? { amount: typedAmount, currency: typedCur } : null;
    const base = {
      qty: Math.min(qty, 9999),
      finish,
      condition: parseCondition(v(iCond)),
      language: parseLanguage(v(iLang)),
      binder: v(iBinder) ?? null,
      paid,
      typed,
      notes: v(iNotes) ?? null,
      signed: csvFlag(v(iSigned)),
      altered: csvFlag(v(iAltered)),
    };
    const id = v(iId);
    const set = v(iSet);
    const no = v(iNum);
    const name = v(iName);
    if (id) out.push({ ...base, ident: { id }, key: `id:${id.toLowerCase()}` });
    else if (set && no) out.push({ ...base, ident: { set: set.toLowerCase(), collector_number: no }, key: `sn:${set.toLowerCase()}|${no.toLowerCase()}` });
    else if (name) out.push({ ...base, ident: { name }, key: `n:${name.toLowerCase()}` });
  }
  return out;
}

/** The lines' identifiers, each printing once. */
export function identifiers(lines: ImportLine[]): Identifier[] {
  const seen = new Map<string, Identifier>();
  for (const l of lines) if (!seen.has(l.key)) seen.set(l.key, l.ident);
  return [...seen.values()];
}

/** Cards found on Scryfall, by every key a line can have (Scryfall's answers don't say which identifier they match). */
export function keyCards(cards: CardRef[]): Map<string, CardRef> {
  const m = new Map<string, CardRef>();
  for (const c of cards) {
    m.set(`id:${c.scryfallId.toLowerCase()}`, c);
    m.set(`sn:${c.setCode.toLowerCase()}|${c.collectorNumber.toLowerCase()}`, c);
    const n = c.name.toLowerCase();
    if (!m.has(`n:${n}`)) m.set(`n:${n}`, c);
    const front = n.split(" // ")[0];
    if (!m.has(`n:${front}`)) m.set(`n:${front}`, c);
  }
  return m;
}

export interface ImportResult {
  imported: number;
  notFound: number;
  /** Names (or set numbers) of what Scryfall didn't know, up to 20. */
  missing: string[];
  newBinders: string[];
}

/**
 * Adds the lines to the collection: lines without a binder go to [defaultBinder] (a binder uid, null for Unsorted);
 * a binder name that doesn't exist yet becomes a new binder.
 */
export function applyImport(
  file: SyncFile,
  lines: ImportLine[],
  cards: Map<string, CardRef>,
  defaultBinder: string | null,
  now: number,
  rates: PriceFile["rates"] | null = null,
  makeUid: () => string = newUid,
): ImportResult {
  const res: ImportResult = { imported: 0, notFound: 0, missing: [], newBinders: [] };
  const binderUid = new Map<string, string | null>();
  const binderFor = (name: string | null): string | null => {
    if (name === null) return defaultBinder;
    const low = name.trim().toLowerCase();
    if (low === "unsorted" || low === "") return null;
    if (binderUid.has(low)) return binderUid.get(low)!;
    let uid = bindersOf(file).find((b) => b.name.trim().toLowerCase() === low)?.uid ?? null;
    if (uid === null) {
      try {
        uid = applyCollectionEdit(file, { type: "addBinder", name }, now, rates, makeUid).uid;
        res.newBinders.push(name.trim());
      } catch {
        uid = null; // A name the app keeps for itself ("All", "Wishlist") or too long: Unsorted.
      }
    }
    binderUid.set(low, uid);
    return uid;
  };
  for (const l of lines) {
    const card = cards.get(l.key);
    if (!card) {
      res.notFound += l.qty;
      if (res.missing.length < 20) res.missing.push("name" in l.ident ? l.ident.name : "id" in l.ident ? l.ident.id : `${l.ident.set.toUpperCase()} #${l.ident.collector_number}`);
      continue;
    }
    const { uid } = applyCollectionEdit(
      file,
      {
        type: "add",
        card,
        quantity: l.qty,
        finish: resolveFinish(card, l.finish),
        condition: l.condition,
        language: l.language,
        binder: binderFor(l.binder),
        signed: l.signed,
        altered: l.altered,
      },
      now,
      rates,
      makeUid,
    );
    if (l.paid !== null || l.notes !== null) {
      const it = collection(file).find((s) => s.item.uid === uid)?.item;
      if (it) {
        if (it.purchasePrice == null && l.paid !== null) {
          it.purchasePrice = Math.round(l.paid * 100) / 100;
          it.paidTyped = l.typed?.amount ?? null;
          it.paidCurrency = l.typed?.currency ?? null;
        }
        if (it.notes == null && l.notes !== null) it.notes = l.notes.slice(0, 1000);
        // collection() hands out the file's own objects, so these changes are in the file.
      }
    }
    res.imported += l.qty;
  }
  return res;
}
