"use client";

import { useState } from "react";
import { api } from "@/components/api";
import type { CollBinder, CollectionView } from "@/lib/collview";
import type { ImportResult } from "@/lib/csvimport";
import { Dialog } from "./Dialog";

/** Imports a collection CSV (ManaBox, Moxfield, Archidekt, Deckbox, the app's own export…), like the app's Import. */
export function ImportDialog({
  binders,
  startBinder,
  onDone,
  onClose,
}: {
  binders: CollBinder[];
  /** The binder shown now (null: Unsorted). */
  startBinder: string | null;
  onDone: (view: CollectionView, text: string) => void;
  onClose: () => void;
}) {
  const [text, setText] = useState<string | null>(null);
  const [fileName, setFileName] = useState("");
  const [binder, setBinder] = useState<string>(startBinder ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run() {
    if (text === null) return;
    setBusy(true);
    setError(null);
    const r = await api<CollectionView & { import?: ImportResult }>("/api/collection/import", { text, binder: binder || null });
    setBusy(false);
    if (r.ok && r.data.import) {
      const i = r.data.import;
      const parts = [`Imported ${i.imported} card${i.imported === 1 ? "" : "s"}.`];
      if (i.newBinders.length) parts.push(`New binder${i.newBinders.length === 1 ? "" : "s"}: ${i.newBinders.join(", ")}.`);
      if (i.notFound) parts.push(`${i.notFound} not found on Scryfall${i.missing.length ? `: ${i.missing.join(", ")}${i.notFound > i.missing.length ? "…" : ""}` : ""}.`);
      onDone(r.data, parts.join(" "));
    } else setError(r.data.error ?? "The import did not work.");
  }

  return (
    <Dialog title="Import CSV" onClose={onClose}>
      <div className="stack">
        <p className="muted small">
          A collection file from ManaBox, Moxfield, Archidekt, Deckbox or the app&apos;s own export. The file needs a header line; the
          cards are found by Scryfall ID, by set code and collector number, or by name. Quantity, foil, condition, language, binder,
          purchase price, notes, signed and altered are read when the file has them.
        </p>
        <label className="field">
          <span>File</span>
          <input
            type="file"
            accept=".csv,text/csv,text/plain"
            disabled={busy}
            onChange={async (e) => {
              const f = e.target.files?.[0];
              setError(null);
              if (!f) return setText(null);
              if (f.size > 8_000_000) {
                setText(null);
                return setError("That file is bigger than 8 MB.");
              }
              setFileName(f.name);
              setText(await f.text());
            }}
          />
        </label>
        <label className="field">
          <span>Cards without a binder in the file go to</span>
          <select value={binder} disabled={busy} onChange={(e) => setBinder(e.target.value)}>
            <option value="">Unsorted</option>
            {binders.map((b) => (
              <option key={b.uid} value={b.uid}>
                {b.name}
              </option>
            ))}
          </select>
        </label>
        <p className="muted small">A binder named in the file that doesn&apos;t exist yet is made. The phones get the cards at their next sync.</p>
        {busy && <div className="bar">Looking the cards up on Scryfall… (a big file takes a minute)</div>}
        {error && <div className="bar error">{error}</div>}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn primary" disabled={busy || text === null} onClick={run} title={fileName}>
            Import
          </button>
        </div>
      </div>
    </Dialog>
  );
}
