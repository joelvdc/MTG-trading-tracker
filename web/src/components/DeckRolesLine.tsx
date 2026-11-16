"use client";

import type { RoleCount, RoleStatus } from "@/lib/deckroles";

/** The colour of a count: "ok" is the success colour, "low" and "high" the warning colour. */
function numberClass(status: RoleStatus): string {
  return status === "ok" ? "ok-text" : status === "low" || status === "high" ? "warn-text" : "";
}

/**
 * The deck's lands and roles on one line under its header: "Lands 37 · Ramp 11 · Draw 9 …". The counts are coloured
 * against the guidelines; a role (not Lands) puts its tag in the deck's filter box when clicked.
 */
export function DeckRolesLine({ roles, onPick }: { roles: RoleCount[]; onPick: (role: string) => void }) {
  return (
    <p className="deck-roles small" aria-label="Lands and roles in this deck">
      {roles.map((item, i) => (
        <span key={item.role}>
          {i > 0 && <span className="muted"> · </span>}
          {item.role === "Lands" ? (
            <span>
              {item.role} <span className={numberClass(item.status)}>{item.count}</span>
            </span>
          ) : (
            <button type="button" className="role-btn" title={`Show the ${item.role} cards`} onClick={() => onPick(item.role)}>
              {item.role} <span className={numberClass(item.status)}>{item.count}</span>
            </button>
          )}
        </span>
      ))}
    </p>
  );
}
