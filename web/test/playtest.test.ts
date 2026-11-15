import { describe, expect, it } from "vitest";
import {
  act,
  chanceByTurn,
  landOdds,
  newGame,
  rng,
  shuffleIds,
  ZONE_IDS,
  type Action,
  type CardDef,
  type Game,
  type GameState,
} from "../src/lib/playtest";

const SEED = 12345;

function card(name: string, over: Partial<CardDef> = {}): CardDef {
  return {
    scryfallId: `sf-${name}`,
    name,
    imageUrl: null,
    backImageUrl: null,
    typeLine: "Creature — Elf",
    manaValue: 2,
    isLand: false,
    commander: false,
    ...over,
  };
}

const commander = card("Atraxa, Praetors' Voice", { commander: true, manaValue: 4, backImageUrl: "atraxa-back.png" });
const solRing = card("Sol Ring", { manaValue: 1, typeLine: "Artifact" });
const llanowar = card("Llanowar Elves", { manaValue: 1 });
const forest = (n: number) => card(`Forest ${n}`, { isLand: true, manaValue: 0, typeLine: "Basic Land — Forest" });

/** 1 commander + 43 library cards (Sol Ring, 2 Llanowar Elves, 40 Forests). */
function deck(): { def: CardDef; quantity: number }[] {
  return [
    { def: commander, quantity: 1 },
    { def: solRing, quantity: 1 },
    { def: llanowar, quantity: 2 },
    ...Array.from({ length: 40 }, (_, i) => ({ def: forest(i + 1), quantity: 1 })),
  ];
}

function start(over: { freeMulligan?: boolean; drawOnFirstTurn?: boolean; seed?: number } = {}): Game {
  return newGame(deck(), { seed: SEED, freeMulligan: true, drawOnFirstTurn: false, ...over });
}

/** A game past the opening hand (kept, on the play unless said otherwise). */
function playing(over: { drawOnFirstTurn?: boolean } = {}): Game {
  return act(start(over), { type: "keep" });
}

function idOf(g: Game, name: string): string {
  const found = Object.values(g.state.cards).find((c) => c.def.name === name);
  if (!found) throw new Error(`no ${name} in the game`);
  return found.id;
}

function doIt(g: Game, action: Action): Game {
  return act(g, action);
}

function library(g: Game): string[] {
  return g.state.zones.library;
}

function topNames(g: Game, count: number): string[] {
  return library(g)
    .slice(0, count)
    .map((id) => g.state.cards[id].def.name);
}

describe("random numbers", () => {
  it("gives values in [0, 1) and repeats from the same seed", () => {
    const a = rng(7);
    expect(a.value).toBeGreaterThanOrEqual(0);
    expect(a.value).toBeLessThan(1);
    expect(rng(7)).toEqual(a);
    const b = rng(a.seed);
    expect(rng(a.seed)).toEqual(b);
    expect(b.seed).not.toBe(a.seed);
    expect(rng(8).value).not.toBe(a.value);
  });

  it("shuffles to a permutation, the same way for the same seed", () => {
    const ids = Array.from({ length: 40 }, (_, i) => `c${i + 1}`);
    const one = shuffleIds(ids, 99);
    const two = shuffleIds(ids, 99);
    expect(one).toEqual(two);
    expect([...one.ids].sort()).toEqual([...ids].sort());
    expect(one.ids).not.toEqual(ids);
    expect(shuffleIds(ids, 100).ids).not.toEqual(one.ids);
    expect(ids).toEqual(Array.from({ length: 40 }, (_, i) => `c${i + 1}`)); // the input is untouched
  });
});

describe("a new game", () => {
  it("puts the commander in the command zone, shuffles and draws 7", () => {
    const g = start();
    expect(g.state.phase).toBe("mulligan");
    expect(g.state.turn).toBe(0);
    expect(g.state.mulligans).toBe(0);
    expect(g.state.toBottom).toBe(0);
    expect(g.state.zones.command).toHaveLength(1);
    expect(g.state.cards[g.state.zones.command[0]].def.commander).toBe(true);
    expect(g.state.player.commanderTax[g.state.zones.command[0]]).toBe(0);
    expect(g.state.zones.hand).toHaveLength(7);
    expect(library(g)).toHaveLength(36);
    expect(g.state.zones.battlefield).toHaveLength(0);
    expect(g.state.player).toEqual({ life: 40, poison: 0, energy: 0, experience: 0, commanderTax: { c1: 0 } });
    expect(g.state.log).toEqual([{ turn: 0, text: "Shuffled and drew 7 cards." }]);
    expect(g.past).toEqual([]);
    expect(Object.keys(g.state.cards)).toHaveLength(44);
    expect(g.state.nextId).toBe(45);
    expect(g.state.peek).toBeNull();
    expect(g.state.topRevealed).toBe(false);
  });

  it("is the same for the same seed and different for another", () => {
    expect(start().state.zones.hand).toEqual(start().state.zones.hand);
    expect(library(start())).toEqual(library(start()));
    expect(start({ seed: 999 }).state.zones.hand).not.toEqual(start().state.zones.hand);
  });

  it("keeps every card in exactly one zone", () => {
    const g = start();
    const places = ZONE_IDS.flatMap((zone) => g.state.zones[zone]);
    expect(places).toHaveLength(Object.keys(g.state.cards).length);
    expect(new Set(places).size).toBe(places.length);
  });
});

describe("the opening hand", () => {
  it("keeps on the play: turn 1 and no draw", () => {
    const g = playing();
    expect(g.state.phase).toBe("playing");
    expect(g.state.turn).toBe(1);
    expect(g.state.zones.hand).toHaveLength(7);
    expect(library(g)).toHaveLength(36);
    expect(g.state.log.map((e) => e.text)).toEqual(["Shuffled and drew 7 cards.", "Kept the hand.", "Turn 1."]);
    expect(g.past).toHaveLength(1);
  });

  it("keeps on the draw: turn 1 and one card more", () => {
    const g = playing({ drawOnFirstTurn: true });
    expect(g.state.turn).toBe(1);
    expect(g.state.zones.hand).toHaveLength(8);
    expect(library(g)).toHaveLength(35);
    expect(g.state.log.at(-1)?.text).toBe("Drew 1 card.");
  });

  it("goes to the bottom after a mulligan without a free one", () => {
    const g = act(start({ freeMulligan: false }), { type: "mulligan" });
    expect(g.state.mulligans).toBe(1);
    expect(g.state.phase).toBe("mulligan");
    expect(g.state.zones.hand).toHaveLength(7);
    expect(library(g)).toHaveLength(36);
    expect(g.state.log.at(-1)?.text).toBe("Mulligan to 7 (1st mulligan).");

    const kept = act(g, { type: "keep" });
    expect(kept.state.phase).toBe("bottom");
    expect(kept.state.toBottom).toBe(1);

    const bottomed = kept.state.zones.hand[0];
    const done = act(kept, { type: "bottom", id: bottomed });
    expect(done.state.phase).toBe("playing");
    expect(done.state.turn).toBe(1);
    expect(done.state.zones.hand).toHaveLength(6);
    expect(library(done).at(-1)).toBe(bottomed);
    expect(library(done)).toHaveLength(37);
    expect(done.state.log.at(-1)?.text).toBe("Turn 1.");
  });

  it("needs two bottoms after two mulligans without a free one", () => {
    let g = act(start({ freeMulligan: false }), { type: "mulligan" });
    g = act(g, { type: "mulligan" });
    expect(g.state.mulligans).toBe(2);
    g = act(g, { type: "keep" });
    expect(g.state.phase).toBe("bottom");
    expect(g.state.toBottom).toBe(2);
    const first = g.state.zones.hand[0];
    g = act(g, { type: "bottom", id: first });
    expect(g.state.phase).toBe("bottom");
    expect(g.state.toBottom).toBe(1);
    expect(g.state.zones.hand).toHaveLength(6);
    g = act(g, { type: "bottom", id: g.state.zones.hand[0] });
    expect(g.state.phase).toBe("playing");
    expect(g.state.zones.hand).toHaveLength(5);
    expect(g.state.turn).toBe(1);
  });

  it("treats the first mulligan as free when asked to", () => {
    const g = act(start({ freeMulligan: true }), { type: "mulligan" });
    expect(g.state.mulligans).toBe(1);
    const kept = act(g, { type: "keep" });
    expect(kept.state.phase).toBe("playing");
    expect(kept.state.zones.hand).toHaveLength(7);
    expect(kept.state.toBottom).toBe(0);

    const twice = act(g, { type: "mulligan" });
    const afterTwo = act(twice, { type: "keep" });
    expect(twice.state.mulligans).toBe(2);
    expect(afterTwo.state.phase).toBe("bottom");
    expect(afterTwo.state.toBottom).toBe(1);
  });

  it("keeps a fresh 7 on a mulligan (London)", () => {
    const g = start();
    const before = g.state.zones.hand;
    const after = act(g, { type: "mulligan" });
    expect(after.state.zones.hand).toHaveLength(7);
    expect(library(after)).toHaveLength(36);
    expect(after.state.zones.hand).not.toEqual(before);
    expect(after.past).toHaveLength(1);
    expect(after.state.log[0].text).toBe("Shuffled and drew 7 cards.");
  });

  it("ignores mulligan actions at the wrong time or with the wrong card", () => {
    const running = playing();
    expect(act(running, { type: "keep" })).toBe(running);
    expect(act(running, { type: "mulligan" })).toBe(running);
    expect(act(running, { type: "bottom", id: running.state.zones.hand[0] })).toBe(running);
    const mulligan = start({ freeMulligan: false });
    expect(act(mulligan, { type: "bottom", id: mulligan.state.zones.hand[0] })).toBe(mulligan);
    const bottoming = act(act(start({ freeMulligan: false }), { type: "mulligan" }), { type: "keep" });
    expect(act(bottoming, { type: "bottom", id: "c999" })).toBe(bottoming);
    expect(act(bottoming, { type: "bottom", id: library(bottoming)[0] })).toBe(bottoming);
  });
});

describe("drawing, milling, exiling and shuffling", () => {
  it("draws the top cards into the hand", () => {
    const g = playing();
    const top = library(g).slice(0, 3);
    const after = act(g, { type: "draw", count: 3 });
    expect(after.state.zones.hand.slice(-3)).toEqual(top);
    expect(library(after)).toHaveLength(33);
    expect(after.state.log.at(-1)?.text).toBe("Drew 3 cards.");
    expect(act(g, { type: "draw" }).state.log.at(-1)?.text).toBe("Drew 1 card.");
  });

  it("stops drawing at an empty library", () => {
    let g = playing();
    g = act(g, { type: "draw", count: 100 });
    expect(library(g)).toHaveLength(0);
    expect(g.state.zones.hand).toHaveLength(43);
    expect(act(g, { type: "draw" })).toBe(g);
    expect(act(g, { type: "mill", count: 2 })).toBe(g);
    expect(act(g, { type: "exileTop", count: 2 })).toBe(g);
    expect(act(g, { type: "shuffle" })).toBe(g);
  });

  it("mills the top cards into the graveyard", () => {
    const g = playing();
    const top = library(g).slice(0, 2);
    const after = act(g, { type: "mill", count: 2 });
    expect(after.state.zones.graveyard).toEqual(top);
    expect(after.state.log.at(-1)?.text).toBe("Milled 2 cards.");
    expect(act(g, { type: "mill", count: 0 })).toBe(g);
  });

  it("exiles the top cards, face down if asked", () => {
    const g = playing();
    const top = library(g).slice(0, 2);
    const after = act(g, { type: "exileTop", count: 2, faceDown: true });
    expect(after.state.zones.exile).toEqual(top);
    expect(after.state.cards[top[0]].faceDown).toBe(true);
    expect(after.state.log.at(-1)?.text).toBe("Exiled 2 cards face down.");
    expect(library(after)).toHaveLength(34);
  });

  it("shuffles the same way for the same seed and leaves the cards alone", () => {
    const g = playing();
    const one = act(g, { type: "shuffle" });
    const two = act(g, { type: "shuffle" });
    expect(one.state.zones.library).toEqual(two.state.zones.library);
    expect(one.state.seed).toBe(two.state.seed);
    expect([...one.state.zones.library].sort()).toEqual([...library(g)].sort());
    expect(one.state.zones.library).not.toEqual(library(g));
    expect(one.state.log.at(-1)?.text).toBe("Shuffled the library.");
  });

  it("does game actions only while playing", () => {
    const mulligan = start();
    expect(act(mulligan, { type: "draw" })).toBe(mulligan);
    expect(act(mulligan, { type: "peek", count: 2, mode: "scry" })).toBe(mulligan);
  });

  it("follows the top card when it is revealed", () => {
    const g = playing();
    const firstTop = library(g)[0];
    const revealed = act(g, { type: "revealTop", on: true });
    expect(revealed.state.topRevealed).toBe(true);
    expect(revealed.state.cards[firstTop].revealed).toBe(true);
    expect(revealed.state.zones.library.filter((id) => revealed.state.cards[id].revealed)).toEqual([firstTop]);

    const drawn = act(revealed, { type: "draw" });
    const secondTop = library(drawn)[0];
    expect(drawn.state.cards[firstTop].revealed).toBe(false);
    expect(drawn.state.cards[secondTop].revealed).toBe(true);

    const off = act(drawn, { type: "revealTop", on: false });
    expect(off.state.topRevealed).toBe(false);
    expect(off.state.zones.library.some((id) => off.state.cards[id].revealed)).toBe(false);
    expect(act(off, { type: "revealTop", on: false })).toBe(off);
  });
});

describe("looking at the top", () => {
  it("scries: the chosen cards go on top and on the bottom in order", () => {
    const g = playing();
    const peeked = library(g).slice(0, 3);
    const rest = library(g).slice(3);
    const looking = act(g, { type: "peek", count: 3, mode: "scry" });
    expect(looking.state.peek).toEqual({ ids: peeked, mode: "scry" });
    expect(looking.state.log.at(-1)?.text).toBe("Scry 3.");

    const done = act(looking, { type: "peekDone", top: [peeked[1], peeked[2]], bottom: [peeked[0]], graveyard: [] });
    expect(done.state.peek).toBeNull();
    expect(library(done)[0]).toBe(peeked[1]);
    expect(library(done)[1]).toBe(peeked[2]);
    expect(library(done).slice(2, 4)).toEqual(rest.slice(0, 2));
    expect(library(done).at(-1)).toBe(peeked[0]);
    expect(library(done)).toHaveLength(36);
    expect(done.state.log.at(-1)?.text).toBe("Scry 3: 2 on top, 1 on the bottom.");
  });

  it("surveils: the chosen card goes to the graveyard", () => {
    const g = playing();
    const peeked = library(g).slice(0, 3);
    const looking = act(g, { type: "peek", count: 3, mode: "surveil" });
    const done = act(looking, { type: "peekDone", top: [peeked[2]], bottom: [peeked[1]], graveyard: [peeked[0]] });
    expect(done.state.zones.graveyard).toEqual([peeked[0]]);
    expect(library(done)[0]).toBe(peeked[2]);
    expect(library(done).at(-1)).toBe(peeked[1]);
    expect(done.state.log.at(-1)?.text).toBe("Surveilled 3: 1 on top, 1 on the bottom, 1 to the graveyard.");
  });

  it("ignores lists that are not exactly the peeked cards", () => {
    const g = playing();
    const peeked = library(g).slice(0, 3);
    const looking = act(g, { type: "peek", count: 3, mode: "scry" });
    expect(act(looking, { type: "peekDone", top: [peeked[0]], bottom: [], graveyard: [] })).toBe(looking);
    expect(act(looking, { type: "peekDone", top: [peeked[0], peeked[0]], bottom: [peeked[1], peeked[2]], graveyard: [] })).toBe(looking);
    expect(act(looking, { type: "peekDone", top: [...peeked, "c999"], bottom: [], graveyard: [] })).toBe(looking);
    const noPeek = playing();
    expect(act(noPeek, { type: "peekDone", top: [], bottom: [], graveyard: [] })).toBe(noPeek);
  });

  it("cancels a look, and refuses a second one", () => {
    const g = playing();
    const looking = act(g, { type: "peek", count: 2, mode: "look" });
    expect(act(looking, { type: "peek", count: 2, mode: "scry" })).toBe(looking);
    const cancelled = act(looking, { type: "peekCancel" });
    expect(cancelled.state.peek).toBeNull();
    expect(library(cancelled)).toEqual(library(g));
    expect(act(cancelled, { type: "peekCancel" })).toBe(cancelled);
  });

  it("peeks no more cards than the library has", () => {
    let g = playing();
    g = act(g, { type: "draw", count: 33 });
    expect(library(g)).toHaveLength(3);
    const looking = act(g, { type: "peek", count: 5, mode: "look" });
    expect(looking.state.peek?.ids).toEqual(library(g));
    expect(looking.state.log.at(-1)?.text).toBe("Looking at the top 3 cards.");
    const none = playing();
    expect(act(none, { type: "peek", count: 0, mode: "scry" })).toBe(none);
  });
});

describe("moving cards", () => {
  it("puts a card on the battlefield with a spot of its own", () => {
    const g = playing();
    const ring = idOf(g, "Sol Ring");
    const moved = act(g, { type: "move", id: ring, to: "battlefield" });
    expect(moved.state.zones.hand).not.toContain(ring);
    expect(moved.state.zones.battlefield).toEqual([ring]);
    const card = moved.state.cards[ring];
    expect(card.x).toBe(10);
    expect(card.y).toBe(10);
    expect(moved.state.log.at(-1)?.text).toBe("Sol Ring → battlefield.");
  });

  it("cascades board spots and puts lands lower", () => {
    const g = playing();
    const ring = idOf(g, "Sol Ring");
    const elf = idOf(g, "Llanowar Elves");
    const land = g.state.zones.hand.find((id) => g.state.cards[id].def.isLand) as string;
    let board = act(g, { type: "move", id: ring, to: "battlefield" });
    board = act(board, { type: "move", id: elf, to: "battlefield" });
    board = act(board, { type: "move", id: land, to: "battlefield" });
    expect(board.state.cards[ring].x).toBe(10);
    expect(board.state.cards[elf].x).toBe(13);
    expect(board.state.cards[land].x).toBe(16);
    expect(board.state.cards[land].y).toBe(76);
    expect(board.state.cards[ring].y).toBe(10);
  });

  it("resets a card that leaves the battlefield", () => {
    let g = playing();
    const cmd = g.state.zones.command[0];
    g = act(g, { type: "move", id: cmd, to: "battlefield" });
    g = act(g, { type: "flip", id: cmd });
    g = act(g, { type: "tap", id: cmd });
    g = act(g, { type: "counter", id: cmd, kind: "loyalty", delta: 3 });
    g = act(g, { type: "faceDown", id: cmd });
    expect(g.state.cards[cmd].flipped).toBe(true);
    expect(g.state.cards[cmd].counters).toHaveLength(1);

    const gone = act(g, { type: "move", id: cmd, to: "graveyard" });
    const moved = gone.state.cards[cmd];
    expect(moved.tapped).toBe(false);
    expect(moved.flipped).toBe(false);
    expect(moved.faceDown).toBe(false);
    expect(moved.counters).toEqual([]);
    expect(gone.state.zones.graveyard).toEqual([cmd]);
    expect(gone.state.log.at(-1)?.text).toBe("Atraxa, Praetors' Voice → graveyard.");
  });

  it("vanishes a token that leaves the battlefield", () => {
    let g = playing();
    g = act(g, { type: "token", def: card("Treasure", { typeLine: "Artifact — Treasure" }), count: 1 });
    const token = g.state.zones.battlefield[0];
    expect(g.state.cards[token].token).toBe(true);
    const gone = act(g, { type: "move", id: token, to: "graveyard" });
    expect(gone.state.cards[token]).toBeUndefined();
    expect(gone.state.zones.graveyard).toEqual([]);
    expect(gone.state.zones.battlefield).toEqual([]);
    expect(gone.state.log.at(-1)?.text).toBe("Treasure left the battlefield and vanished.");
  });

  it("raises the commander tax each time the commander is cast from the command zone", () => {
    let g = playing();
    const cmd = g.state.zones.command[0];
    g = act(g, { type: "move", id: cmd, to: "battlefield" });
    expect(g.state.player.commanderTax[cmd]).toBe(2);
    expect(g.state.log.at(-2)?.text).toBe("Cast Atraxa, Praetors' Voice from the command zone (tax +0).");
    expect(g.state.log.at(-1)?.text).toBe("Atraxa, Praetors' Voice → battlefield.");

    // Going back to the command zone costs nothing; the next cast costs 2 more.
    g = act(g, { type: "move", id: cmd, to: "command" });
    expect(g.state.player.commanderTax[cmd]).toBe(2);
    expect(g.state.zones.command).toContain(cmd);
    expect(g.state.log.at(-1)?.text).toBe("Atraxa, Praetors' Voice → command zone.");
    g = act(g, { type: "move", id: cmd, to: "battlefield" });
    expect(g.state.player.commanderTax[cmd]).toBe(4);
    expect(g.state.log.at(-2)?.text).toBe("Cast Atraxa, Praetors' Voice from the command zone (tax +2).");
  });

  it("leaves the commander tax alone for cards that are not the commander", () => {
    let g = playing();
    const cmd = g.state.zones.command[0];
    const ring = idOf(g, "Sol Ring");
    g = act(g, { type: "move", id: ring, to: "command" });
    expect(g.state.player.commanderTax[cmd]).toBe(0);
    g = act(g, { type: "move", id: ring, to: "battlefield" });
    expect(g.state.player.commanderTax[cmd]).toBe(0);
    expect(g.state.log.at(-1)?.text).toBe("Sol Ring → battlefield.");
  });

  it("puts cards on the top, the bottom and n from the top of the library", () => {
    const g = playing();
    const ring = idOf(g, "Sol Ring");
    const rest = library(g).filter((id) => id !== ring);

    const onTop = act(g, { type: "move", id: ring, to: "library" });
    expect(library(onTop)[0]).toBe(ring);
    expect(library(onTop).slice(1)).toEqual(rest);

    const onBottom = act(g, { type: "move", id: ring, to: "library", position: "bottom" });
    expect(library(onBottom).at(-1)).toBe(ring);
    expect(library(onBottom).slice(0, -1)).toEqual(rest);

    const third = act(g, { type: "move", id: ring, to: "library", position: 2 });
    expect(library(third)[2]).toBe(ring);
    expect(library(third)[0]).toBe(rest[0]);
    expect(library(third)[1]).toBe(rest[1]);

    const beyond = act(g, { type: "move", id: ring, to: "library", position: 999 });
    expect(library(beyond).at(-1)).toBe(ring);
  });

  it("ignores moves it cannot make", () => {
    const g = playing();
    const ring = idOf(g, "Sol Ring");
    expect(act(g, { type: "move", id: "c999", to: "graveyard" })).toBe(g);
    const onBoard = act(g, { type: "move", id: ring, to: "battlefield" });
    expect(act(onBoard, { type: "move", id: ring, to: "battlefield" })).toBe(onBoard);
    expect(act(onBoard, { type: "move", id: ring, to: "elsewhere" as never })).toBe(onBoard);
  });
});

describe("tutors", () => {
  it("searches a library card out and shuffles the rest", () => {
    const g = playing();
    const target = library(g)[10];
    const name = g.state.cards[target].def.name;
    const rest = library(g).filter((id) => id !== target);
    const after = act(g, { type: "tutor", id: target, to: "hand" });
    expect(after.state.zones.hand).toContain(target);
    expect(library(after)).toHaveLength(35);
    expect([...library(after)].sort()).toEqual([...rest].sort());
    expect(library(after)).not.toEqual(rest);
    expect(after.state.log.at(-1)?.text).toBe(`Searched for ${name}.`);
    expect(act(g, { type: "tutor", id: target, to: "hand" }).state.zones.library).toEqual(library(after));
  });

  it("leaves the library in order when the card goes back in it", () => {
    const g = playing();
    const target = library(g)[10];
    const rest = library(g).filter((id) => id !== target);
    const top = act(g, { type: "tutor", id: target, to: "library", position: "top" });
    expect(library(top)[0]).toBe(target);
    expect(library(top).slice(1)).toEqual(rest);
    expect(library(top)).toHaveLength(36);
    const bottom = act(g, { type: "tutor", id: target, to: "library", position: "bottom" });
    expect(library(bottom).at(-1)).toBe(target);
    expect(library(bottom).slice(0, -1)).toEqual(rest);
    expect(act(g, { type: "tutor", id: target, to: "library" }).state.zones.library[0]).toBe(target);
  });

  it("leaves the commander tax alone (only a cast from the command zone raises it)", () => {
    let g = playing();
    const cmd = g.state.zones.command[0];
    g = act(g, { type: "move", id: cmd, to: "library", position: "top" });
    expect(g.state.player.commanderTax[cmd]).toBe(0);

    // Searching the commander out of the library doesn't count as casting it.
    const toCommand = act(g, { type: "tutor", id: cmd, to: "command" });
    expect(toCommand.state.player.commanderTax[cmd]).toBe(0);
    expect(toCommand.state.zones.command).toContain(cmd);

    const toBoard = act(g, { type: "tutor", id: cmd, to: "battlefield" });
    expect(toBoard.state.zones.battlefield).toContain(cmd);
    expect(toBoard.state.player.commanderTax[cmd]).toBe(0);

    // Casting it from the command zone still does.
    const cast = act(toCommand, { type: "move", id: cmd, to: "battlefield" });
    expect(cast.state.player.commanderTax[cmd]).toBe(2);
  });

  it("needs a card that is in the library", () => {
    const g = playing();
    const hand = g.state.zones.hand[0];
    expect(act(g, { type: "tutor", id: hand, to: "hand" })).toBe(g);
    expect(act(g, { type: "tutor", id: "c999", to: "hand" })).toBe(g);
  });
});

describe("card state", () => {
  it("taps and untaps, and untaps everything", () => {
    let g = playing();
    const ring = idOf(g, "Sol Ring");
    const elf = idOf(g, "Llanowar Elves");
    g = act(g, { type: "move", id: ring, to: "battlefield" });
    g = act(g, { type: "move", id: elf, to: "battlefield" });
    g = act(g, { type: "tap", id: ring });
    g = act(g, { type: "tap", id: elf });
    expect(g.state.cards[ring].tapped).toBe(true);
    expect(g.state.log.at(-1)?.text).toBe("Tapped Llanowar Elves.");
    const tappedRing = act(g, { type: "tap", id: ring });
    expect(tappedRing.state.cards[ring].tapped).toBe(false);
    expect(tappedRing.state.log.at(-1)?.text).toBe("Untapped Sol Ring.");

    const all = act(g, { type: "untapAll" });
    expect(all.state.zones.battlefield.every((id) => !all.state.cards[id].tapped)).toBe(true);
    expect(all.state.log.at(-1)?.text).toBe("Untapped everything.");
    expect(act(all, { type: "untapAll" })).toBe(all);
    expect(act(g, { type: "tap", id: g.state.zones.hand[0] })).toBe(g);
  });

  it("flips only a card with a back face", () => {
    let g = playing();
    const cmd = g.state.zones.command[0];
    const ring = idOf(g, "Sol Ring");
    g = act(g, { type: "move", id: ring, to: "battlefield" });
    expect(act(g, { type: "flip", id: ring })).toBe(g);
    g = act(g, { type: "move", id: cmd, to: "battlefield" });
    const back = act(g, { type: "flip", id: cmd });
    expect(back.state.cards[cmd].flipped).toBe(true);
    expect(back.state.log.at(-1)?.text).toBe("Atraxa, Praetors' Voice flipped to its back face.");
    const front = act(back, { type: "flip", id: cmd });
    expect(front.state.cards[cmd].flipped).toBe(false);
  });

  it("turns a card face down and back, hiding its name", () => {
    let g = playing();
    const ring = idOf(g, "Sol Ring");
    g = act(g, { type: "move", id: ring, to: "battlefield" });
    const down = act(g, { type: "faceDown", id: ring });
    expect(down.state.cards[ring].faceDown).toBe(true);
    const tapped = act(down, { type: "tap", id: ring });
    expect(tapped.state.log.at(-1)?.text).toBe("Tapped a face-down card.");
    const up = act(down, { type: "faceDown", id: ring });
    expect(up.state.cards[ring].faceDown).toBe(false);
    expect(up.state.log.at(-1)?.text).toBe("Turned Sol Ring face up.");
  });

  it("adds counters and takes them away again", () => {
    let g = playing();
    const elf = idOf(g, "Llanowar Elves");
    g = act(g, { type: "move", id: elf, to: "battlefield" });
    g = act(g, { type: "counter", id: elf, kind: "+1/+1", delta: 2 });
    expect(g.state.cards[elf].counters).toEqual([{ kind: "+1/+1", amount: 2 }]);
    expect(g.state.log.at(-1)?.text).toBe("+1/+1 counter on Llanowar Elves (now 2).");
    g = act(g, { type: "counter", id: elf, kind: "+1/+1", delta: 1 });
    expect(g.state.cards[elf].counters).toEqual([{ kind: "+1/+1", amount: 3 }]);
    g = act(g, { type: "counter", id: elf, kind: "+1/+1", delta: -2 });
    expect(g.state.cards[elf].counters).toEqual([{ kind: "+1/+1", amount: 1 }]);
    g = act(g, { type: "counter", id: elf, kind: "+1/+1", delta: -1 });
    expect(g.state.cards[elf].counters).toEqual([]);
    expect(g.state.log.at(-1)?.text).toBe("Removed 1 +1/+1 counter from Llanowar Elves.");
  });

  it("has +1/+1 and -1/-1 counters cancel each other", () => {
    let g = playing();
    const elf = idOf(g, "Llanowar Elves");
    g = act(g, { type: "move", id: elf, to: "battlefield" });
    g = act(g, { type: "counter", id: elf, kind: "+1/+1", delta: 2 });
    g = act(g, { type: "counter", id: elf, kind: "-1/-1", delta: 1 });
    expect(g.state.cards[elf].counters).toEqual([{ kind: "+1/+1", amount: 1 }]);
    g = act(g, { type: "counter", id: elf, kind: "-1/-1", delta: 3 });
    expect(g.state.cards[elf].counters).toEqual([{ kind: "-1/-1", amount: 2 }]);
    g = act(g, { type: "counter", id: elf, kind: "-1/-1", delta: -2 });
    expect(g.state.cards[elf].counters).toEqual([]);
  });

  it("keeps any other counter kind apart", () => {
    let g = playing();
    const ring = idOf(g, "Sol Ring");
    g = act(g, { type: "move", id: ring, to: "battlefield" });
    g = act(g, { type: "counter", id: ring, kind: "charge", delta: 2 });
    g = act(g, { type: "counter", id: ring, kind: "+1/+1", delta: 1 });
    expect(g.state.cards[ring].counters).toEqual([
      { kind: "charge", amount: 2 },
      { kind: "+1/+1", amount: 1 },
    ]);
    g = act(g, { type: "counter", id: ring, kind: "charge", delta: -2 });
    expect(g.state.cards[ring].counters).toEqual([{ kind: "+1/+1", amount: 1 }]);
    expect(act(g, { type: "counter", id: ring, kind: "charge", delta: 0 })).toBe(g);
    expect(act(g, { type: "counter", id: g.state.zones.hand[0], kind: "charge", delta: 1 })).toBe(g);
  });
});

describe("copies and tokens", () => {
  it("makes a token copy of a battlefield card next to it", () => {
    let g = playing();
    const ring = idOf(g, "Sol Ring");
    g = act(g, { type: "move", id: ring, to: "battlefield" });
    const after = act(g, { type: "copy", id: ring });
    expect(after.state.zones.battlefield).toHaveLength(2);
    const copyId = after.state.zones.battlefield[1];
    const copy = after.state.cards[copyId];
    expect(copyId).toMatch(/^t\d+$/);
    expect(copy.token).toBe(true);
    expect(copy.copyOf).toBe(ring);
    expect(copy.def).toBe(solRing);
    expect(copy.x).toBe(after.state.cards[ring].x + 3);
    expect(after.state.log.at(-1)?.text).toBe("Created a token copy of Sol Ring.");
    expect(act(g, { type: "copy", id: g.state.zones.hand[0] })).toBe(g);
  });

  it("creates token cards, up to twenty at a time", () => {
    let g = playing();
    const treasure = card("Treasure", { typeLine: "Artifact — Treasure" });
    g = act(g, { type: "token", def: treasure, count: 3 });
    expect(g.state.zones.battlefield).toHaveLength(3);
    expect(g.state.log.at(-1)?.text).toBe("Created 3 Treasure tokens.");
    const ids = g.state.zones.battlefield;
    expect(ids.every((id) => /^t\d+$/.test(id) && g.state.cards[id].token)).toBe(true);
    expect(new Set(ids).size).toBe(3);
    expect(ids.map((id) => g.state.cards[id].x)).toEqual([10, 13, 16]);

    const many = act(playing(), { type: "token", def: treasure, count: 50 });
    expect(many.state.zones.battlefield).toHaveLength(20);
    expect(many.state.log.at(-1)?.text).toBe("Created 20 Treasure tokens.");

    const one = act(playing(), { type: "token", def: treasure, count: 1 });
    expect(one.state.log.at(-1)?.text).toBe("Created 1 Treasure token.");
    const none = playing();
    expect(act(none, { type: "token", def: treasure, count: 0 })).toBe(none);
    expect(g.state.cards[g.state.zones.battlefield[0]].copyOf).toBeNull();
  });
});

describe("the player and the turns", () => {
  it("changes life, poison, energy and experience", () => {
    let g = playing();
    g = act(g, { type: "player", key: "life", delta: -7 });
    expect(g.state.player.life).toBe(33);
    expect(g.state.log.at(-1)?.text).toBe("Life 40 → 33.");
    g = act(g, { type: "player", key: "poison", delta: 3 });
    expect(g.state.player.poison).toBe(3);
    g = act(g, { type: "player", key: "poison", delta: -5 });
    expect(g.state.player.poison).toBe(0);
    g = act(g, { type: "player", key: "energy", delta: 2 });
    g = act(g, { type: "player", key: "experience", delta: 1 });
    expect([g.state.player.energy, g.state.player.experience]).toEqual([2, 1]);
    expect(act(g, { type: "player", key: "life", delta: 0 })).toBe(g);
  });

  it("changes the commander tax by hand", () => {
    let g = playing();
    const cmd = g.state.zones.command[0];
    g = act(g, { type: "tax", id: cmd, delta: 2 });
    expect(g.state.player.commanderTax[cmd]).toBe(2);
    expect(g.state.log.at(-1)?.text).toBe("Commander tax for Atraxa, Praetors' Voice is now 2.");
    g = act(g, { type: "tax", id: cmd, delta: -10 });
    expect(g.state.player.commanderTax[cmd]).toBe(0);
    const ring = idOf(g, "Sol Ring");
    expect(act(g, { type: "tax", id: ring, delta: 2 })).toBe(g);
  });

  it("starts the next turn with everything untapped and one card more", () => {
    let g = playing();
    const ring = idOf(g, "Sol Ring");
    g = act(g, { type: "move", id: ring, to: "battlefield" });
    g = act(g, { type: "tap", id: ring });
    const hand = g.state.zones.hand.length;
    const next = act(g, { type: "nextTurn" });
    expect(next.state.turn).toBe(2);
    expect(next.state.cards[ring].tapped).toBe(false);
    expect(next.state.zones.hand).toHaveLength(hand + 1);
    expect(next.state.log.slice(-2).map((e) => e.text)).toEqual(["Turn 2.", "Drew 1 card."]);
    expect(next.state.log.at(-2)?.turn).toBe(2);
  });

  it("keeps drawing nothing on an empty library", () => {
    let g = playing();
    g = act(g, { type: "draw", count: 40 });
    const next = act(g, { type: "nextTurn" });
    expect(next.state.turn).toBe(2);
    expect(library(next)).toHaveLength(0);
    expect(next.state.log.at(-1)?.text).toBe("Turn 2.");
  });
});

describe("undo", () => {
  it("restores the exact state before the last change", () => {
    const g = playing();
    const ring = idOf(g, "Sol Ring");
    const one = act(g, { type: "move", id: ring, to: "battlefield" });
    const two = act(one, { type: "tap", id: ring });
    const back = act(two, { type: "undo" });
    expect(back.state).toEqual(one.state);
    expect(back.past).toHaveLength(one.past.length);
    const backMore = act(back, { type: "undo" });
    expect(backMore.state).toEqual(g.state);
    expect(backMore.past).toHaveLength(1);
    const undoKeep = act(backMore, { type: "undo" });
    expect(undoKeep.state.phase).toBe("mulligan");
    expect(undoKeep.past).toEqual([]);
    const nothing = act(undoKeep, { type: "undo" });
    expect(nothing).toBe(undoKeep);
  });

  it("does not take a step for actions that change nothing", () => {
    const g = playing();
    const tapped = act(act(g, { type: "draw", count: 40 }), { type: "draw" });
    expect(act(tapped, { type: "draw" }).past).toHaveLength(tapped.past.length);
    expect(act(g, { type: "tap", id: "c999" }).past).toHaveLength(g.past.length);
  });

  it("keeps at most 100 states", () => {
    let g = playing();
    for (let i = 0; i < 220; i++) g = act(g, { type: "nextTurn" });
    expect(g.state.turn).toBe(221);
    expect(g.past).toHaveLength(100);
    expect(g.state.log).toHaveLength(200);
    const back = act(g, { type: "undo" });
    expect(back.state.turn).toBe(220);
    expect(back.past).toHaveLength(99);
  });

  it("does not take a step when a card is dragged around the board", () => {
    let g = playing();
    const ring = idOf(g, "Sol Ring");
    g = act(g, { type: "move", id: ring, to: "battlefield" });
    const prior = act(g, { type: "undo" }).state;
    const before = g.past.length;
    const dragged = act(g, { type: "position", id: ring, x: 150, y: -5 });
    expect(dragged.past).toHaveLength(before);
    expect(dragged.state.cards[ring].x).toBe(100);
    expect(dragged.state.cards[ring].y).toBe(0);
    expect(act(dragged, { type: "position", id: ring, x: 100, y: 0 })).toBe(dragged);
    expect(act(dragged, { type: "position", id: dragged.state.zones.hand[0], x: 20, y: 20 })).toBe(dragged);
    expect(act(dragged, { type: "undo" }).state).toEqual(prior);
  });
});

describe("restart", () => {
  it("deals the same cards again from a new seed", () => {
    let g = playing();
    g = act(g, { type: "draw", count: 3 });
    const fresh = act(g, { type: "restart", seed: 777 });
    expect(fresh.state.phase).toBe("mulligan");
    expect(fresh.state.turn).toBe(0);
    expect(fresh.state.zones.hand).toHaveLength(7);
    expect(library(fresh)).toHaveLength(36);
    expect(fresh.state.player.life).toBe(40);
    expect(fresh.state.log).toEqual([{ turn: 0, text: "Shuffled and drew 7 cards." }]);
    expect(fresh.past).toEqual([]);
    expect(fresh.state.freeMulligan).toBe(true);
    expect(fresh.state.drawOnFirstTurn).toBe(false);
    expect(Object.keys(fresh.state.cards)).toHaveLength(44);
    expect(act(fresh, { type: "restart", seed: 777 }).state.zones.hand).toEqual(fresh.state.zones.hand);
    expect(act(fresh, { type: "restart", seed: 778 }).state.zones.hand).not.toEqual(fresh.state.zones.hand);
  });
});

describe("the numbers", () => {
  it("counts land odds as a hypergeometric distribution", () => {
    const total = [0, 1, 2, 3, 4, 5, 6, 7].reduce((sum, k) => sum + landOdds(99, 37, 7, k), 0);
    expect(total).toBeCloseTo(1, 10);
    expect(landOdds(99, 37, 7, 3)).toBeCloseTo(0.288, 2);
    expect(landOdds(99, 37, 7, 0)).toBeGreaterThan(0);
    expect(landOdds(99, 37, 7, 8)).toBe(0);
    expect(landOdds(99, 37, 7, -1)).toBe(0);
    expect(landOdds(99, 99, 7, 7)).toBe(1);
    expect(landOdds(0, 0, 7, 0)).toBe(0);
  });

  it("counts the chance of seeing a card by a turn", () => {
    expect(chanceByTurn(99, 1, 7)).toBeCloseTo(0.0707, 3);
    expect(chanceByTurn(99, 1, 7)).toBeCloseTo(7 / 99, 8);
    expect(chanceByTurn(99, 0, 7)).toBe(0);
    expect(chanceByTurn(99, 4, 0)).toBe(0);
    expect(chanceByTurn(99, 4, 99)).toBe(1);
    expect(chanceByTurn(99, 4, 7)).toBeLessThan(chanceByTurn(99, 4, 14));
    expect(chanceByTurn(99, 4, 14)).toBeLessThan(chanceByTurn(99, 4, 21));
    expect(chanceByTurn(99, 4, 7)).toBeCloseTo(1 - (95 / 99) * (94 / 98) * (93 / 97) * (92 / 96) * (91 / 95) * (90 / 94) * (89 / 93), 10);
  });
});

describe("a whole little game", () => {
  it("plays a few turns without ever losing a card", () => {
    let g = playing({ drawOnFirstTurn: true });
    const cmd = g.state.zones.command[0];
    const ring = idOf(g, "Sol Ring");
    const elf = idOf(g, "Llanowar Elves");
    const land = g.state.zones.hand.find((id) => g.state.cards[id].def.isLand) as string;
    const steps: Action[] = [
      { type: "move", id: cmd, to: "battlefield" },
      { type: "move", id: land, to: "battlefield" },
      { type: "move", id: ring, to: "battlefield" },
      { type: "tap", id: ring },
      { type: "move", id: elf, to: "battlefield" },
      { type: "nextTurn" },
      { type: "draw" },
      { type: "mill", count: 1 },
      { type: "token", def: card("Treasure", { typeLine: "Artifact — Treasure" }), count: 2 },
      { type: "player", key: "life", delta: -3 },
      { type: "nextTurn" },
      { type: "undo" },
    ];
    for (const step of steps) g = doIt(g, step);
    const state: GameState = g.state;
    const placed = ZONE_IDS.flatMap((zone) => state.zones[zone]);
    // the two tokens are extra; the 44 dealt cards are all still somewhere
    expect(placed.filter((id) => id.startsWith("c"))).toHaveLength(44);
    expect(new Set(placed).size).toBe(placed.length);
    expect(state.turn).toBe(2);
    expect(state.player.life).toBe(37);
    expect(state.log.length).toBeGreaterThan(5);
  });
});
