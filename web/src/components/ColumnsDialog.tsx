"use client";

import { useState } from "react";
import { COLUMN_GROUPS, COLUMNS, columnDef, DEFAULT_COLUMNS, type ColumnId } from "@/lib/collcolumns";
import { Dialog } from "./Dialog";

/**
 * Which columns the collection table shows, and in what order (since app 1.90): the shown ones at the top with
 * arrows to move them, every other column below by group with a tick box. Remembered in this browser.
 */
export function ColumnsDialog({ columns, onSave, onClose }: { columns: ColumnId[]; onSave: (c: ColumnId[]) => void; onClose: () => void }) {
  const [shown, setShown] = useState<ColumnId[]>(columns);
  const move = (i: number, by: number) => {
    const to = i + by;
    if (to < 0 || to >= shown.length) return;
    const next = [...shown];
    next.splice(to, 0, next.splice(i, 1)[0]);
    setShown(next);
  };
  const label = (id: ColumnId) => {
    const c = columnDef(id);
    return id === "changeMoney" ? "Change (money)" : id === "change" ? "Change (%)" : c.label;
  };
  return (
    <Dialog title="Columns" onClose={onClose}>
      <div className="stack">
        <p className="muted small">The table&apos;s columns in this browser. Name is always shown first.</p>
        <div className="field">
          <span>Shown</span>
          <ol className="arrange-list columns-list">
            {shown.map((id, i) => (
              <li key={id}>
                <label className="inline">
                  <input type="checkbox" className="tick" checked aria-label={`Hide ${label(id)}`} onChange={() => setShown(shown.filter((x) => x !== id))} />
                  <span>
                    {label(id)} <span className="muted small">{columnDef(id).hint}</span>
                  </span>
                </label>
                <span className="spacer" />
                <button type="button" className="btn small-btn" aria-label={`Move ${label(id)} up`} disabled={i === 0} onClick={() => move(i, -1)}>
                  ↑
                </button>
                <button type="button" className="btn small-btn" aria-label={`Move ${label(id)} down`} disabled={i === shown.length - 1} onClick={() => move(i, 1)}>
                  ↓
                </button>
              </li>
            ))}
            {shown.length === 0 && <li className="muted small">Only the name.</li>}
          </ol>
        </div>
        {COLUMN_GROUPS.map((g) => {
          const more = COLUMNS.filter((c) => c.group === g && !shown.includes(c.id));
          if (more.length === 0) return null;
          return (
            <div className="field" key={g}>
              <span>More: {g.toLowerCase()}</span>
              <ul className="arrange-list columns-list">
                {more.map((c) => (
                  <li key={c.id}>
                    <label className="inline">
                      <input type="checkbox" className="tick" checked={false} aria-label={`Show ${label(c.id)}`} onChange={() => setShown([...shown, c.id])} />
                      <span>
                        {label(c.id)} <span className="muted small">{c.hint}</span>
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            </div>
          );
        })}
        <div className="dialog-actions">
          <button type="button" className="link" onClick={() => setShown(DEFAULT_COLUMNS)}>
            Reset to default
          </button>
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn primary" onClick={() => onSave(shown)}>
            Save
          </button>
        </div>
      </div>
    </Dialog>
  );
}
