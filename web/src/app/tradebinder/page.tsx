"use client";

import { useEffect, useMemo, useState } from "react";
import { api } from "@/components/api";
import { Dialog } from "@/components/Dialog";
import { TopBar } from "@/components/Nav";
import type { TradeBinderView } from "@/app/api/tradebinder/route";
import { displayName } from "@/lib/colquery";
import { format } from "@/lib/money";
import type { TradeChange, TradeRules } from "@/lib/tradebinder";

type Choice = "accept" | "decline" | "later";

const finishLabel = (c: TradeChange) => (c.kind.etched ? "Etched foil" : c.kind.foil ? "Foil" : "Normal");

/**
 * The trade binder (the app's Trade binder screen): spare copies worth trading, and copies to take out. Accept or
 * decline each suggestion; declined ones aren't suggested again. The binder is made when the first copies go in.
 */
export default function TradeBinderPage() {
  const [view, setView] = useState<TradeBinderView | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [choices, setChoices] = useState<Record<string, Choice>>({});
  const [editingRules, setEditingRules] = useState(false);

  useEffect(() => {
    (async () => {
      const [s, t] = await Promise.all([
        api<{ loggedIn: boolean; folder?: string }>("/api/session"),
        api<TradeBinderView & { noFolder?: boolean }>("/api/tradebinder"),
      ]);
      if (!s.data.loggedIn || t.status === 401 || t.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || t.data.noFolder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      if (t.ok && Array.isArray(t.data.changes)) setView(t.data);
      else setError(t.data.error ?? "Could not load the trade binder.");
    })();
  }, []);

  const id = (c: TradeChange) => `${c.add ? "add" : "remove"}|${c.key}`;
  const choice = (c: TradeChange): Choice => choices[id(c)] ?? "accept";

  const counts = useMemo(() => {
    if (!view) return { accept: 0, decline: 0 };
    let accept = 0;
    let decline = 0;
    for (const c of view.changes) {
      const ch = choices[id(c)] ?? "accept";
      if (ch === "accept") accept++;
      if (ch === "decline") decline++;
    }
    return { accept, decline };
  }, [view, choices]);

  async function send(body: unknown, done: string) {
    setBusy(true);
    setMessage(null);
    const r = await api<TradeBinderView>("/api/tradebinder", body);
    setBusy(false);
    if (r.ok && Array.isArray(r.data.changes)) {
      setView(r.data);
      setChoices({});
      setMessage(done);
      return true;
    }
    setMessage(r.data.error ?? "That didn't work.");
    return false;
  }

  if (!view) {
    return (
      <>
        <TopBar page="collection" folder={folder} />
        <main className="page wide">{error ? <div className="bar error">{error}</div> : <p className="muted center">Loading…</p>}</main>
      </>
    );
  }

  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  const adds = view.changes.filter((c) => c.add);
  const removes = view.changes.filter((c) => !c.add);
  const decide = (c: TradeChange) => ({ key: c.key, add: c.add, copies: c.copies });
  const apply = () =>
    send(
      {
        action: "apply",
        accepted: view.changes.filter((c) => choice(c) === "accept").map(decide),
        declined: view.changes.filter((c) => choice(c) === "decline").map(decide),
      },
      "Done. The phones get the changes with their next sync.",
    );
  const setAll = (list: TradeChange[], ch: Choice) => setChoices((old) => ({ ...old, ...Object.fromEntries(list.map((c) => [id(c), ch])) }));

  const List = ({ title, list }: { title: string; list: TradeChange[] }) =>
    list.length === 0 ? null : (
      <section className="stat-block">
        <div className="value-title">
          <h2>
            {title} ({list.length})
          </h2>
          <div className="seg text" role="group" aria-label={`All of ${title}`}>
            <button onClick={() => setAll(list, "accept")} disabled={view.readOnly}>
              Accept all
            </button>
            <button onClick={() => setAll(list, "decline")} disabled={view.readOnly}>
              Decline all
            </button>
          </div>
        </div>
        <ul className="stat-cards trade-list">
          {list.map((c) => {
            const ch = choice(c);
            return (
              <li key={id(c)} className={ch === "accept" ? undefined : "faded"}>
                {c.card.imageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img className="mini-thumb" src={c.card.imageUrl} alt="" loading="lazy" />
                ) : (
                  <span className="mini-thumb" />
                )}
                <span className="grow">
                  {c.copies > 1 ? `${c.copies}× ` : ""}
                  {displayName(c.card)}
                  <span className="muted small">
                    {" "}
                    · {c.card.setCode.toUpperCase()} · {finishLabel(c)} · {c.kind.condition} · {c.kind.language.toUpperCase()}
                  </span>
                  <div className="muted small">{c.reason}</div>
                </span>
                <div className="seg text" role="group" aria-label={`${displayName(c.card)}: accept or decline`}>
                  {(["accept", "decline", "later"] as Choice[]).map((x) => (
                    <button key={x} className={ch === x ? "on" : undefined} aria-pressed={ch === x} disabled={view.readOnly} onClick={() => setChoices((o) => ({ ...o, [id(c)]: x }))}>
                      {x === "accept" ? (c.add ? "Put in" : "Take out") : x === "decline" ? "Decline" : "Later"}
                    </button>
                  ))}
                </div>
              </li>
            );
          })}
        </ul>
      </section>
    );

  const r = view.rules;
  return (
    <>
      <TopBar page="collection" folder={folder} />
      <main className="page wide">
        <div className="controls">
          <a className="btn" href="/collection">
            ← Collection
          </a>
          <button className="btn" onClick={() => setEditingRules(true)} disabled={view.readOnly}>
            Rules
          </button>
          {view.declined > 0 && (
            <button className="btn" disabled={busy || view.readOnly} onClick={() => send({ action: "forget" }, "The declined cards are suggested again.")}>
              Suggest declined cards again ({view.declined})
            </button>
          )}
          <button className="btn primary" disabled={busy || view.readOnly || counts.accept + counts.decline === 0} onClick={apply}>
            {busy ? "Saving…" : `Apply (${counts.accept} accepted, ${counts.decline} declined)`}
          </button>
        </div>
        {message && <div className="bar">{message}</div>}

        <div className="stat-tiles">
          <div className="stat-tile">
            <div className="muted small">{view.binder ? view.binder.name : "Trade binder"}</div>
            <div className="big">{view.binderCards} cards</div>
            <div className="muted small">
              {view.binder ? `${money(view.binderValue)} (${view.priceTypeLabel}, ${view.sourceLabel})` : "Made when the first cards go in"}
            </div>
          </div>
          <div className="stat-tile">
            <div className="muted small">Rules</div>
            <div className="small">
              At most {r.maxCards} cards · from {money(r.minValue)} · {r.keepOne ? "keep one copy of each card" : "trade every spare copy"}
              {r.keepBestAlways ? " · the best copy stays home" : r.keepBestForDecks ? " · deck cards: the best copy stays home" : ""}
            </div>
          </div>
        </div>
        <p className="muted small">
          Spare copies (beyond what your decks use and the copy you keep) go in by value and by how much Commander players want them (EDHREC), with a
          nudge for rising prices. Copies taken out go to Unsorted. Cards on your wishlist, basic lands, tokens, signed and altered cards are never suggested.
        </p>

        {view.changes.length === 0 && <p className="muted center">The trade binder is up to date: no changes to suggest.</p>}
        <List title="Put in" list={adds} />
        <List title="Take out" list={removes} />
      </main>
      {editingRules && (
        <RulesDialog
          rules={r}
          money={money}
          busy={busy}
          onClose={() => setEditingRules(false)}
          onSave={async (rules) => {
            if (await send({ action: "rules", rules }, "Rules saved; the suggestions follow them.")) setEditingRules(false);
          }}
        />
      )}
    </>
  );
}

function RulesDialog({ rules, money, busy, onClose, onSave }: { rules: TradeRules; money: (eur: number) => string; busy: boolean; onClose: () => void; onSave: (r: TradeRules) => void }) {
  const [r, setR] = useState(rules);
  const [max, setMax] = useState(String(rules.maxCards));
  const [min, setMin] = useState(String(rules.minValue));
  const maxN = Number.parseInt(max, 10);
  const minN = Number.parseFloat(min.replace(",", "."));
  const ok = Number.isInteger(maxN) && maxN >= 0 && maxN <= 10000 && Number.isFinite(minN) && minN >= 0;
  return (
    <Dialog title="Trade binder rules" onClose={onClose}>
      <label className="field">
        <span>At most this many cards</span>
        <input type="number" min={0} max={10000} value={max} onChange={(e) => setMax(e.target.value)} />
      </label>
      <label className="field">
        <span>Only cards worth at least (in euros{money(1).startsWith("€") ? "" : `; ${money(1)} per euro`})</span>
        <input type="number" min={0} step={0.5} value={min} onChange={(e) => setMin(e.target.value)} />
      </label>
      <label className="check">
        <input type="checkbox" checked={r.keepOne} onChange={(e) => setR({ ...r, keepOne: e.target.checked })} /> Keep one copy of each card
      </label>
      <label className="check">
        <input type="checkbox" checked={r.keepBestForDecks} onChange={(e) => setR({ ...r, keepBestForDecks: e.target.checked })} /> For cards a deck uses, offer the
        cheapest spare copies and keep the best
      </label>
      <label className="check">
        <input type="checkbox" checked={r.keepBestAlways} onChange={(e) => setR({ ...r, keepBestAlways: e.target.checked })} /> Do that for every card
      </label>
      <div className="dialog-actions">
        <button className="btn" onClick={onClose}>
          Cancel
        </button>
        <button className="btn primary" disabled={!ok || busy} onClick={() => onSave({ ...r, maxCards: maxN, minValue: minN })}>
          Save
        </button>
      </div>
    </Dialog>
  );
}
