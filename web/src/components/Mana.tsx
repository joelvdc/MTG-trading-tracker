"use client";

/** Colours of the mana symbols (close to the printed ones), drawn here rather than loaded from elsewhere. */
const PIP: Record<string, [string, string]> = {
  W: ["#f8f4d8", "#3b3420"],
  U: ["#aad3ef", "#0d2e4a"],
  B: ["#c9c1bd", "#211b1b"],
  R: ["#f3a183", "#4a160a"],
  G: ["#9fd3a8", "#0f3a1c"],
  C: ["#d6d0cc", "#333"],
};

function Pip({ s, size }: { s: string; size: number }) {
  const parts = s.toUpperCase().split("/");
  const colours = parts.filter((p) => p in PIP);
  const [bg, fg] = PIP[colours[0] ?? "C"] ?? PIP.C;
  const bg2 = colours[1] ? PIP[colours[1]][0] : null;
  const up = s.toUpperCase();
  const text = parts.includes("P") ? "φ" : colours.length === parts.length && up !== "C" ? "" : (parts.find((p) => !(p in PIP)) ?? up);
  return (
    <span
      className="pip"
      title={`{${s}}`}
      style={{
        width: size,
        height: size,
        fontSize: size * 0.6,
        background: bg2 ? `linear-gradient(135deg, ${bg} 50%, ${bg2} 50%)` : bg,
        color: fg,
      }}
    >
      {text.slice(0, 2)}
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

/** A colour identity ("BRG") as coloured dots; colourless shows {C}. */
export function ColourPips({ identity, size = 14 }: { identity: string; size?: number }) {
  const list = identity ? [...identity] : ["C"];
  return (
    <span className="mana" aria-label={identity || "colourless"}>
      {list.map((c) => (
        <span key={c} className="pip" style={{ width: size, height: size, background: (PIP[c] ?? PIP.C)[0] }} title={c} />
      ))}
    </span>
  );
}

export const COLOUR_NAMES: Record<string, string> = { W: "White", U: "Blue", B: "Black", R: "Red", G: "Green", C: "Colourless" };
export const COLOUR_FILL: Record<string, string> = { W: "#e9e2b8", U: "#5aa7dc", B: "#8f8580", R: "#e5714a", G: "#4fa565", C: "#b4aca7" };
