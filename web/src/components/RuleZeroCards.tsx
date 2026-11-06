"use client";

import { bracketName, POWER_SOURCES, type Deck, type PowerSource, type SaltCard } from "@/lib/deckshared";
import { ColourPips } from "./Mana";

/** Fixed tile colours per bracket (light tints with dark text), as the app draws them. */
export const BRACKET_COLORS: Record<number, [string, string]> = {
  1: ["#E1F5EE", "#085041"],
  2: ["#EAF3DE", "#27500A"],
  3: ["#FAEEDA", "#633806"],
  4: ["#FAECE7", "#712B13"],
  5: ["#FCEBEB", "#791F1F"],
  0: ["#F1EFE8", "#444441"],
};

/** Power meter colours, green (1) to red (10). */
const METER = ["#5DCAA5", "#5DCAA5", "#97C459", "#97C459", "#EF9F27", "#EF9F27", "#D85A30", "#D85A30", "#E24B4A", "#A32D2D"];

const AXES: [string, string][] = [
  ["consistency", "Consistency"],
  ["efficiency", "Efficiency"],
  ["interaction", "Interaction"],
  ["manabase", "Manabase"],
  ["winConditions", "Win conditions"],
];

const EFFECTS: [string, string][] = [
  ["fastmana", "Fast mana"],
  ["tutors", "Tutors"],
  ["counters", "Counterspells"],
  ["spotRemoval", "Spot removal"],
  ["boardWipes", "Board wipes"],
  ["grouphug", "Group hug"],
  ["groupslug", "Group slug"],
  ["extraTurns", "Extra turns"],
  ["stax", "Stax"],
  ["taxes", "Taxes"],
  ["mld", "Land denial"],
  ["combos", "Infinite combos"],
];

/** Power level as its source shows it: "4.4" (Commander Salt, ScrollVault), "6.70" (edhpowerlevel.com), "6" (the app's). */
export function formatPower(v: number, source: PowerSource): string {
  return v.toFixed(source === "EDH_POWER_LEVEL" ? 2 : source === "APP" ? 0 : 1);
}

/** The 1–5 bar Commander Salt draws for a 0–1 rating (the app's BracketAxes.level). */
export function axisLevel(rating: number): number {
  return rating >= 0.92 ? 5 : rating >= 0.8 ? 4 : rating >= 0.55 ? 3 : 2;
}

type DeckLike = Pick<Deck, "name" | "commanders" | "colorIdentity" | "bracketBaseline" | "bracketRealistic" | "archetype" | "saltPercent" | "owner" | "scoredAt">;

function Frame({ deck, cover, kind, inkSaver, children }: { deck: DeckLike; cover: string | null; kind: string; inkSaver: boolean; children: React.ReactNode }) {
  return (
    <article className={inkSaver ? "rz-card ink" : "rz-card"}>
      <header className="rz-head" style={!inkSaver && cover ? { backgroundImage: `url("${cover}")` } : undefined}>
        <div className="rz-tint">
          <span className="rz-kind">{kind}</span>
          <strong className="rz-name">{deck.name}</strong>
          <span className="rz-commanders">
            {deck.commanders} <ColourPips identity={deck.colorIdentity} />
          </span>
        </div>
      </header>
      {children}
    </article>
  );
}

function Section({ title, children }: { title?: string; children: React.ReactNode }) {
  return (
    <section className="rz-section">
      {title && <div className="rz-label">{title}</div>}
      {children}
    </section>
  );
}

function BarRow({ label, fraction, value }: { label: string; fraction: number; value: string }) {
  return (
    <div className="rz-bar-row">
      <span>{label}</span>
      <span className="rz-track">
        <span className="rz-fill" style={{ width: `${Math.min(1, Math.max(0, fraction)) * 100}%` }} />
      </span>
      <span className="rz-value">{value}</span>
    </div>
  );
}

function Footer({ credit, deck }: { credit: string; deck: DeckLike }) {
  const by = [deck.owner || null, deck.scoredAt ? new Date(deck.scoredAt).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" }) : null].filter(Boolean).join(" · ");
  return (
    <footer className="rz-foot">
      <span style={{ whiteSpace: "pre-line" }}>{credit}</span>
      <span>{by}</span>
    </footer>
  );
}

/** The bracket card (the app's BracketCardView); [speed] is the win-turn section, once the website works it out. */
export function BracketCard({ deck, cover, data, inkSaver, speed }: { deck: DeckLike; cover: string | null; data: SaltCard; inkSaver: boolean; speed?: React.ReactNode }) {
  const tile = (label: string, b: number | null) => {
    const [bg, fg] = BRACKET_COLORS[b ?? 0] ?? BRACKET_COLORS[0];
    return (
      <div className="rz-tile" style={{ background: bg, color: fg }}>
        <span className="rz-label-in">{label}</span>
        <span className="rz-big">{b ?? "—"}</span>
        <span className="rz-label-in">{bracketName(b) ?? ""}</span>
      </div>
    );
  };
  const criteria: [string, number][] = (
    [
      ["Two-card combos", data.twoCardCombos.length],
      ["Game changers", data.gameChangers.length],
      ["Early-game combos", data.earlyCombos.length],
      ["Extra turns", data.extraTurns.length],
      ["Mass land denial", data.massLandDenial.length],
    ] as [string, number][]
  )
    .map((c, i) => ({ c, i }))
    .sort((a, b) => Number(b.c[1] > 0) - Number(a.c[1] > 0) || a.i - b.i)
    .map((x) => x.c);
  const combos = [...new Map([...data.twoCardCombos, ...data.earlyCombos].map((c) => [JSON.stringify(c), c])).values()];
  return (
    <Frame deck={deck} cover={cover} kind="Bracket" inkSaver={inkSaver}>
      <div className="rz-tiles">
        {tile("Baseline · WotC rules", deck.bracketBaseline)}
        {tile("Realistic · how it plays", deck.bracketRealistic)}
      </div>
      {speed}
      <Section title="Bracket criteria">
        {criteria.map(([label, n]) => (
          <div key={label} className={n > 0 ? "rz-criterion hit" : "rz-criterion"}>
            <span>{n > 0 ? "⚠" : "✓"}</span>
            <span className="grow">{label}</span>
            <span>{n}</span>
          </div>
        ))}
      </Section>
      {data.gameChangers.length > 0 && (
        <Section title="Game changers">
          <p>{data.gameChangers.join(", ")}</p>
        </Section>
      )}
      {Object.keys(data.axes).length > 0 && (
        <Section title="How it plays (1–5)">
          {AXES.filter(([k]) => data.axes[k] !== undefined).map(([k, label]) => {
            const level = axisLevel(data.axes[k]);
            return <BarRow key={k} label={label} fraction={level / 5} value={String(level)} />;
          })}
        </Section>
      )}
      {(combos.length > 0 || data.notes.length > 0) && (
        <Section title="Rule zero: worth mentioning">
          {combos.map((c) => (
            <p key={c.join("+")}>{c.join(" + ")}</p>
          ))}
          {data.notes.map((n, i) => (
            <p key={i} className="rz-note">
              {n.why.trim() ? n.why : n.label}
            </p>
          ))}
        </Section>
      )}
      <Footer credit="Bracket data: Commander Salt" deck={deck} />
    </Frame>
  );
}

/** The power level card (the app's PowerCardView). */
export function PowerCard({
  deck,
  cover,
  data,
  source,
  power,
  error,
  scrollVault,
  inkSaver,
}: {
  deck: DeckLike;
  cover: string | null;
  data: SaltCard;
  source: PowerSource;
  power: number | null;
  error: string | null;
  scrollVault: { margin?: number | null; typicalWin?: number | null; earliestWin?: number | null; bracket?: number | null; borderline?: number | null; podLine?: string | null } | null;
  inkSaver: boolean;
}) {
  const site = POWER_SOURCES[source].site;
  const sv = source === "SCROLLVAULT" && power !== null ? scrollVault : null;
  const fmt = (n: number) => formatPower(n, source);
  const effects = EFFECTS.filter(([k]) => data.effects[k] !== undefined).map(([k, label]) => [label, data.effects[k]] as [string, number]);
  const shown = effects.filter(([, n]) => n > 0).sort((a, b) => b[1] - a[1]);
  const max = Math.max(1, ...shown.map(([, n]) => n));
  const none = effects.filter(([, n]) => n === 0).map(([l]) => l.toLowerCase());
  return (
    <Frame deck={deck} cover={cover} kind="Power level" inkSaver={inkSaver}>
      <div className="rz-section rz-power">
        <div className="inline">
          <span className="rz-big">{power !== null ? fmt(power) : "—"}</span>
          <span className="muted grow">/ 10</span>
          {deck.archetype && <span className="rz-pill">{deck.archetype}</span>}
        </div>
        <div className="rz-small">
          {source === "COMMANDER_SALT"
            ? [data.playStyle?.toLowerCase(), site].filter(Boolean).join(" · ")
            : sv?.margin != null
              ? `by ${site} · ±${fmt(sv.margin)}`
              : `by ${site}`}
        </div>
        <div className="rz-meter">
          {METER.map((c, i) => (
            <span key={i}>
              <span style={{ width: `${Math.min(1, Math.max(0, (power ?? 0) - i)) * 100}%`, background: c }} />
            </span>
          ))}
        </div>
        {power === null && source !== "COMMANDER_SALT" && (
          <div className="rz-small rz-error">
            {error
              ? `${site[0].toUpperCase()}${site.slice(1)} couldn't rate this deck: ${error}`
              : source === "APP"
                ? "Worked out on the phone (the app's own power level isn't synced)."
                : `Not rated by ${site} yet.`}
          </div>
        )}
      </div>
      {sv && (sv.typicalWin != null || sv.bracket != null || sv.podLine) && (
        <Section title="At the table (ScrollVault)">
          {sv.typicalWin != null && (
            <p>
              <strong>Typical win turn {sv.typicalWin}</strong>
              {sv.earliestWin != null && <span className="rz-small"> · earliest turn {sv.earliestWin}</span>}
            </p>
          )}
          {sv.bracket != null && (
            <p className="rz-small">
              Its bracket: {sv.bracket} ({bracketName(sv.bracket)?.toLowerCase()}){sv.borderline != null ? `, borderline ${sv.borderline}` : ""}
            </p>
          )}
          {sv.podLine && <p className="rz-quote">“{sv.podLine}”</p>}
        </Section>
      )}
      <Section>
        <div className="rz-two">
          <div>
            <div className="rz-label">Saltiness</div>
            <p>
              <strong>{deck.saltPercent != null ? `${Math.round(deck.saltPercent)}%` : "—"}</strong>
              {data.saltIntensity && <span className="rz-error"> {data.saltIntensity.toLowerCase()}</span>}
            </p>
            {data.saltDriver && <p className="rz-small">mostly {data.saltDriver.toLowerCase()}</p>}
          </div>
          <div>
            <div className="rz-label">Manabase</div>
            {data.manaFixing != null && <p className="rz-small">Fixing {data.manaFixing}%</p>}
            {data.onCurve != null && <p className="rz-small">On curve {data.onCurve}%</p>}
            {data.manaQuality != null && <p className="rz-small">Quality {data.manaQuality}%</p>}
          </div>
        </div>
      </Section>
      {effects.length > 0 && (
        <Section title="Interaction and effects">
          {shown.map(([label, n]) => (
            <BarRow key={label} label={label} fraction={n / max} value={String(n)} />
          ))}
          {none.length > 0 && <p className="rz-small">None: {none.join(", ")}</p>}
        </Section>
      )}
      {data.wincons.length > 0 && (
        <Section title="Win conditions besides combos">
          <div className="rz-wincons">
            {data.wincons.map((w) => (
              <span key={w}>{w}</span>
            ))}
          </div>
        </Section>
      )}
      <Footer
        credit={[source === "COMMANDER_SALT" ? null : source === "APP" ? "Power level: MTG Trader app" : `Power level: ${site}`, "Data: Commander Salt"].filter(Boolean).join("\n")}
        deck={deck}
      />
    </Frame>
  );
}
