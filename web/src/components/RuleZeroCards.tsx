"use client";

import { useEffect, useId, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { bracketName, bracketReasons, comboData, tagName, type Deck, type SaltCard } from "@/lib/deckshared";
import type { RoleCount } from "@/lib/deckroles";
import type { SaltSum } from "@/lib/localratings";
import { winWindow, type WinResult } from "@/lib/winodds";

// The bracket card, drawn as the app draws it (CardSheets.kt): the size of a Magic card, 63 × 88 mm, every measure in
// millimetres, so the screen and the printed A4 sheets (3 × 3, cut marks) match the app's. Keep the two in step.

/** Fixed tile colours per bracket (light tints with dark text), as the app draws them. */
export const BRACKET_COLORS: Record<number, [string, string]> = {
  1: ["#E1F5EE", "#085041"],
  2: ["#EAF3DE", "#27500A"],
  3: ["#FAEEDA", "#633806"],
  4: ["#FAECE7", "#712B13"],
  5: ["#FCEBEB", "#791F1F"],
  0: ["#F1EFE8", "#444441"],
};

/** The salt tile's colours: Mild like bracket 2, Moderate like 3, High like 4. */
export function saltColors(level: string | null | undefined): [string, string] {
  return BRACKET_COLORS[level === "Mild" ? 2 : level === "Moderate" ? 3 : level === "High" ? 4 : 0];
}

/** What one bracket card shows. */
export interface BracketCardData {
  deck: Pick<Deck, "name" | "commanders" | "colorIdentity" | "owner" | "scoredAt">;
  cover: string | null;
  /** The website's estimate; null before the deck was rated. */
  data: SaltCard | null;
  win: WinResult | null;
  saltSum: SaltSum | null;
  roles: RoleCount[];
}

export const CARD_W = 63;
export const CARD_H = 88;
const PAGE_W = 210;
const PAGE_H = 297;
const COLS = 3;
export const PER_PAGE = 9;
const LEFT = (PAGE_W - COLS * CARD_W) / 2;
const TOP = (PAGE_H - 3 * CARD_H) / 2;

const WHITE = "#FFFFFF";
const INK = "#1C1B1F";
const GREY = "#5F5C66";
const LINE = "#D6D3DC";
const MARK = "#888888";
const BAR = "#7F77DD";
const GOLD = "#9A6A00";
const GREEN = "#1E7A52";
const AMBER = "#BA7517";
const PIPS: Record<string, string> = { W: "#F8F1D6", U: "#3F8FD2", B: "#3B3434", R: "#D9503F", G: "#3E9A5B" };

const PAD = 3;
const INNER = CARD_W - 2 * PAD;
const BODY_PT = 6.2;
const BODY_LH = 7.1;
const LABEL_PT = 5.2;
const CREDIT_LH = 2.1;
const FONT = 'Roboto, "Helvetica Neue", Arial, sans-serif';
const CREDIT = ["Data: Scryfall, EDHREC, Commander Spellbook"];

/** The roles the card shows, in its order. */
const CARD_ROLES = ["Lands", "Ramp", "Draw", "Removal", "Wipes", "Tutor"];

type Weight = 400 | 500 | 700;
type Align = "start" | "middle" | "end";

const mm = (pt: number) => (pt * 25.4) / 72;

let measureCtx: CanvasRenderingContext2D | null | undefined;

/** A text's width in mm (an even guess where there's no canvas, as on the server). */
function textWidth(s: string, pt: number, weight: Weight = 400, spacing = 0): number {
  if (measureCtx === undefined) measureCtx = typeof document === "undefined" ? null : document.createElement("canvas").getContext("2d");
  const size = mm(pt);
  if (!measureCtx) return s.length * size * 0.52 + s.length * spacing * size;
  measureCtx.font = `${weight} 100px ${FONT}`;
  return (measureCtx.measureText(s).width / 100) * size + s.length * spacing * size;
}

/** [s] shortened with "…" to [max] mm. */
function ellipsize(s: string, pt: number, max: number, weight: Weight = 400): string {
  if (textWidth(s, pt, weight) <= max) return s;
  let lo = 0;
  let hi = s.length;
  while (lo < hi) {
    const mid = Math.ceil((lo + hi) / 2);
    if (textWidth(s.slice(0, mid).trimEnd() + "…", pt, weight) <= max) lo = mid;
    else hi = mid - 1;
  }
  return s.slice(0, lo).trimEnd() + "…";
}

/** [s] in lines of at most [width] mm, at most [maxLines], the last one shortened with "…" when there's more. */
function wrapLines(s: string, pt: number, width: number, maxLines: number): string[] {
  const words = s.split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  let line = "";
  let i = 0;
  for (; i < words.length; i++) {
    const next = line ? `${line} ${words[i]}` : words[i];
    if (!line || textWidth(next, pt) <= width) {
      line = next;
      continue;
    }
    if (lines.length === maxLines - 1) break;
    lines.push(line);
    line = words[i];
  }
  // What's left goes on the last line, shortened when it doesn't fit.
  const rest = [line, ...words.slice(i)].filter(Boolean).join(" ");
  if (rest) lines.push(rest);
  return lines.map((l) => ellipsize(l, pt, width));
}

/** "1.6": a card's salt, as the app shows it. */
const saltText = (v: number) => (Math.round(v * 10) / 10).toFixed(1);

/** The card's drawing, top to bottom, as SVG in mm (BracketPrint in CardSheets.kt). */
class CardDrawing {
  out: ReactNode[] = [];
  y = 0;
  private k = 0;

  constructor(
    private card: BracketCardData,
    private ink: boolean,
  ) {}

  private key() {
    return this.k++;
  }

  fill(x: number, y: number, w: number, h: number, colour: string, r = 0, opacity = 1) {
    this.out.push(<rect key={this.key()} x={x} y={y} width={w} height={h} rx={r || undefined} fill={colour} fillOpacity={opacity < 1 ? opacity : undefined} />);
  }

  frame(x: number, y: number, w: number, h: number, colour: string, stroke: number, r = 0) {
    this.out.push(<rect key={this.key()} x={x} y={y} width={w} height={h} rx={r || undefined} fill="none" stroke={colour} strokeWidth={stroke} />);
  }

  line(x1: number, y1: number, x2: number, y2: number, colour: string, stroke: number) {
    this.out.push(<line key={this.key()} x1={x1} y1={y1} x2={x2} y2={y2} stroke={colour} strokeWidth={stroke} />);
  }

  /** One line of text on [baseline]; shortened with "…" to [max] mm. Returns its width in mm. */
  text(s: string, x: number, baseline: number, pt: number, colour: string, weight: Weight = 400, align: Align = "start", max?: number, spacing = 0): number {
    const shown = max !== undefined ? ellipsize(s, pt, max, weight) : s;
    this.out.push(
      <text key={this.key()} x={x} y={baseline} fontSize={mm(pt)} fill={colour} fontWeight={weight} textAnchor={align} letterSpacing={spacing ? spacing * mm(pt) : undefined}>
        {shown}
      </text>,
    );
    return textWidth(shown, pt, weight, spacing);
  }

  /** Text wrapped to [width] mm in at most [maxLines] lines from [top]; returns the height used. */
  wrappedAt(s: string, x: number, top: number, width: number, colour: string, maxLines: number): number {
    const lh = mm(BODY_LH);
    const lines = wrapLines(s, BODY_PT, width, maxLines);
    lines.forEach((l, i) => this.text(l, x, top + lh * (i + 1) - lh * 0.25, BODY_PT, colour));
    return lh * lines.length;
  }

  /** The room left above the footer, in mm. */
  get room() {
    return CARD_H - 4.5 - (CREDIT.length - 1) * CREDIT_LH - this.y;
  }

  rule() {
    this.y += 0.9;
    this.line(PAD, this.y, CARD_W - PAD, this.y, LINE, 0.15);
    this.y += 0.9;
  }

  label(s: string) {
    this.y += mm(6.4);
    this.text(s.toUpperCase(), PAD, this.y - 0.45, LABEL_PT, GREY, 500, "start", undefined, 0.04);
  }

  bodyLine(s: string, x = PAD, width = INNER, colour = INK, weight: Weight = 400) {
    this.text(s, x, this.y + mm(BODY_LH) - 0.55, BODY_PT, colour, weight, "start", width);
  }

  newLine() {
    this.y += mm(BODY_LH);
  }

  wrapped(s: string, maxLines: number) {
    this.y += this.wrappedAt(s, PAD, this.y, INNER, INK, maxLines);
  }

  draw(): ReactNode[] {
    this.fill(0, 0, CARD_W, CARD_H, WHITE);
    this.header();
    const data = this.card.data;
    if (!data) this.text("Not rated yet", PAD, this.y + 3, BODY_PT, GREY, 400, "start", INNER);
    else this.body(data);
    this.footer();
    return this.out;
  }

  private header() {
    const { deck, cover } = this.card;
    let kindColour: string;
    let nameColour: string;
    let subColour: string;
    let bottom: number;
    if (this.ink) {
      this.fill(0, 0, CARD_W, 1.2, BAR);
      kindColour = "#534AB7";
      nameColour = INK;
      subColour = GREY;
      bottom = 11.6;
    } else {
      const h = 16;
      this.fill(0, 0, CARD_W, h, "#2E2766");
      if (cover) this.out.push(<image key={this.key()} href={cover} x={0} y={0} width={CARD_W} height={h} preserveAspectRatio="xMidYMid slice" />);
      this.fill(0, 0, CARD_W, h, "#26215C", 0, 0.8);
      kindColour = "#AFA9EC";
      nameColour = WHITE;
      subColour = "#CECBF6";
      bottom = h - 1.6;
    }
    const commanderBase = bottom - 0.5;
    const nameBase = commanderBase - 3.1;
    const kindBase = nameBase - 3.9;
    this.text("BRACKET", PAD, kindBase, 5.5, kindColour, 500, "start", undefined, 0.05);
    this.text(deck.name, PAD, nameBase, 10, nameColour, 500, "start", INNER);
    const pips = [...(deck.colorIdentity ?? "")].map((c) => PIPS[c]).filter(Boolean);
    const shown = pips.length ? pips : ["#B9B4B0"];
    const r = 1.05;
    const pipsWidth = shown.length * 2 * r + (shown.length - 1) * 0.5;
    const w = this.text(deck.commanders, PAD, commanderBase, 6.5, subColour, 400, "start", INNER - pipsWidth - 1.5);
    let cx = PAD + w + 1.5 + r;
    for (const colour of shown) {
      this.out.push(<circle key={this.key()} cx={cx} cy={commanderBase - 0.75} r={r} fill={colour} />);
      cx += 2 * r + 0.5;
    }
    if (this.ink) {
      this.line(PAD, 13, CARD_W - PAD, 13, LINE, 0.2);
      this.y = 14.2;
    } else {
      this.y = 17.5;
    }
  }

  private footer() {
    const base = CARD_H - 1.8;
    [...CREDIT].reverse().forEach((line, i) => this.text(line, PAD, base - i * CREDIT_LH, 4.8, GREY, 400, "start", INNER * 0.6));
    const { deck } = this.card;
    const date = deck.scoredAt ? new Date(deck.scoredAt).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" }) : null;
    const by = [deck.owner || null, date].filter(Boolean).join(" · ");
    this.text(by, CARD_W - PAD, base, 4.8, GREY, 400, "end");
  }

  private body(data: SaltCard) {
    this.tiles(data);
    const bracket = data.bracket ?? null;
    // Why the bracket, in one or two lines; the Game Changers have their own block below.
    const why = bracketReasons(data, bracket).filter((l) => !(l.includes("Game Changer") && data.gameChangers.length > 0));
    if (why.length) {
      this.rule();
      this.label(`Why bracket ${bracket}`);
      for (const l of why.slice(0, 2)) this.y += this.wrappedAt(l, PAD, this.y, INNER, INK, 1);
    }
    if (this.card.win) {
      this.rule();
      this.speed(this.card.win);
    }
    this.rule();
    this.criteria(data);
    if (this.card.roles.length) {
      this.rule();
      this.rolesBlock(this.card.roles);
    }
    if (data.gameChangers.length) {
      this.rule();
      this.label("Game changers");
      this.wrapped(data.gameChangers.join(", "), 2);
    }
    const seen = new Set<string>();
    const combos = [...data.twoCardCombos, ...data.earlyCombos].filter((c) => {
      const k = c.join("\u0001");
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    });
    if (combos.length && this.room > 6) {
      this.rule();
      this.label("Rule zero: combos");
      const lh = mm(BODY_LH);
      const fit = Math.max(1, Math.floor((this.room - 0.5) / lh));
      const shown = combos.length <= fit ? combos : combos.slice(0, Math.max(1, fit - 1));
      for (const c of shown) {
        // The bracket tag in words after the pieces, when Commander Spellbook gave the combo one.
        const name = tagName(comboData(data, c)?.bracketTag);
        this.bodyLine(name ? `${c.join(" + ")} · ${name}` : c.join(" + "));
        this.newLine();
      }
      if (shown.length < combos.length) {
        this.bodyLine(`+${combos.length - shown.length} more`, PAD, INNER, GREY);
        this.newLine();
      }
    }
    // The saltiest cards, when there's room left for their two lines.
    const salty = this.card.saltSum?.saltiest ?? [];
    if (salty.length && this.room > 9) {
      this.rule();
      this.label("Saltiest cards");
      this.wrapped(salty.map((c) => `${c.name} ${saltText(c.salt)}`).join(", "), 2);
    }
  }

  /** Three tiles: the bracket (with its name), the salt sum (a whole number, coloured by its level) and the win turn. */
  private tiles(data: SaltCard) {
    const gap = 1.5;
    const w = (INNER - 2 * gap) / 3;
    const h = 9.8;
    const tile = (i: number, bg: string, fg: string, label: string, big: string, bigColour = fg) => {
      const x = PAD + i * (w + gap);
      this.fill(x, this.y, w, h, bg, 1.5);
      const cx = x + w / 2;
      this.text(label, cx, this.y + 2.9, 5, fg, 400, "middle", w - 1);
      this.text(big, cx, this.y + 8.15, 13, bigColour, 700, "middle");
    };
    const b = data.bracket ?? null;
    const [bg, fg] = BRACKET_COLORS[b ?? 0] ?? BRACKET_COLORS[0];
    tile(0, bg, fg, ["Bracket", bracketName(b)].filter(Boolean).join(" · "), b != null ? String(b) : "—");
    const sum = this.card.saltSum?.total != null ? this.card.saltSum : null;
    const [sbg, sfg] = saltColors(sum?.level);
    tile(1, sbg, sfg, sum?.level ? `Salt · ${sum.level.toLowerCase()}` : "Salt", sum?.total != null ? String(Math.round(sum.total)) : "—");
    // The turn 4 in 10 games are won by, in gold like its bar below.
    tile(2, "#F1EFF6", GREY, "Wins by", this.card.win?.label ?? "—", GOLD);
    this.y += h;
  }

  /** The speed bars: % of games won by each turn, the first win (green) and 4 in 10 games (gold) outlined. */
  private speed(win: WinResult) {
    this.label("Speed · % of games won by each turn");
    this.y += 0.5;
    const turns = winWindow(win);
    const gap = 0.5;
    const w = (INNER - gap * (turns.length - 1)) / turns.length;
    const h = 3.6;
    turns.forEach((t, i) => {
      const v = win.odds.byTurn[t - 1] ?? 0;
      const mark = t === win.fourInTen ? GOLD : t === win.firstWin ? GREEN : null;
      const x = PAD + i * (w + gap);
      const alpha = this.ink ? Math.min(0.35, Math.max(0.05, (v / 100) * 0.35)) : Math.min(1, Math.max(0.1, v / 100));
      this.fill(x, this.y, w, h, BAR, 0.6, alpha);
      if (mark) this.frame(x + 0.15, this.y + 0.15, w - 0.3, h - 0.3, mark, 0.3, 0.5);
      this.text(String(v), x + w / 2, this.y + h / 2 + 0.65, 5.2, !this.ink && v > 55 ? WHITE : INK, 400, "middle");
      this.text(`T${t}`, x + w / 2, this.y + h + 1.9, 4.8, mark ?? GREY, mark ? 700 : 400, "middle");
    });
    this.y += h + 2.4;
  }

  private criteria(data: SaltCard) {
    this.label("Bracket criteria");
    const rows: [string, number][] = (
      [
        ["Game changers", data.gameChangers.length],
        ["Combos", data.twoCardCombos.length],
        ["Extra turns", data.extraTurns.length],
        ["Land denial", data.massLandDenial.length],
      ] as [string, number][]
    ).sort((a, b) => Number(b[1] > 0) - Number(a[1] > 0));
    const colW = (INNER - 3) / 2;
    for (let r = 0; r < rows.length; r += 2) {
      rows.slice(r, r + 2).forEach(([label, n], i) => {
        const x = PAD + i * (colW + 3);
        const hit = n > 0;
        this.bodyLine((hit ? "▲ " : "✓ ") + label, x, colW - 3, hit ? INK : GREY);
        this.text(String(n), x + colW, this.y + mm(BODY_LH) - 0.55, BODY_PT, hit ? AMBER : GREY, hit ? 500 : 400, "end");
      });
      this.newLine();
    }
  }

  /** The deck's roles in three columns: lands (+ MDFCs), ramp, draw, then removal, wipes, tutors; outside the guideline in amber. */
  private rolesBlock(all: RoleCount[]) {
    this.label("Deck");
    const byName = new Map(all.map((r) => [r.role, r]));
    const shown = CARD_ROLES.map((role) => byName.get(role) ?? { role, count: 0, status: "none" as const, extra: 0 });
    const colW = (INNER - 2 * 3) / 3;
    for (let r = 0; r < shown.length; r += 3) {
      shown.slice(r, r + 3).forEach((item, i) => {
        const x = PAD + i * (colW + 3);
        const off = item.status === "low" || item.status === "high";
        this.bodyLine(item.role === "Tutor" ? "Tutors" : item.role, x, colW - 5, INK);
        const value = `${item.count}${item.extra > 0 ? `+${item.extra}` : ""}`;
        this.text(value, x + colW, this.y + mm(BODY_LH) - 0.55, BODY_PT, off ? AMBER : INK, off ? 500 : 400, "end");
      });
      this.newLine();
    }
  }
}

/** One bracket card, as wide as its box on screen (CSS), 63 × 88 mm when printed. */
export function BracketCardSvg({ card, inkSaver = false, className }: { card: BracketCardData; inkSaver?: boolean; className?: string }) {
  const clip = useId();
  return (
    <svg className={className ?? "rz-card-svg"} viewBox={`0 0 ${CARD_W} ${CARD_H}`} fontFamily={FONT} role="img" aria-label={`Bracket card for ${card.deck.name}`}>
      <defs>
        <clipPath id={clip}>
          <rect width={CARD_W} height={CARD_H} rx={2.5} />
        </clipPath>
      </defs>
      <g clipPath={`url(#${clip})`}>{new CardDrawing(card, inkSaver).draw()}</g>
    </svg>
  );
}

/** Short lines in the margins on the lines between the cards (only where there are cards). */
function cutMarks(count: number): ReactNode[] {
  if (count === 0) return [];
  const rows = Math.ceil(Math.min(count, PER_PAGE) / COLS);
  const cols = Math.min(count, COLS);
  const xs = Array.from({ length: cols + 1 }, (_, i) => LEFT + i * CARD_W);
  const ys = Array.from({ length: rows + 1 }, (_, i) => TOP + i * CARD_H);
  // Each mark 6.5 mm long, 1.5 mm off the cards, so they stay clear of the footer.
  const gap = 1.5;
  const len = 6.5;
  const out: ReactNode[] = [];
  const mark = (x1: number, y1: number, x2: number, y2: number) => out.push(<line key={out.length} x1={x1} y1={y1} x2={x2} y2={y2} stroke={MARK} strokeWidth={0.15} />);
  for (const x of xs) {
    mark(x, ys[0] - gap - len, x, ys[0] - gap);
    mark(x, ys[ys.length - 1] + gap, x, ys[ys.length - 1] + gap + len);
  }
  for (const y of ys) {
    mark(xs[0] - gap - len, y, xs[0] - gap, y);
    mark(xs[xs.length - 1] + gap, y, xs[xs.length - 1] + gap + len, y);
  }
  return out;
}

/**
 * The sheets to print, straight under <body> and hidden on screen: while the browser prints, body.printing-cards hides
 * the rest of the page and the sheets fill A4 pages without margins (globals.css). Mount it only where Print means the
 * cards.
 */
export function BracketPrintRoot({ cards, inkSaver }: { cards: BracketCardData[]; inkSaver: boolean }) {
  const [body, setBody] = useState<HTMLElement | null>(null);
  useEffect(() => {
    setBody(document.body);
    const before = () => document.body.classList.add("printing-cards");
    const after = () => document.body.classList.remove("printing-cards");
    window.addEventListener("beforeprint", before);
    window.addEventListener("afterprint", after);
    return () => {
      window.removeEventListener("beforeprint", before);
      window.removeEventListener("afterprint", after);
      after();
    };
  }, []);
  return body
    ? createPortal(
        <div className="rz-print-root">
          <BracketSheets cards={cards} inkSaver={inkSaver} />
        </div>,
        body,
      )
    : null;
}

/** The cards as A4 pages to print and cut, nine to a page, touching, with cut marks (the app's PDF). */
export function BracketSheets({ cards, inkSaver }: { cards: BracketCardData[]; inkSaver: boolean }) {
  const pages: BracketCardData[][] = [];
  for (let i = 0; i < cards.length; i += PER_PAGE) pages.push(cards.slice(i, i + PER_PAGE));
  return (
    <div className="rz-sheets">
      {pages.map((page, p) => (
        <svg key={p} className="rz-sheet" viewBox={`0 0 ${PAGE_W} ${PAGE_H}`} fontFamily={FONT}>
          <rect width={PAGE_W} height={PAGE_H} fill={WHITE} />
          {cutMarks(page.length)}
          {page.map((card, i) => {
            const x = LEFT + (i % COLS) * CARD_W;
            const y = TOP + Math.floor(i / COLS) * CARD_H;
            return (
              <g key={i}>
                <svg x={x} y={y} width={CARD_W} height={CARD_H} viewBox={`0 0 ${CARD_W} ${CARD_H}`} overflow="hidden">
                  {new CardDrawing(card, inkSaver).draw()}
                </svg>
                <rect x={x} y={y} width={CARD_W} height={CARD_H} fill="none" stroke={LINE} strokeWidth={0.15} />
              </g>
            );
          })}
          <text x={PAGE_W / 2} y={PAGE_H - 5} fontSize={mm(6.5)} fill={GREY} textAnchor="middle">
            {`MTG Trader · Bracket cards · page ${p + 1} of ${pages.length} · print at 100% (actual size)`}
          </text>
        </svg>
      ))}
    </div>
  );
}
