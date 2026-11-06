import type { StatsCard } from "./deckstats";

/** What the win turn counted in the deck, for its "How?" (the app's WinCounted). */
export interface WinCounted {
  creatures: number;
  tokenMakers: number;
  triggers: number;
  drains: number;
  gear: number;
  pumps: number;
  combos: string[];
  tutors: number;
  counting: number;
  sacrifice: number;
  xSpells: number;
  mazeEnd: boolean;
  graveyard: number;
  copies: number;
  planeswalkers: number;
  cascade: number;
}

/** The app's WinOdds: the share of games (whole percent) won by each turn, turn 1 first, and how. */
export interface WinOddsData {
  games: number;
  byTurn: number[];
  byCombo: number[];
  damage: Record<string, number>;
  commanderKnockouts: number;
  counted: WinCounted;
  byMaze: number[];
}

/** The app's power level from the win turn (AppPowerReading). */
export interface AppPowerReading {
  power: number;
  exact: number;
  turn: number | null;
  wonByEnd: number;
  speed: number;
  adjust: number | null;
}

/** What the app's own code (compiled to JavaScript, web/winodds-kt) answers for a deck. */
export interface WinResult {
  odds: WinOddsData;
  label: string;
  fourInTen: number | null;
  usual: number | null;
  nineInTen: number | null;
  firstWin: number | null;
  windowStart: number;
  windowEnd: number;
  damageText: string;
  comboShare: number;
  mazeShare: number;
  power: AppPowerReading;
}

/** The deck's cards as the app's StatsCard (its tokens as the app keeps them: a JSON list, or null). */
export function winInput(cards: StatsCard[]): string {
  return JSON.stringify(
    cards.map((c) => ({
      name: c.name,
      scryfallId: c.scryfallId,
      quantity: c.quantity,
      cmc: c.cmc,
      types: c.types,
      commander: c.commander,
      typeLine: c.typeLine,
      manaCost: c.manaCost,
      producedMana: c.producedMana,
      oracleText: c.oracleText,
      tokens: c.tokens && c.tokens.length > 0 ? JSON.stringify(c.tokens.map((t) => ({ id: t.id, name: t.name, typeLine: t.typeLine }))) : null,
      powerToughness: c.powerToughness ?? null,
      loyalty: c.loyalty ?? null,
    })),
  );
}

/** A short fingerprint of a text (cyrb53), for keeping a deck's result until its cards change. */
export function fingerprint(text: string): string {
  let h1 = 0xdeadbeef;
  let h2 = 0x41c6ce57;
  for (let i = 0; i < text.length; i++) {
    const ch = text.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return (4294967296 * (2097151 & h2) + (h1 >>> 0)).toString(36);
}

/** The turns shown: from the first win to 9 in 10 games (8 to 10 turns). */
export function winWindow(w: WinResult): number[] {
  const out: number[] = [];
  for (let t = w.windowStart; t <= w.windowEnd; t++) out.push(t);
  return out;
}

/** "Damage: combat 52%, commander damage 48%. A combo wins 25% of games. …" (the app's winDamageNote). */
export function winDamageNote(w: WinResult): string {
  let s = "";
  if (w.damageText) s += "Damage: " + w.damageText[0].toLowerCase() + w.damageText.slice(1);
  if (w.comboShare > 0) s += ` A combo wins ${w.comboShare}% of games.`;
  if (w.mazeShare > 0) s += ` Maze's End wins ${w.mazeShare}% of games.`;
  if (w.odds.commanderKnockouts >= 5) s += ` Commander damage knocked a player out in ${w.odds.commanderKnockouts}% of the wins.`;
  return s.trim();
}

export const WIN_SLOW_NOTE =
  "Much of this deck's damage may come from what the app can't read yet (sacrifice triggers, its commander's own ability…), so it's slower here than at the table.";

/** The app's "How?" text for the win turn. */
export function winAbout(n: WinCounted): string {
  return (
    "The app plays the deck alone, with the mana as for casting the commander, every land of a ramp spell and " +
    "Treasures. Each turn it casts its commander, creatures, token makers, equipment and auras and the cards that deal " +
    "damage, the most expensive first; uses tap and loyalty abilities, Clues and Mutagens with the mana left; it puts " +
    "the equipment and auras on its commander (or else its biggest creature), keeps pump spells for combat and " +
    "attacks with everything that can (nobody blocks; each attacker hits one opponent; a creature that tapped for mana or an ability stays back). " +
    "The opponents only draw their cards (for Howling Mine and the like). It wins when all three opponents are at 0, " +
    "a commander has dealt 21 damage to each, a combo wins" +
    (n.mazeEnd ? " or Maze's End finds its tenth Gate" : "") +
    `. Counted: ${n.creatures} creatures, ${n.tokenMakers} token makers, ` +
    `${n.triggers} damage triggers (casting spells, creatures or lands entering), ${n.drains} drain or burn cards, ` +
    `${n.gear} equipment and auras, ${n.pumps} pumps` +
    (n.counting > 0 ? `, ${n.counting} cards that count what's on the battlefield ("for each Shrine you control") or trigger when a kind of permanent enters` : "") +
    (n.sacrifice > 0 ? `, ${n.sacrifice} cards that make sacrifices pay or untap the creature that sacrifices` : "") +
    (n.xSpells > 0 ? `, ${n.xSpells} X spells (cast with the mana left)` : "") +
    (n.graveyard > 0 ? `, ${n.graveyard} cards that fill or use the graveyard (mill, looting, reanimation, casting from it)` : "") +
    (n.copies > 0 ? `, ${n.copies} cards that copy, flicker or cheat in creatures` : "") +
    (n.planeswalkers > 0 ? `, ${n.planeswalkers} planeswalker(s) using a loyalty ability each turn` : "") +
    (n.cascade > 0 ? `, ${n.cascade} cascade, discover or paradigm cards (the spells they find are cast for free)` : "") +
    (n.combos.length === 0 ? ", and no combo that can win." : `, and combos that can win: ${n.combos.join("; ")}.`) +
    (n.tutors > 0 ? ` ${n.tutors} tutor(s) fetch a missing combo piece.` : "") +
    " A combo counts when its cards are at hand with the mana, it wins outright, its loop has a card that turns it " +
    "into life loss, or it makes endless tokens (they win the turn after); what Commander Spellbook says it needs " +
    "first must be there too (an opponent's creature is taken as there from turn 4)." +
    " Not counted: removal, blockers, what the opponents cast and abilities the app can't read from the card text."
  );
}
