// The cards' Commander rules (lib/legality.ts LegalRule) for the legality check: kept in the website's table
// legal_rules by front-face name, looked up on Scryfall (/cards/collection, by Scryfall id) when missing or a week old.
// Shared by every account. Scryfall's Commander legality follows Wizards' banned list, so a week-old copy is fresh
// enough; the weekly email refreshes the cards of the decks it looks at first.
import { accountStore } from "./accounts";
import { USER_AGENT } from "./config";
import { checkLegality, ruleKey, ruleOf, type LegalRule, type Legality, type ScryRuleCard } from "./legality";
import { scryFetch } from "./scrylimit";
import type { SyncDeck } from "./deckshared";

export const RULES_MAX_AGE_MS = 7 * 86_400_000;
const BATCH = 75;

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;

/** A card of a deck: its name, and a printing to look it up by. */
export interface RuleCard {
  name: string;
  scryfallId: string;
}

/**
 * The rules of [cards] by name key: what the table has, plus (when [lookUp]) the missing or week-old ones asked from
 * Scryfall until [deadline]. A failed lookup keeps what the table had.
 */
export async function rulesFor(cards: RuleCard[], lookUp: boolean, now = Date.now(), deadline = now + 20_000, f: Fetch = scryFetch): Promise<Map<string, LegalRule>> {
  const store = accountStore();
  const out = new Map<string, LegalRule>();
  if (!store) return out;
  const byKey = new Map<string, RuleCard>();
  for (const c of cards) if (c.scryfallId && !byKey.has(ruleKey(c.name))) byKey.set(ruleKey(c.name), c);
  const have = await store.legalRules([...byKey.keys()]);
  const stale: RuleCard[] = [];
  for (const [k, c] of byKey) {
    const h = have.get(k);
    if (h?.rule) {
      try {
        out.set(k, JSON.parse(h.rule) as LegalRule);
      } catch {
        // A broken row is simply looked up again.
      }
    }
    if (!h || now - h.at > RULES_MAX_AGE_MS) stale.push(c);
  }
  if (!lookUp) return out;
  for (let i = 0; i < stale.length && Date.now() < deadline; i += BATCH) {
    const part = stale.slice(i, i + BATCH);
    const found = await lookupRules(part, f);
    if (!found) break;
    const rows = part.map((c) => {
      const r = found.get(c.scryfallId) ?? null;
      if (r) out.set(ruleKey(c.name), r);
      return { key: ruleKey(c.name), rule: r ? JSON.stringify(r) : null };
    });
    await store.putLegalRules(rows, now);
  }
  return out;
}

/** Scryfall's cards for these printings, by Scryfall id; null when Scryfall didn't answer. */
async function lookupRules(cards: RuleCard[], f: Fetch): Promise<Map<string, LegalRule> | null> {
  try {
    const r = await f("https://api.scryfall.com/cards/collection", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json", "User-Agent": USER_AGENT },
      body: JSON.stringify({ identifiers: cards.map((c) => ({ id: c.scryfallId })) }),
    });
    if (!r.ok) return null;
    const body = (await r.json()) as { data?: (ScryRuleCard & { id: string })[] };
    return new Map((body.data ?? []).map((c) => [c.id, ruleOf(c)]));
  } catch {
    return null;
  }
}

/** The main deck's lines as the check takes them (the maybeboard left out). */
export function deckLines(d: SyncDeck): { name: string; quantity: number; commander: boolean; scryfallId: string }[] {
  return d.cards.filter((c) => !c.maybeboard).map((c) => ({ name: c.card.name, quantity: c.quantity, commander: c.commander, scryfallId: c.card.scryfallId }));
}

/** A deck's legality, looking up the rules it lacks when [lookUp]. */
export async function deckLegality(d: SyncDeck, lookUp: boolean, now = Date.now(), deadline?: number): Promise<Legality> {
  const lines = deckLines(d);
  const rules = await rulesFor(lines, lookUp, now, deadline);
  return checkLegality(lines, rules);
}
