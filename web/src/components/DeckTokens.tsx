"use client";

import { useEffect, useMemo, useState } from "react";
import { scryfallImage, tokens, type DeckToken, type StatsCard } from "@/lib/deckstats";
import { api } from "./api";
import { Dialog } from "./Dialog";

/**
 * The deck's tokens as their own foldable section (the app's tokens card): the clickable token grid and the dialog for
 * choosing a printing. Left out when the deck makes no tokens. The page keeps whether it is folded, like its other sections.
 */
export function DeckTokens({
  cards,
  tokenInfo,
  tokenPicks,
  onTokenPick,
  hover,
  open,
  onToggle,
}: {
  cards: StatsCard[];
  tokenInfo: Record<string, { oi: string | null; pt: string | null }>;
  /** The printing picked for each token (its key → Scryfall id). */
  tokenPicks: Record<string, string>;
  /** Saves a token's printing (null: the cards' own); an error text or null. Missing when the deck can't be changed. */
  onTokenPick?: (key: string, id: string | null) => Promise<string | null>;
  hover: { follow: (url: string | null) => (e: React.MouseEvent) => void; hide: () => void };
  open: boolean;
  onToggle: (open: boolean) => void;
}) {
  const [openToken, setOpenToken] = useState<string | null>(null);
  const toks = useMemo(() => {
    const oi: Record<string, string | undefined> = {};
    const pt: Record<string, string | null> = {};
    for (const [id, t] of Object.entries(tokenInfo)) {
      if (t.oi) oi[id] = t.oi;
      pt[id] = t.pt;
    }
    return tokens(cards, oi, pt, tokenPicks);
  }, [cards, tokenInfo, tokenPicks]);
  const shownToken = toks.find((t) => t.key === openToken) ?? null;
  if (toks.length === 0) return null;

  return (
    <>
      <details className="card" open={open} onToggle={(e) => onToggle((e.currentTarget as HTMLDetailsElement).open)}>
        <summary>
          <strong>Tokens</strong> <span className="muted small">· {toks.length}</span>
        </summary>
        <ul className="token-grid">
          {toks.map((t) => (
            <li
              key={t.key}
              className="clickable"
              role="button"
              tabIndex={0}
              title="See it and choose its printing"
              onClick={() => (hover.hide(), setOpenToken(t.key))}
              onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && (e.preventDefault(), setOpenToken(t.key))}
              onMouseMove={hover.follow(scryfallImage(t.id))}
              onMouseLeave={hover.hide}
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={scryfallImage(t.id, "small")} alt="" loading="lazy" />
              <span className="small">
                <strong>{t.label}</strong>
                {t.picked && <span className="ok-text"> · your printing</span>}
                <span className="muted"> · made by {t.makers.join(", ")}</span>
              </span>
            </li>
          ))}
        </ul>
        <p className="muted small">Click a token to see it{onTokenPick ? " and choose the printing shown for this deck (the phones show it too)" : ""}.</p>
      </details>
      {shownToken && <TokenDialog token={shownToken} onPick={onTokenPick} onClose={() => setOpenToken(null)} />}
    </>
  );
}

type Printing = { id: string; setName: string; collectorNumber: string };

/** A token large, with every printing to pick from (the app's TokenDialog). */
function TokenDialog({ token, onPick, onClose }: { token: DeckToken; onPick?: (key: string, id: string | null) => Promise<string | null>; onClose: () => void }) {
  const [prints, setPrints] = useState<Printing[] | null>(null);
  const [choosing, setChoosing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!token.oracleId) {
      setPrints([]);
      return;
    }
    api<{ results?: Printing[] }>(`/api/cards/tokenprints?oracle=${encodeURIComponent(token.oracleId)}`).then((r) =>
      setPrints(r.ok && Array.isArray(r.data.results) ? r.data.results : []),
    );
  }, [token.oracleId]);
  const where = (id: string) => prints?.find((p) => p.id === id);
  async function pick(id: string | null) {
    if (!onPick) return;
    setBusy(true);
    setError(null);
    const e = await onPick(token.key, id === token.defaultId ? null : id);
    setBusy(false);
    if (e) setError(e);
    else setChoosing(false);
  }
  const ids = prints ? [token.defaultId, ...prints.map((p) => p.id).filter((id) => id !== token.defaultId)] : [];
  const here = where(token.id);
  return (
    <Dialog title={choosing ? `${token.name} · choose a printing` : token.label} onClose={() => !busy && onClose()}>
      <div className="stack">
        {!choosing ? (
          <>
            <p className="muted small">Made by {token.makers.join(", ")}</p>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={scryfallImage(token.id)} alt={token.label} className="line-picture" />
            {here && (
              <p className="small center">
                {here.setName} · #{here.collectorNumber}
              </p>
            )}
            {token.picked && <p className="ok-text small center">Your pick for this deck</p>}
          </>
        ) : prints === null ? (
          <p className="muted small">Looking up its printings on Scryfall…</p>
        ) : prints.length === 0 ? (
          <p className="muted small">Its printings can&apos;t be looked up right now.</p>
        ) : (
          <>
            <p className="muted small">{prints.length} printings on Scryfall, newest first. The one you pick shows for this deck, here and on the phones.</p>
            <ul className="print-pick">
              {ids.map((id) => (
                <li key={id}>
                  <button className={id === token.id ? "on" : ""} disabled={busy} onClick={() => pick(id)} title={id === token.defaultId ? "The cards' own printing" : undefined}>
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={scryfallImage(id, "small")} alt="" loading="lazy" />
                    <span className="small">
                      {where(id)?.setName ?? "The cards' own"}
                      {where(id) ? ` #${where(id)!.collectorNumber}` : ""}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          {choosing && token.picked && (
            <button className="btn" disabled={busy} onClick={() => pick(null)}>
              Use the cards&apos; own printing
            </button>
          )}
          <span className="spacer" />
          {!choosing && onPick && (
            <button className="btn" disabled={!token.oracleId} onClick={() => setChoosing(true)}>
              Other printings
            </button>
          )}
          <button className="btn" disabled={busy} onClick={() => (choosing ? setChoosing(false) : onClose())}>
            {choosing ? "Back" : "Close"}
          </button>
        </div>
      </div>
    </Dialog>
  );
}
