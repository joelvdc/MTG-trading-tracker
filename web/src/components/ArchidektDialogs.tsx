"use client";

import { useRef, useState } from "react";
import type { ArchidektDeckSummary } from "@/lib/archidekt";
import type { UpdateNeed } from "@/lib/deckload";
import { api } from "./api";
import { Dialog } from "./Dialog";

type Listed = ArchidektDeckSummary & { imported: boolean };

/** Shows a run through several decks, one request each, with a bar and a Stop button. */
function useRun() {
  const [progress, setProgress] = useState<{ done: number; total: number; text: string } | null>(null);
  const stop = useRef(false);
  return {
    progress,
    stopping: () => (stop.current = true),
    async run<T>(items: T[], label: (t: T) => string, step: (t: T) => Promise<void>): Promise<boolean> {
      stop.current = false;
      for (let i = 0; i < items.length; i++) {
        if (stop.current) return false;
        setProgress({ done: i, total: items.length, text: label(items[i]) });
        await step(items[i]);
      }
      setProgress({ done: items.length, total: items.length, text: "" });
      return true;
    },
  };
}

function Progress({ p, onStop }: { p: { done: number; total: number; text: string }; onStop: () => void }) {
  return (
    <div className="stack small">
      <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={p.total} aria-valuenow={p.done}>
        <span style={{ width: `${(100 * p.done) / Math.max(1, p.total)}%` }} />
      </div>
      <span className="muted">
        Deck {Math.min(p.done + 1, p.total)} of {p.total}
        {p.text ? ` · ${p.text}` : ""}
      </span>
      <div className="inline">
        <button className="btn small-btn" onClick={onStop}>
          Stop after this deck
        </button>
      </div>
    </div>
  );
}

/**
 * Importing from Archidekt (the app's import sheet): a deck link (optionally as a local deck), or a username or
 * profile link to pick decks from. Each deck is read from Archidekt and scored on Commander Salt, one at a time.
 */
export function ArchidektImportDialog({ user, onClose, onDone }: { user: string | null; onClose: () => void; onDone: (text: string, openId?: number) => void }) {
  const [text, setText] = useState(user ?? "");
  const [local, setLocal] = useState(false);
  const [list, setList] = useState<{ username: string; decks: Listed[] } | null>(null);
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const r = useRun();
  const isDeck = /archidekt\.com\/(?:api\/)?decks\/\d+/i.test(text) || /^\s*\d+\s*$/.test(text);

  async function go() {
    setError(null);
    if (isDeck) {
      setBusy("Reading the deck from Archidekt and scoring it on Commander Salt…");
      const a = await api<{ id?: number; name?: string; scoreError?: string | null }>("/api/decks", { action: "archidekt-import", link: text, local });
      setBusy(null);
      if (!a.ok || a.data.id === undefined) return setError(a.data.error ?? "That did not work.");
      onDone(a.data.scoreError ? `${a.data.name} saved, but Commander Salt couldn't score it: ${a.data.scoreError}` : `Imported ${a.data.name}${local ? " as a local deck" : ""}.`, a.data.id);
      return;
    }
    setBusy("Reading the decks from Archidekt…");
    const a = await api<{ username?: string; decks?: Listed[] }>("/api/decks", { action: "archidekt-decks", username: text });
    setBusy(null);
    if (!a.ok || !a.data.decks) return setError(a.data.error ?? "That did not work.");
    setList({ username: a.data.username ?? text, decks: a.data.decks });
    setPicked(notImported(a.data.decks));
  }

  /** The decks ticked at first: the Commander ones not imported yet. */
  function notImported(decks: Listed[]): Set<number> {
    return new Set(decks.filter((d) => !d.imported && d.commanderFormat).map((d) => d.id));
  }

  async function importPicked() {
    if (!list) return;
    const decks = list.decks.filter((d) => picked.has(d.id));
    let ok = 0;
    const unscored: string[] = [];
    const failed: string[] = [];
    setError(null);
    const finished = await r.run(
      decks,
      (d) => d.name,
      async (d) => {
        const a = await api<{ id?: number; scoreError?: string | null }>("/api/decks", { action: "archidekt-import", link: String(d.id) });
        if (!a.ok) failed.push(d.name);
        else {
          ok++;
          if (a.data.scoreError) unscored.push(d.name);
        }
      },
    );
    const parts = [`Imported ${ok} of ${decks.length} decks`];
    if (unscored.length) parts.push(`${unscored.length} couldn't be scored (reload them later)`);
    if (failed.length) parts.push(`${failed.length} failed: ${failed.join(", ")}`);
    onDone(parts.join(" · ") + (finished ? "" : " · stopped") + ".");
  }

  const running = r.progress !== null && r.progress.done < r.progress.total;
  return (
    <Dialog title="Import from Archidekt" onClose={() => !busy && !running && onClose()}>
      <div className="stack">
        {!list && (
          <>
            <label className="stack">
              <span className="muted small">A deck&apos;s link, or a username or profile link to pick from its public decks</span>
              <input type="text" value={text} onChange={(e) => setText(e.target.value)} placeholder="https://archidekt.com/decks/… or username" disabled={!!busy} autoFocus />
            </label>
            {isDeck && (
              <label className="check small">
                <input type="checkbox" checked={local} onChange={(e) => setLocal(e.target.checked)} disabled={!!busy} />
                <span>As a local deck (editable here, no longer reloaded from Archidekt)</span>
              </label>
            )}
            <p className="muted small">
              Public and unlisted decks only; the website doesn&apos;t log in to Archidekt. Each deck is scored on Commander Salt as in the app.
            </p>
            <div className="inline">
              <button className="btn primary" disabled={!!busy || !text.trim()} onClick={go}>
                {isDeck ? "Import" : "Show the decks"}
              </button>
            </div>
          </>
        )}
        {list && (
          <>
            <p className="small">
              {list.decks.length} public deck{list.decks.length === 1 ? "" : "s"} of {list.username}. Ticked: not imported yet{list.decks.some((d) => !d.commanderFormat) ? ", Commander only" : ""}.
            </p>
            <div className="inline wrap">
              <button className="btn small-btn" disabled={running} onClick={() => setPicked(new Set())}>
                Select none
              </button>
              <button className="btn small-btn" disabled={running} onClick={() => setPicked(new Set(list.decks.map((d) => d.id)))}>
                Select all
              </button>
              <button className="btn small-btn" disabled={running} onClick={() => setPicked(notImported(list.decks))}>
                Not imported
              </button>
            </div>
            <ul className="plain-list arch-list">
              {list.decks.map((d) => (
                <li key={d.id}>
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={picked.has(d.id)}
                      disabled={running}
                      onChange={(e) =>
                        setPicked((s) => {
                          const n = new Set(s);
                          if (e.target.checked) n.add(d.id);
                          else n.delete(d.id);
                          return n;
                        })
                      }
                    />
                    <span>
                      {d.name}
                      <span className="muted small">
                        {" "}
                        · {d.size} cards{d.folder ? ` · ${d.folder}` : ""}
                        {d.imported ? " · imported (reloads it)" : ""}
                      </span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
            {r.progress ? (
              running && <Progress p={r.progress} onStop={r.stopping} />
            ) : (
              <div className="inline">
                <button className="btn primary" disabled={picked.size === 0} onClick={importPicked}>
                  Import {picked.size} deck{picked.size === 1 ? "" : "s"}
                </button>
                <button className="btn" onClick={() => setList(null)}>
                  Back
                </button>
              </div>
            )}
          </>
        )}
        {busy && <p className="muted small">{busy}</p>}
        {error && <div className="bar error">{error}</div>}
      </div>
    </Dialog>
  );
}

/**
 * "Update all decks from Archidekt" (the app's updateAll): reloads and scores the decks changed on Archidekt since
 * they were read, and scores those without a good score, one at a time.
 */
export function ArchidektUpdateDialog({ onClose, onDone }: { onClose: () => void; onDone: (text: string) => void }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [started, setStarted] = useState(false);
  const r = useRun();

  async function go() {
    setStarted(true);
    setError(null);
    setBusy("Checking your decks on Archidekt…");
    const plan = await api<{ decks?: { id: number; name: string; need: UpdateNeed }[] }>("/api/decks", { action: "archidekt-check" });
    setBusy(null);
    if (!plan.ok || !plan.data.decks) {
      setStarted(false);
      return setError(plan.data.error ?? "That did not work.");
    }
    let updated = 0;
    let scored = 0;
    const unscored: string[] = [];
    const failed: string[] = [];
    const finished = await r.run(
      plan.data.decks,
      (d) => `${d.name}: ${d.need === "unscored" ? "scoring on Commander Salt" : "checking"}…`,
      async (d) => {
        const a = await api<{ done?: "updated" | "scored" | "same"; scoreError?: string | null }>(`/api/decks/${d.id}`, { action: "update", need: d.need });
        if (!a.ok) return void failed.push(d.name);
        if (a.data.done === "updated") updated++;
        if (a.data.done === "scored") scored++;
        if (a.data.scoreError) unscored.push(d.name);
      },
    );
    const parts = [updated === 0 && scored === 0 ? "All decks are up to date" : `Updated ${updated} changed deck${updated === 1 ? "" : "s"}${scored ? `, scored ${scored} more` : ""}`];
    if (unscored.length) parts.push(`${unscored.length} couldn't be scored: ${unscored.join(", ")}`);
    if (failed.length) parts.push(`${failed.length} failed: ${failed.join(", ")}`);
    onDone(parts.join(" · ") + (finished ? "" : " · stopped") + ".");
  }

  const running = started && !error;
  return (
    <Dialog title="Update all decks from Archidekt" onClose={() => !running && onClose()}>
      <div className="stack">
        {!started && (
          <>
            <p className="small">
              Reloads the decks that changed on Archidekt since they were read and scores them again on Commander Salt, and scores decks that have no score
              yet. Local decks aren&apos;t touched. Power levels from edhpowerlevel.com or ScrollVault are rated by the app at its next update.
            </p>
            <div className="inline">
              <button className="btn primary" onClick={go}>
                Update all decks
              </button>
            </div>
          </>
        )}
        {busy && <p className="muted small">{busy}</p>}
        {r.progress && r.progress.done < r.progress.total && <Progress p={r.progress} onStop={r.stopping} />}
        {error && <div className="bar error">{error}</div>}
      </div>
    </Dialog>
  );
}
