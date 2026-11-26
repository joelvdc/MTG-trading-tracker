import { describe, expect, it } from "vitest";
import { FACE_BREAK } from "../src/lib/deckstats";
import { describeCounters, isSaga, startCounters } from "../src/lib/entercounters";
import { act, newGame, type CardDef, type Game } from "../src/lib/playtest";

const c = (name: string, typeLine: string, oracleText: string | null = null, more: { loyalty?: string | null; defense?: string | null } = {}) =>
  startCounters({ name, typeLine, oracleText, ...more });

describe("startCounters", () => {
  it("gives a planeswalker its loyalty", () => {
    expect(c("Jace Beleren", "Legendary Planeswalker — Jace", "+2: Each player draws a card.", { loyalty: "3" })).toEqual([
      { kind: "loyalty", amount: 3 },
    ]);
  });

  it("gives no loyalty when only the back face is a planeswalker", () => {
    expect(c("Valki, God of Lies // Tibalt, Cosmic Impostor", "Legendary Creature — God // Legendary Planeswalker — Devil", null, { loyalty: "" })).toEqual([]);
  });

  it("gives a saga one lore counter, also a double-faced one", () => {
    expect(c("History of Benalia", "Enchantment — Saga", "(As this Saga enters...)\nI, II — Create a 2/2 white Knight.")).toEqual([
      { kind: "lore", amount: 1 },
    ]);
    expect(c("The Restoration of Eiganjo // Architect of Restoration", "Enchantment — Saga // Enchantment Creature — Fox Monk")).toEqual([
      { kind: "lore", amount: 1 },
    ]);
    expect(isSaga("Enchantment — Saga")).toBe(true);
    expect(isSaga("Enchantment Creature — Fox // Enchantment — Saga")).toBe(false);
  });

  it("gives a battle its defense", () => {
    expect(c("Invasion of Zendikar // Awakened Skyclave", "Battle — Siege // Creature — Elemental", null, { defense: "3" })).toEqual([
      { kind: "defense", amount: 3 },
    ]);
  });

  it("reads 'enters with N counters' in today's and older wording", () => {
    expect(c("Polukranos, World Eater", "Legendary Creature — Hydra", "This creature enters with three +1/+1 counters on it.")).toEqual([
      { kind: "+1/+1", amount: 3 },
    ]);
    expect(c("Polukranos, World Eater", "Legendary Creature — Hydra", "Polukranos enters the battlefield with three +1/+1 counters on it.")).toEqual([
      { kind: "+1/+1", amount: 3 },
    ]);
    expect(c("Vivid Crag", "Land", "Vivid Crag enters the battlefield tapped with two charge counters on it.\n{T}: Add {R}.")).toEqual([
      { kind: "charge", amount: 2 },
    ]);
    expect(c("Oil Pump", "Artifact", "Flash\nThis artifact enters with an oil counter on it.")).toEqual([{ kind: "oil", amount: 1 }]);
  });

  it("leaves counters that depend on the game to the player", () => {
    expect(c("Walking Ballista", "Artifact Creature — Construct", "This creature enters with X +1/+1 counters on it.")).toEqual([]);
    expect(c("Everflowing Chalice", "Artifact", "Multikicker {2}\nThis artifact enters with a charge counter on it for each time it was kicked.")).toEqual([]);
    expect(c("Kicked Beast", "Creature — Beast", "Kicker {2}\nIf this creature was kicked, it enters with two +1/+1 counters on it.")).toEqual([]);
    expect(c("Wizard Friend", "Creature — Human", "If you control a Wizard, this creature enters with two +1/+1 counters on it.")).toEqual([]);
    expect(c("Crusade Lord", "Creature — Human", "Each other creature you control enters with an additional +1/+1 counter on it.")).toEqual([]);
    expect(c("Ravenous Thing", "Creature — Dinosaur", "Ravenous (This creature enters with X +1/+1 counters on it.)")).toEqual([]);
  });

  it("reads Vanishing, Fading, Modular and Graft, not their reminder text", () => {
    expect(c("Keldon Marauders", "Creature — Human Warrior", "Vanishing 2 (This creature enters with two time counters on it.)")).toEqual([
      { kind: "time", amount: 2 },
    ]);
    expect(c("Blastoderm", "Creature — Beast", "Shroud\nFading 3 (This creature enters with three fade counters on it.)")).toEqual([
      { kind: "fade", amount: 3 },
    ]);
    expect(c("Arcbound Ravager", "Artifact Creature — Beast", "Sacrifice an artifact: Put a +1/+1 counter on this creature.\nModular 1 (...)")).toEqual([
      { kind: "+1/+1", amount: 1 },
    ]);
    expect(c("Flying Graft", "Creature — Mutant", "Flying, graft 2")).toEqual([{ kind: "+1/+1", amount: 2 }]);
    expect(c("Arcbound Wanderer", "Artifact Creature — Golem", "Modular—Sunburst (...)")).toEqual([]);
  });

  it("reads only the front face", () => {
    const text = `Flying${FACE_BREAK}This creature enters with two +1/+1 counters on it.`;
    expect(c("Front // Back", "Creature — Spirit // Creature — Spirit", text)).toEqual([]);
  });

  it("describes counters for the log", () => {
    expect(describeCounters([{ kind: "loyalty", amount: 3 }, { kind: "lore", amount: 1 }])).toBe("3 loyalty, 1 lore");
  });
});

function def(name: string, over: Partial<CardDef> = {}): CardDef {
  return { scryfallId: `sf-${name}`, name, imageUrl: null, backImageUrl: null, typeLine: "Creature", manaValue: 2, isLand: false, commander: false, ...over };
}

const walker = def("Jace Beleren", { typeLine: "Legendary Planeswalker — Jace", startCounters: [{ kind: "loyalty", amount: 3 }] });
const saga = def("History of Benalia", { typeLine: "Enchantment — Saga", startCounters: [{ kind: "lore", amount: 1 }], saga: true });
const walkerCommander = def("Commander Walker", { commander: true, typeLine: "Legendary Planeswalker — X", startCounters: [{ kind: "loyalty", amount: 5 }] });

function game(): Game {
  const filler = Array.from({ length: 30 }, (_, i) => ({ def: def(`Forest ${i}`, { isLand: true, typeLine: "Basic Land — Forest" }), quantity: 1 }));
  return act(newGame([{ def: walkerCommander, quantity: 1 }, { def: walker, quantity: 1 }, { def: saga, quantity: 1 }, ...filler], {
    seed: 7, freeMulligan: true, drawOnFirstTurn: false,
  }), { type: "keep" });
}

const idOf = (g: Game, name: string) => Object.values(g.state.cards).find((x) => x.def.name === name)!.id;
const counters = (g: Game, id: string) => g.state.cards[id].counters;

describe("the playtester puts starting counters on cards", () => {
  it("when a card is moved onto the battlefield, and again after it left and came back", () => {
    let g = game();
    const id = idOf(g, "Jace Beleren");
    g = act(g, { type: "move", id, to: "battlefield" });
    expect(counters(g, id)).toEqual([{ kind: "loyalty", amount: 3 }]);
    expect(g.state.log.at(-1)?.text).toBe("Jace Beleren → battlefield (3 loyalty).");
    g = act(g, { type: "counter", id, kind: "loyalty", delta: 2 });
    g = act(g, { type: "move", id, to: "graveyard" });
    expect(counters(g, id)).toEqual([]);
    g = act(g, { type: "move", id, to: "battlefield" });
    expect(counters(g, id)).toEqual([{ kind: "loyalty", amount: 3 }]);
  });

  it("when the commander is cast and when a card is searched onto the battlefield", () => {
    let g = game();
    const cmd = idOf(g, "Commander Walker");
    g = act(g, { type: "move", id: cmd, to: "battlefield" });
    expect(counters(g, cmd)).toEqual([{ kind: "loyalty", amount: 5 }]);
    const sagaId = idOf(g, "History of Benalia");
    if (!g.state.zones.library.includes(sagaId)) g = act(g, { type: "move", id: sagaId, to: "library", position: "top" });
    g = act(g, { type: "tutor", id: sagaId, to: "battlefield" });
    expect(counters(g, sagaId)).toEqual([{ kind: "lore", amount: 1 }]);
    expect(g.state.log.at(-1)?.text).toBe("Searched for History of Benalia (1 lore).");
  });

  it("on a token copy, with the card's starting counters rather than the original's", () => {
    let g = game();
    const id = idOf(g, "Jace Beleren");
    g = act(g, { type: "move", id, to: "battlefield" });
    g = act(g, { type: "counter", id, kind: "loyalty", delta: 4 });
    g = act(g, { type: "copy", id });
    const copy = g.state.zones.battlefield.find((x) => x !== id)!;
    expect(counters(g, copy)).toEqual([{ kind: "loyalty", amount: 3 }]);
  });

  it("not on a face-down card", () => {
    let g = game();
    const id = idOf(g, "Jace Beleren");
    g = act(g, { type: "move", id, to: "exile" });
    g.state.cards[id].faceDown = true;
    g = act(g, { type: "move", id, to: "battlefield" });
    expect(counters(g, id)).toEqual([]);
  });

  it("a saga gets one more lore counter each new turn", () => {
    let g = game();
    const id = idOf(g, "History of Benalia");
    g = act(g, { type: "move", id, to: "battlefield" });
    g = act(g, { type: "nextTurn" });
    expect(counters(g, id)).toEqual([{ kind: "lore", amount: 2 }]);
    const jace = idOf(g, "Jace Beleren");
    g = act(g, { type: "move", id: jace, to: "battlefield" });
    g = act(g, { type: "nextTurn" });
    expect(counters(g, id)).toEqual([{ kind: "lore", amount: 3 }]);
    expect(counters(g, jace)).toEqual([{ kind: "loyalty", amount: 3 }]);
  });
});
