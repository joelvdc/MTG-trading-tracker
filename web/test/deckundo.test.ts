import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { SyncDeck } from "@/lib/deckshared";
import { clearUndo, currentUndo, labelFor, offerUndo, snapshotOf, subscribeUndo } from "@/components/DeckUndo";

/** A deck as the server saves it before a change (only the shape matters here). */
const snapshot = (name: string) => ({ deck: { id: 7, name }, cards: [] }) as unknown as SyncDeck;

describe("labelFor", () => {
  const words: [string, string][] = [
    ["set-quantity", "Copies changed"],
    ["printing", "Printing changed"],
    ["remove", "Removed"],
    ["move", "Moved"],
    ["auto-all", "Cards auto-categorised"],
    ["add-card", "Card added"],
    ["add-maybe", "Added to the maybeboard"],
    ["category-order", "Categories reordered"],
    ["rename-category", "Category renamed"],
    ["delete-category", "Category deleted"],
    ["add-category", "Category added"],
    ["hide-category", "Category hidden or shown"],
    ["rename", "Deck renamed"],
    ["delete", "Deck deleted"],
    ["retire", "Deck retired or brought back"],
    ["bulk-quantity", "Copies changed"],
    ["bulk-finish", "Finish changed"],
    ["bulk-printing", "Printings changed"],
    ["paste", "Cards added"],
  ];

  it("says in a few words what each action did", () => {
    for (const [action, label] of words) expect(labelFor({ action })).toBe(label);
  });

  it("falls back to a plain wording for anything else", () => {
    expect(labelFor({ action: "something-new" })).toBe("Deck changed");
    expect(labelFor({})).toBe("Deck changed");
  });
});

describe("the undo store", () => {
  beforeEach(() => clearUndo());

  it("offers the last change, with its deck, label and time", () => {
    const before = Date.now();
    offerUndo(7, snapshot("Seven"), "Deck deleted");
    const entry = currentUndo();
    expect(entry).not.toBeNull();
    expect(entry!.deckId).toBe(7);
    expect(entry!.label).toBe("Deck deleted");
    expect(entry!.snapshot.deck.name).toBe("Seven");
    expect(entry!.snapshot.cards).toEqual([]);
    expect(entry!.at).toBeGreaterThanOrEqual(before);
    expect(entry!.at).toBeLessThanOrEqual(Date.now());
  });

  it("keeps only the last change", () => {
    offerUndo(1, snapshot("One"), "Copies changed");
    offerUndo(2, snapshot("Two"), "Deck deleted");
    expect(currentUndo()!.deckId).toBe(2);
    expect(currentUndo()!.label).toBe("Deck deleted");
  });

  it("is over after 30 seconds", () => {
    offerUndo(3, snapshot("Three"), "Moved");
    const { at } = currentUndo()!;
    expect(currentUndo(at + 29_999)).not.toBeNull();
    expect(currentUndo(at + 30_000)).toBeNull();
    expect(currentUndo(at + 60_000)).toBeNull();
  });

  it("forgets the change when it is cleared", () => {
    offerUndo(4, snapshot("Four"), "Removed");
    expect(currentUndo()).not.toBeNull();
    clearUndo();
    expect(currentUndo()).toBeNull();
  });

  it("tells subscribers about a change, until they stop listening", () => {
    let calls = 0;
    const off = subscribeUndo(() => {
      calls++;
    });
    offerUndo(5, snapshot("Five"), "Card added");
    expect(calls).toBe(1);
    clearUndo();
    expect(calls).toBe(2);
    off();
    offerUndo(6, snapshot("Six"), "Card added");
    expect(calls).toBe(2);
  });

  it("works without session storage, as on a server", () => {
    const globals = globalThis as { sessionStorage?: Storage };
    const saved = globals.sessionStorage;
    globals.sessionStorage = undefined;
    try {
      offerUndo(8, snapshot("Eight"), "Card added");
      expect(currentUndo()!.deckId).toBe(8);
    } finally {
      globals.sessionStorage = saved;
    }
  });
});

describe("the undo store with session storage", () => {
  const fake = () => {
    const items = new Map<string, string>();
    return {
      get length() {
        return items.size;
      },
      clear: () => items.clear(),
      getItem: (k: string) => items.get(k) ?? null,
      key: (i: number) => [...items.keys()][i] ?? null,
      removeItem: (k: string) => void items.delete(k),
      setItem: (k: string, v: string) => void items.set(k, v),
    } as Storage;
  };
  const globals = globalThis as { sessionStorage?: Storage };
  let saved: Storage | undefined;

  beforeEach(() => {
    clearUndo();
    saved = globals.sessionStorage;
    globals.sessionStorage = fake();
  });
  afterEach(() => {
    globals.sessionStorage = saved;
    clearUndo();
  });

  it("keeps the change in session storage, so another page finds it", () => {
    offerUndo(9, snapshot("Nine"), "Deck deleted");
    const raw = globals.sessionStorage!.getItem("mtg.deckUndo");
    expect(raw).not.toBeNull();
    const kept = JSON.parse(raw!) as { deckId: number; label: string; at: number; snapshot: SyncDeck };
    expect(kept.deckId).toBe(9);
    expect(kept.label).toBe("Deck deleted");
    expect(typeof kept.at).toBe("number");
    expect(snapshotOf(kept.snapshot)).not.toBeNull();
  });

  it("takes the change out again when it is cleared", () => {
    offerUndo(10, snapshot("Ten"), "Moved");
    clearUndo();
    expect(globals.sessionStorage!.getItem("mtg.deckUndo")).toBeNull();
  });

  it("ignores a kept answer that isn't a saved deck", () => {
    globals.sessionStorage!.setItem("mtg.deckUndo", JSON.stringify({ deckId: 11, label: "Moved", at: Date.now(), snapshot: { deck: {} } }));
    expect(currentUndo()).toBeNull();
    globals.sessionStorage!.setItem("mtg.deckUndo", "not json");
    expect(currentUndo()).toBeNull();
  });
});
