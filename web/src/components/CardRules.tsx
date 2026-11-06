"use client";

import { useEffect, useState, type ReactNode } from "react";
import { formatName, LEGALITY_LABELS, orderedFormats, rulingDate, type CardRules, type Legality } from "@/lib/cardrules";
import { api } from "./api";
import { ManaCost, SymbolText } from "./Mana";

type Loaded = { id: string; rules: CardRules | null; error: string | null };

/** Loads a card's rules text, legalities and rulings; [loading] while the answer for this card is on its way. */
function useCardRules(scryfallId: string): { rules: CardRules | null; error: string | null; loading: boolean } {
  const [got, setGot] = useState<Loaded | null>(null);
  useEffect(() => {
    let live = true;
    api<CardRules>(`/api/cards/rules?id=${encodeURIComponent(scryfallId)}`).then((r) => {
      if (live) setGot(r.ok ? { id: scryfallId, rules: r.data, error: null } : { id: scryfallId, rules: null, error: r.data.error ?? "Could not load the card text." });
    });
    return () => {
      live = false;
    };
  }, [scryfallId]);
  const mine = got?.id === scryfallId ? got : null;
  return { rules: mine?.rules ?? null, error: mine?.error ?? null, loading: !mine };
}

function CardText({ rules, error, loading }: { rules: CardRules | null; error: string | null; loading: boolean }) {
  if (loading) return <div className="muted small">Loading the card text…</div>;
  if (error || !rules) return <div className="muted small">{error ?? "No card text."}</div>;
  return (
    <div className="card-text">
      {rules.faces.map((f, i) => (
        <div key={i} className="card-face">
          <div className="card-face-head">
            <strong>{f.name}</strong>
            <ManaCost cost={f.manaCost} size={15} />
          </div>
          <div className="muted small">{f.typeLine}</div>
          {f.oracleText && (
            <div className="oracle">
              <SymbolText text={f.oracleText} />
            </div>
          )}
          {f.power != null && f.toughness != null && <div className="small">{f.power}/{f.toughness}</div>}
          {f.loyalty != null && <div className="small">Loyalty {f.loyalty}</div>}
          {f.defense != null && <div className="small">Defense {f.defense}</div>}
        </div>
      ))}
    </div>
  );
}

const KEY: Legality[] = ["legal", "banned", "restricted", "not_legal"];

function RulingsPanel({ rules, error, loading }: { rules: CardRules | null; error: string | null; loading: boolean }) {
  if (loading) return <div className="muted small">Loading the rulings…</div>;
  if (error || !rules) return <div className="bar error">{error ?? "Could not load the rulings."}</div>;
  return (
    <div className="stack">
      <section>
        <h3 className="mini-head">Legality</h3>
        <ul className="legal-grid">
          {orderedFormats(rules.legalities).map((f) => (
            <li key={f} className={`legal-chip ${rules.legalities[f]}`}>
              <span>{formatName(f)}</span>
              <strong>{LEGALITY_LABELS[rules.legalities[f]]}</strong>
            </li>
          ))}
        </ul>
        <div className="legal-key small muted">
          Key:
          {KEY.map((k) => (
            <span key={k} className={`legal-chip ${k}`}>
              {LEGALITY_LABELS[k]}
            </span>
          ))}
        </div>
      </section>
      <section>
        <h3 className="mini-head">Rulings</h3>
        {rules.rulings.length === 0 ? (
          <div className="muted small">No rulings for this card.</div>
        ) : (
          <ul className="plain-list ruling-list">
            {rules.rulings.map((r, i) => (
              <li key={i}>
                <div className="muted small">{rulingDate(r.date)}</div>
                <div>{r.text}</div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

/**
 * The card's text (every face) under its picture, then [between] (always shown), then the tabs "Versions" (the window's own content, [children]) and
 * "Rulings". A failed or slow load only affects the text and the Rulings tab.
 */
export function CardRulesTabs({ scryfallId, between, children }: { scryfallId: string; between?: ReactNode; children: ReactNode }) {
  const [tab, setTab] = useState<"versions" | "rulings">("versions");
  const r = useCardRules(scryfallId);
  return (
    <>
      <CardText {...r} />
      {between}
      <div className="seg text card-window-tabs" role="tablist" aria-label="Card information">
        <button role="tab" aria-selected={tab === "versions"} className={tab === "versions" ? "on" : ""} onClick={() => setTab("versions")}>
          Versions
        </button>
        <button role="tab" aria-selected={tab === "rulings"} className={tab === "rulings" ? "on" : ""} onClick={() => setTab("rulings")}>
          Rulings
        </button>
      </div>
      {tab === "versions" ? children : <RulingsPanel {...r} />}
    </>
  );
}
