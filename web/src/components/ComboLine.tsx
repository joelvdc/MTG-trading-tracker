import { comboResultText, type SaltCombo } from "@/lib/deckshared";

/** A combo in words: its cards (or the others than [cardName]), what it does and a link to Commander Spellbook (the app's ComboLine). */
export function ComboLine({ combo, cardName }: { combo: SaltCombo; cardName?: string }) {
  const others = cardName ? combo.cards.filter((c) => c.split(" // ")[0].trim().toLowerCase() !== cardName.split(" // ")[0].trim().toLowerCase()) : combo.cards;
  const what = comboResultText(combo);
  return (
    <li>
      <span className={combo.counts ? "combo-dot counts" : "combo-dot"} aria-hidden="true" />
      <span className="grow small">
        {cardName ? "Combo with " : ""}
        {others.join(" + ")}
        {combo.needsBoard ? " (needs more on the battlefield)" : ""}
        <br />
        <span className="muted">
          {[combo.counts ? "Counts for the bracket" : "Doesn't count for the bracket", what].filter(Boolean).join(" · ")}
          {combo.prerequisites.length > 0 && ` · first: ${combo.prerequisites.join("; ")}`}
        </span>
      </span>
      {combo.spellbookUri?.startsWith("https://") && (
        <a className="small" href={combo.spellbookUri} target="_blank" rel="noreferrer">
          Spellbook ↗
        </a>
      )}
    </li>
  );
}
