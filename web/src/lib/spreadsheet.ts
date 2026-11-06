// Reads the first sheet of a spreadsheet into rows of cell texts: old Excel (.xls, as CardTrader exports orders), new
// Excel (.xlsx) or CSV; the app's Spreadsheet.kt. Numbers come out without a trailing ".0" ("231", "1.5"). Server only.
import { inflateRawSync } from "node:zlib";
import { csvDelimiter, parseCsv } from "./csvimport";

export class Unreadable extends Error {}

export function readSpreadsheet(bytes: Uint8Array): string[][] {
  let rows: string[][];
  if (bytes.length >= 8 && bytes[0] === 0xd0 && bytes[1] === 0xcf && bytes[2] === 0x11 && bytes[3] === 0xe0) rows = readXls(bytes);
  else if (bytes.length >= 4 && bytes[0] === 0x50 && bytes[1] === 0x4b) rows = readXlsx(bytes);
  else {
    const text = new TextDecoder("utf-8").decode(bytes).replace(/^﻿/, "");
    rows = parseCsv(text, csvDelimiter(text));
  }
  return rows.map((r) => r.map((c) => c.trim())).filter((r) => r.some((c) => c !== ""));
}

export function numberText(v: number): string {
  return Number.isInteger(v) && Math.abs(v) < 1e15 ? String(v) : String(v);
}

function grid(cells: Map<string, string>): string[][] {
  if (cells.size === 0) return [];
  let rows = 0;
  let cols = 0;
  for (const k of cells.keys()) {
    const [r, c] = k.split(",").map(Number);
    rows = Math.max(rows, r + 1);
    cols = Math.max(cols, c + 1);
  }
  return Array.from({ length: rows }, (_, r) => Array.from({ length: cols }, (_, c) => cells.get(`${r},${c}`) ?? ""));
}

// ---- Excel 97–2003: a compound file holding a "Workbook" stream of BIFF8 records ----

const END_OF_CHAIN = -2;
const FREE = -1;

function readXls(file: Uint8Array): string[][] {
  return grid(xlsCells(workbookStream(file)));
}

const u16 = (b: Uint8Array, at: number) => (b[at] ?? 0) | ((b[at + 1] ?? 0) << 8);
const i32 = (b: Uint8Array, at: number) => (u16(b, at) | (u16(b, at + 2) << 16)) | 0;

function workbookStream(file: Uint8Array): Uint8Array {
  if (file.length < 512) throw new Unreadable("The file is too short to be an Excel file.");
  const sectorSize = 1 << u16(file, 0x1e);
  const miniSectorSize = 1 << u16(file, 0x20);
  const firstDirSector = i32(file, 0x30);
  const miniCutoff = i32(file, 0x38);
  const firstMiniFat = i32(file, 0x3c);
  let difatSector = i32(file, 0x44);
  const sector = (n: number) => (n + 1) * sectorSize;

  const fatSectors: number[] = [];
  for (let i = 0; i < 109; i++) {
    const s = i32(file, 0x4c + i * 4);
    if (s >= 0) fatSectors.push(s);
  }
  let guard = 0;
  while (difatSector >= 0 && guard++ < 10_000) {
    const at = sector(difatSector);
    const per = sectorSize / 4 - 1;
    for (let i = 0; i < per; i++) {
      const s = i32(file, at + i * 4);
      if (s >= 0) fatSectors.push(s);
    }
    difatSector = i32(file, at + per * 4);
  }
  const perSector = sectorSize / 4;
  const fat = new Int32Array(fatSectors.length * perSector);
  fatSectors.forEach((s, k) => {
    const at = sector(s);
    if (at + sectorSize > file.length) throw new Unreadable("The Excel file is damaged.");
    for (let i = 0; i < perSector; i++) fat[k * perSector + i] = i32(file, at + i * 4);
  });
  const chain = (start: number): number[] => {
    const out: number[] = [];
    let s = start;
    while (s >= 0 && s !== END_OF_CHAIN && s !== FREE && out.length <= fat.length) {
      out.push(s);
      s = s < fat.length ? fat[s] : END_OF_CHAIN;
    }
    return out;
  };
  const readChain = (start: number, size: number): Uint8Array => {
    const out = new Uint8Array(Math.max(0, size));
    let p = 0;
    for (const s of chain(start)) {
      if (p >= size) break;
      const at = sector(s);
      const part = file.subarray(at, Math.min(at + sectorSize, file.length));
      out.set(part.subarray(0, Math.min(part.length, size - p)), p);
      p += part.length;
    }
    return out;
  };

  const dir = readChain(firstDirSector, chain(firstDirSector).length * sectorSize);
  const entries: { name: string; type: number; start: number; size: number }[] = [];
  for (let i = 0; i < Math.floor(dir.length / 128); i++) {
    const at = i * 128;
    const nameBytes = Math.max(0, Math.min(64, u16(dir, at + 0x40) - 2));
    const name = new TextDecoder("utf-16le").decode(dir.subarray(at, at + nameBytes));
    entries.push({ name, type: dir[at + 0x42], start: i32(dir, at + 0x74), size: i32(dir, at + 0x78) });
  }
  const root = entries.find((e) => e.type === 5);
  if (!root) throw new Unreadable("The Excel file has no contents.");
  const book = entries.find((e) => e.type === 2 && (e.name === "Workbook" || e.name === "Book"));
  if (!book) throw new Unreadable("The file has no Excel workbook in it.");
  if (book.size >= miniCutoff) return readChain(book.start, book.size);

  const miniStream = readChain(root.start, root.size);
  const miniFatBytes = readChain(firstMiniFat, chain(firstMiniFat).length * sectorSize);
  const miniFat = new Int32Array(Math.floor(miniFatBytes.length / 4));
  for (let i = 0; i < miniFat.length; i++) miniFat[i] = i32(miniFatBytes, i * 4);
  const out = new Uint8Array(book.size);
  let p = 0;
  let s = book.start;
  let guard2 = 0;
  while (s >= 0 && guard2++ <= miniFat.length && p < book.size) {
    const part = miniStream.subarray(s * miniSectorSize, Math.min((s + 1) * miniSectorSize, miniStream.length));
    out.set(part.subarray(0, Math.min(part.length, book.size - p)), p);
    p += part.length;
    s = s < miniFat.length ? miniFat[s] : END_OF_CHAIN;
  }
  return out;
}

interface XlsRecord {
  type: number;
  data: Uint8Array;
}

function* records(stream: Uint8Array, from = 0): Generator<XlsRecord> {
  let p = from;
  while (p + 4 <= stream.length) {
    const type = u16(stream, p);
    const len = u16(stream, p + 2);
    if (p + 4 + len > stream.length) break;
    yield { type, data: stream.subarray(p + 4, p + 4 + len) };
    p += 4 + len;
  }
}

function sharedStrings(parts: Uint8Array[]): string[] {
  if (parts.length === 0) return [];
  const total = parts.reduce((n, b) => n + b.length, 0);
  const data = new Uint8Array(total);
  const boundaries = new Set<number>();
  let off = 0;
  for (const b of parts) {
    data.set(b, off);
    off += b.length;
    boundaries.add(off);
  }
  let p = 8;
  const unique = i32(data, 4);
  const out: string[] = [];
  while (out.length < unique && p + 3 <= data.length) {
    const cch = u16(data, p);
    let flags = data[p + 2];
    p += 3;
    let runs = 0;
    let ext = 0;
    if (flags & 0x08) {
      runs = u16(data, p);
      p += 2;
    }
    if (flags & 0x04) {
      ext = i32(data, p);
      p += 4;
    }
    let sb = "";
    for (let i = 0; i < cch; i++) {
      // A string cut by a CONTINUE record restarts with a fresh flags byte.
      if (boundaries.has(p)) {
        flags = data[p];
        p += 1;
      }
      if (flags & 0x01) {
        sb += String.fromCharCode(u16(data, p));
        p += 2;
      } else {
        sb += String.fromCharCode(data[p]);
        p += 1;
      }
    }
    p += runs * 4 + ext;
    out.push(sb);
  }
  return out;
}

function unicodeString(d: Uint8Array, at: number): string {
  const cch = u16(d, at);
  const flags = d[at + 2];
  let p = at + 3;
  if (flags & 0x08) p += 2;
  if (flags & 0x04) p += 4;
  if (flags & 0x01) return new TextDecoder("utf-16le").decode(d.subarray(p, p + cch * 2));
  return new TextDecoder("latin1").decode(d.subarray(p, p + cch));
}

function float64(d: Uint8Array, at: number): number {
  return new DataView(d.buffer, d.byteOffset + at, 8).getFloat64(0, true);
}

function rk(v: number): number {
  let n: number;
  if (v & 0x02) n = v >> 2;
  else {
    const buf = new DataView(new ArrayBuffer(8));
    buf.setInt32(4, v & ~3, true);
    buf.setInt32(0, 0, true);
    n = buf.getFloat64(0, true);
  }
  return v & 0x01 ? n / 100 : n;
}

function xlsCells(stream: Uint8Array): Map<string, string> {
  const sstParts: Uint8Array[] = [];
  let sheetOffset: number | null = null;
  let inSst = false;
  for (const r of records(stream)) {
    if (r.type === 0x00fc) {
      sstParts.push(r.data);
      inSst = true;
    } else if (r.type === 0x003c) {
      if (inSst) sstParts.push(r.data);
    } else if (r.type === 0x0085) {
      inSst = false;
      if (sheetOffset === null && r.data.length >= 6 && r.data[5] === 0) sheetOffset = i32(r.data, 0);
    } else if (r.type === 0x000a) break;
    else inSst = false;
  }
  const sst = sharedStrings(sstParts);
  if (sheetOffset === null) throw new Unreadable("The Excel file has no sheet.");
  const cells = new Map<string, string>();
  const key = (d: Uint8Array) => `${u16(d, 0)},${u16(d, 2)}`;
  let pendingFormula: string | null = null;
  let first = true;
  for (const r of records(stream, sheetOffset)) {
    if (first) {
      first = false; // the sheet's own BOF
      continue;
    }
    const d = r.data;
    switch (r.type) {
      case 0x000a:
        return cells;
      case 0x00fd: {
        const s = sst[i32(d, 6)];
        if (s !== undefined) cells.set(key(d), s);
        break;
      }
      case 0x0203:
        cells.set(key(d), numberText(float64(d, 6)));
        break;
      case 0x027e:
        cells.set(key(d), numberText(rk(i32(d, 6))));
        break;
      case 0x00bd: {
        const row = u16(d, 0);
        const col = u16(d, 2);
        const n = Math.floor((d.length - 6) / 6);
        for (let k = 0; k < n; k++) cells.set(`${row},${col + k}`, numberText(rk(i32(d, 4 + k * 6 + 2))));
        break;
      }
      case 0x0204:
        cells.set(key(d), unicodeString(d, 6));
        break;
      case 0x0205:
        if (d[7] === 0) cells.set(key(d), d[6] !== 0 ? "TRUE" : "FALSE");
        break;
      case 0x0006:
        if (u16(d, 12) === 0xffff) {
          if (d[6] === 0) pendingFormula = key(d);
          else if (d[6] === 1) cells.set(key(d), d[8] !== 0 ? "TRUE" : "FALSE");
        } else cells.set(key(d), numberText(float64(d, 6)));
        break;
      case 0x0207:
        if (pendingFormula !== null) {
          cells.set(pendingFormula, unicodeString(d, 0));
          pendingFormula = null;
        }
        break;
    }
  }
  return cells;
}

// ---- Excel 2007+: a zip of XML files ----

function unzip(bytes: Uint8Array): Map<string, Uint8Array> {
  const files = new Map<string, Uint8Array>();
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  // The end of central directory record: the last "PK\5\6".
  let eocd = -1;
  for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65_557); i--) {
    if (view.getUint32(i, true) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new Unreadable("The Excel file is damaged.");
  const count = view.getUint16(eocd + 10, true);
  let p = view.getUint32(eocd + 16, true);
  for (let n = 0; n < count && p + 46 <= bytes.length; n++) {
    if (view.getUint32(p, true) !== 0x02014b50) break;
    const method = view.getUint16(p + 10, true);
    const compSize = view.getUint32(p + 20, true);
    const nameLen = view.getUint16(p + 28, true);
    const extraLen = view.getUint16(p + 30, true);
    const commentLen = view.getUint16(p + 32, true);
    const local = view.getUint32(p + 42, true);
    const name = new TextDecoder().decode(bytes.subarray(p + 46, p + 46 + nameLen)).replace(/^\//, "");
    p += 46 + nameLen + extraLen + commentLen;
    if (!name.endsWith(".xml") && !name.endsWith(".rels")) continue;
    const lName = view.getUint16(local + 26, true);
    const lExtra = view.getUint16(local + 28, true);
    const start = local + 30 + lName + lExtra;
    const raw = bytes.subarray(start, start + compSize);
    if (method === 0) files.set(name, raw);
    else if (method === 8) files.set(name, new Uint8Array(inflateRawSync(raw)));
  }
  return files;
}

function unescapeXml(s: string): string {
  return s
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(Number.parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, "&");
}

/** The visible text of a shared or inline string: its <t> parts, without phonetic hints. */
function xmlText(xml: string): string {
  const clean = xml.replace(/<rPh\b[\s\S]*?<\/rPh>/g, "");
  let out = "";
  for (const m of clean.matchAll(/<t(?:\s[^>]*)?>([\s\S]*?)<\/t>/g)) out += unescapeXml(m[1]);
  return out;
}

function attr(tag: string, name: string): string | null {
  const m = new RegExp(`\\s${name.replace(":", "\\:")}="([^"]*)"`).exec(tag);
  return m ? unescapeXml(m[1]) : null;
}

function position(ref: string): [number, number] | null {
  const m = /^([A-Za-z]+)(\d+)$/.exec(ref);
  if (!m) return null;
  const col = [...m[1].toUpperCase()].reduce((acc, ch) => acc * 26 + (ch.charCodeAt(0) - 64), 0) - 1;
  return [Number(m[2]) - 1, col];
}

function readXlsx(bytes: Uint8Array): string[][] {
  const files = unzip(bytes);
  const text = (n: string) => {
    const b = files.get(n);
    return b ? new TextDecoder().decode(b) : null;
  };
  const sharedXml = text("xl/sharedStrings.xml");
  const shared = sharedXml ? [...sharedXml.matchAll(/<si>([\s\S]*?)<\/si>/g)].map((m) => xmlText(m[1])) : [];
  let sheet: string | null = null;
  const workbook = text("xl/workbook.xml");
  const rels = text("xl/_rels/workbook.xml.rels");
  const firstTag = workbook ? /<sheet\b[^>]*>/.exec(workbook)?.[0] : undefined;
  const firstId = firstTag ? attr(firstTag, "r:id") : null;
  if (rels && firstId) {
    for (const m of rels.matchAll(/<Relationship\b[^>]*>/g)) {
      if (attr(m[0], "Id") === firstId) {
        const t = attr(m[0], "Target") ?? "";
        sheet = text(t.startsWith("/") ? t.slice(1) : `xl/${t}`);
      }
    }
  }
  if (sheet === null) {
    const name = [...files.keys()].filter((k) => k.startsWith("xl/worksheets/") && k.endsWith(".xml")).sort()[0];
    sheet = name ? text(name) : null;
  }
  if (sheet === null) throw new Unreadable("The Excel file has no sheet.");
  const cells = new Map<string, string>();
  for (const m of sheet.matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
    const tag = `<c${m[1]}>`;
    const pos = position(attr(tag, "r") ?? "");
    if (!pos) continue;
    const body = m[2] ?? "";
    const vm = /<v>([\s\S]*?)<\/v>/.exec(body);
    const v = vm ? unescapeXml(vm[1]) : undefined;
    let out: string;
    switch (attr(tag, "t")) {
      case "s":
        out = shared[Number.parseInt(v?.trim() ?? "", 10)] ?? "";
        break;
      case "inlineStr": {
        const is = /<is>([\s\S]*?)<\/is>/.exec(body);
        out = is ? xmlText(is[1]) : "";
        break;
      }
      case "b":
        out = v === "1" ? "TRUE" : "FALSE";
        break;
      case "str":
      case "e":
        out = v ?? "";
        break;
      default: {
        const n = v !== undefined && v.trim() !== "" ? Number(v) : NaN;
        out = Number.isFinite(n) ? numberText(n) : (v ?? "");
      }
    }
    cells.set(`${pos[0]},${pos[1]}`, out);
  }
  return grid(cells);
}
