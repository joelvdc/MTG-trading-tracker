"use client";

import type { HistoryEntry } from "@/lib/deckhistory";
import { formatDate } from "./api";
import { Dialog } from "./Dialog";

/** How a source reads in the list. */
const SOURCE_LABELS: Record<string, string> = { app: "App", website: "Website", archidekt: "Archidekt" };

/** The deck's change history (the synced Deck.history): one entry a day, newest first. */
export function HistoryDialog({ history, onClose }: { history: HistoryEntry[]; onClose: () => void }) {
  return (
    <Dialog title="Change history" onClose={onClose}>
      <div className="stack">
        {history.length === 0 ? (
          <p className="muted small">No changes recorded yet. Changes made from version 1.97 on show here.</p>
        ) : (
          <ul className="plain-list history-list">
            {history.map((entry, i) => (
              <li key={`${entry.day}-${i}`} className="stack tight">
                <div className="inline">
                  <strong className="grow">{formatDate(Date.parse(`${entry.day}T00:00:00Z`))}</strong>
                  <span className="muted small">{entry.sources.map((s) => SOURCE_LABELS[s] ?? s).join(" · ")}</span>
                </div>
                <ul className="plain-list small">
                  {Object.entries(entry.added).map(([name, count]) => (
                    <li key={`added-${name}`} className="ok-text">
                      +{count} {name}
                    </li>
                  ))}
                  {Object.entries(entry.removed).map(([name, count]) => (
                    <li key={`removed-${name}`} className="warn-text">
                      −{count} {name}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        )}
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </Dialog>
  );
}
