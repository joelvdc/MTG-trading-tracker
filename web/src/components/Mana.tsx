"use client";

/**
 * Mana symbols as the Android app draws them (ui/ManaSymbols.kt): the Mana font by Andrew Gioia (SIL Open Font License,
 * public/fonts) on a round tinted background, hybrids split corner to corner; a symbol the font has no character for
 * ({T}, {Q}, {E}…) is written out.
 */
const TINT: Record<string, string> = { W: "#f0f2c0", U: "#b5cde3", B: "#aca29a", R: "#db8664", G: "#93b483" };
const GENERIC_TINT = "#beb9b2";
const INK = "#111";

/** The font's character for a symbol, or null when it has none (the code points are the app's `glyph()`). */
function glyph(s: string): string | null {
  const fixed: Record<string, number> = { W: 0xe600, U: 0xe601, B: 0xe602, R: 0xe603, G: 0xe604, C: 0xe904, X: 0xe615, P: 0xe618, S: 0xe619 };
  if (s in fixed) return String.fromCharCode(fixed[s]);
  if (/^\d+$/.test(s) && Number(s) <= 10) return String.fromCharCode(0xe605 + Number(s));
  return null;
}

/** One symbol's text: the font's character, or the symbol written out in bold. */
function Glyph({ s, size, style }: { s: string; size: number; style?: React.CSSProperties }) {
  const g = glyph(s);
  return g ? (
    <span className="mana-glyph" style={{ fontSize: size, ...style }}>
      {g}
    </span>
  ) : (
    <span className="mana-glyph text" style={{ fontSize: size * 0.8, ...style }}>
      {s}
    </span>
  );
}

function Pip({ s, size }: { s: string; size: number }) {
  const up = s.toUpperCase();
  const parts = up.split("/").filter((p) => p !== "");
  const colours = parts.filter((p) => p !== "P");
  const tint = (p: string) => TINT[p] ?? GENERIC_TINT;
  const big = size * 0.68;
  const small = size * 0.44;
  let body: React.ReactNode;
  let background: string;
  if (parts.length >= 2 && parts[parts.length - 1] === "P" && colours.length === 1) {
    // Phyrexian: the Phyrexian mark on the colour's circle.
    background = tint(colours[0]);
    body = <Glyph s="P" size={big} />;
  } else if (colours.length >= 2) {
    // Hybrid: the circle split corner to corner, a small symbol in each half.
    background = `linear-gradient(to bottom right, ${tint(colours[0])} 50%, ${tint(colours[1])} 50%)`;
    body = (
      <>
        <Glyph s={colours[0]} size={small} style={{ position: "absolute", left: size * 0.12, top: size * 0.02 }} />
        <Glyph s={colours[1]} size={small} style={{ position: "absolute", right: size * 0.12, bottom: size * 0.02 }} />
      </>
    );
  } else {
    background = tint(up);
    body = <Glyph s={up} size={glyph(up) == null && up.length > 1 ? small : big} />;
  }
  return (
    <span className="pip" title={`{${s}}`} style={{ width: size, height: size, background, color: INK }}>
      {body}
    </span>
  );
}

/** A mana cost like "{2}{B}{G/U}" as round symbols; faces split by " // ". */
export function ManaCost({ cost, size = 16 }: { cost: string | null | undefined; size?: number }) {
  if (!cost) return null;
  const faces = cost.split(" // ").filter((f) => f.trim() !== "");
  return (
    <span className="mana" aria-label={cost}>
      {faces.map((f, i) => (
        <span key={i} className="mana">
          {i > 0 && <span className="muted small"> // </span>}
          {[...f.matchAll(/\{([^}]+)\}/g)].map((m, j) => (
            <Pip key={j} s={m[1]} size={size} />
          ))}
        </span>
      ))}
    </span>
  );
}

/** Rules text with its {T}, {2}, {W/U} symbols drawn as round symbols; line breaks kept. */
export function SymbolText({ text, size = 14 }: { text: string; size?: number }) {
  return (
    <span className="symbol-text">
      {text.split(/(\{[^}]+\})/).map((part, i) => {
        const m = /^\{([^}]+)\}$/.exec(part);
        return m ? <Pip key={i} s={m[1]} size={size} /> : <span key={i}>{part}</span>;
      })}
    </span>
  );
}

/** A colour identity ("BRG") as mana symbols; colourless shows {C}. */
export function ColourPips({ identity, size = 14 }: { identity: string; size?: number }) {
  const list = identity ? [...identity] : ["C"];
  return (
    <span className="mana" aria-label={identity || "colourless"}>
      {list.map((c) => (
        <Pip key={c} s={c} size={size} />
      ))}
    </span>
  );
}

export const COLOUR_NAMES: Record<string, string> = { W: "White", U: "Blue", B: "Black", R: "Red", G: "Green", C: "Colourless" };
export const COLOUR_FILL: Record<string, string> = { W: "#e9e2b8", U: "#5aa7dc", B: "#8f8580", R: "#e5714a", G: "#4fa565", C: "#b4aca7" };
