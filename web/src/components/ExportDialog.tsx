"use client";

import { useState } from "react";
import type { CollBinder, CollRow } from "@/lib/collview";
import { archidektCsv, fullCsv } from "@/lib/csvexport";
import { Dialog } from "./Dialog";

type Kind = "full" | "archidekt";

function fileName(base: string, kind: Kind): string {
  const safe = base.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "collection";
  return `mtg-${safe}${kind === "archidekt" ? "-archidekt" : ""}.csv`;
}

/** Saves the shown binder (or everything) as a CSV file, as the app's Export does. */
export function ExportDialog({
  rows,
  binders,
  priceType,
  binderLabel,
  onClose,
}: {
  rows: CollRow[];
  binders: CollBinder[];
  priceType: string;
  /** "collection" for everything, or the binder's name. */
  binderLabel: string;
  onClose: () => void;
}) {
  const [kind, setKind] = useState<Kind>("full");
  const copies = rows.reduce((n, r) => n + r.quantity, 0);

  function save() {
    const text = kind === "full" ? fullCsv(rows, binders, priceType) : archidektCsv(rows);
    const url = URL.createObjectURL(new Blob([text], { type: "text/csv;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName(binderLabel, kind);
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    onClose();
  }

  return (
    <Dialog title="Export as CSV" onClose={onClose}>
      <div className="stack">
        <p className="muted">
          {copies} cards ({rows.length} piles) from {binderLabel === "collection" ? "the whole collection" : `“${binderLabel}”`}.
        </p>
        <label className="check">
          <input type="radio" name="kind" checked={kind === "full"} onChange={() => setKind("full")} />
          <span>
            <strong>MTG Trader</strong>
            <span className="muted small"> · every field, prices included (the app's full export)</span>
          </span>
        </label>
        <label className="check">
          <input type="radio" name="kind" checked={kind === "archidekt"} onChange={() => setKind("archidekt")} />
          <span>
            <strong>Archidekt</strong>
            <span className="muted small"> · for Archidekt's collection import, binders merged</span>
          </span>
        </label>
      </div>
      <div className="dialog-actions">
        <span className="spacer" />
        <button className="btn" onClick={onClose}>
          Cancel
        </button>
        <button className="btn primary" disabled={rows.length === 0} onClick={save}>
          Save file
        </button>
      </div>
    </Dialog>
  );
}
