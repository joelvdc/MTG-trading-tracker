import { describe, expect, it } from "vitest";
import {
  cardsOf,
  compareSnapshots,
  decklistText,
  defaultSnapshotName,
  deleteSnapshot,
  MAX_SNAPSHOTS,
  MAX_SNAPSHOT_NAME,
  newSnapshotId,
  parseSnapshots,
  renameSnapshot,
  takeSnapshot,
  type SnapCard,
  type SnapLine,
} from "@/lib/decksnapshots";

const NOW = 1_800_000_000_000;

const card = (name: string, quantity = 1, o: Partial<SnapCard> = {}): SnapCard => ({ name, quantity, set: "cmr", number: "263", commander: false, ...o });

/** A deck line as the sync file keeps it (DeckCard), for cardsOf. */
const line = (name: string, quantity: number, setCode: string, number: string, o: Partial<SnapLine> = {}): SnapLine => ({
  card: { name, setCode, collectorNumber: number },
  quantity,
  commander: false,
  maybeboard: false,
  ...o,
});

describe("a deck's snapshots (the app's DeckSnapshots)", () => {
  it("takes a snapshot in front and keeps at most 25, newest first", () => {
    let text: string | null = null;
    for (let i = 0; i <= MAX_SNAPSHOTS; i++) text = takeSnapshot(text, NOW + i, `s${i}`, [card("Sol Ring")], null, null, null, `id${i}`);
    const list = parseSnapshots(text);
    expect(list).toHaveLength(MAX_SNAPSHOTS);
    expect(list[0].name).toBe(`s${MAX_SNAPSHOTS}`);
    expect(list[list.length - 1].name).toBe("s1");
    expect(list[0]).toMatchObject({ id: `id${MAX_SNAPSHOTS}`, at: NOW + MAX_SNAPSHOTS });
  });

  it("keeps what the page showed: the bracket, the win turn and the price", () => {
    const list = parseSnapshots(takeSnapshot(null, NOW, "Before upgrade", [card("Sol Ring", 2)], 3, "T11", 263.28, "abc"));
    expect(list[0]).toMatchObject({ id: "abc", name: "Before upgrade", bracket: 3, winTurn: "T11", eur: 263.28 });
    expect(list[0].cards).toEqual([{ name: "Sol Ring", quantity: 2, set: "cmr", number: "263", commander: false }]);
    // Nothing known: the parts stay null.
    const bare = parseSnapshots(takeSnapshot(null, NOW, "Bare", [], null, null, null, "x"))[0];
    expect([bare.bracket, bare.winTurn, bare.eur]).toEqual([null, null, null]);
  });

  it("names a snapshot after today's UTC date when none is given, and cuts a name to 60 characters", () => {
    expect(defaultSnapshotName(Date.UTC(2026, 10, 27))).toBe("2026-11-27");
    expect(parseSnapshots(takeSnapshot(null, Date.UTC(2026, 10, 27), "   ", [], null, null, null, "a"))[0].name).toBe("2026-11-27");
    expect(parseSnapshots(takeSnapshot(null, NOW, "x".repeat(80), [], null, null, null, "a"))[0].name).toHaveLength(MAX_SNAPSHOT_NAME);
    expect(parseSnapshots(takeSnapshot(null, NOW, "  Before upgrade  ", [], null, null, null, "a"))[0].name).toBe("Before upgrade");
  });

  it("renames a snapshot and deletes it, without snapshots left the list is null", () => {
    const text = takeSnapshot(takeSnapshot(null, NOW, "One", [card("Sol Ring")], 3, null, null, "a"), NOW + 1, "Two", [card("Island")], null, null, null, "b");
    const renamed = parseSnapshots(renameSnapshot(text, "b", "  Between  "));
    expect(renamed.map((s) => s.name)).toEqual(["Between", "One"]);
    // An empty name keeps the one it had.
    expect(parseSnapshots(renameSnapshot(text, "b", "  "))[0].name).toBe("Two");
    const left = deleteSnapshot(text, "b");
    expect(parseSnapshots(left).map((s) => s.name)).toEqual(["One"]);
    expect(deleteSnapshot(left, "a")).toBeNull();
  });

  it("ignores unknown keys and unreadable text", () => {
    expect(parseSnapshots(null)).toEqual([]);
    expect(parseSnapshots("not json")).toEqual([]);
    expect(parseSnapshots('{"snapshots":[]}')).toEqual([]);
    // No id, no name or no date: not a snapshot.
    expect(parseSnapshots(JSON.stringify([{ name: "x", at: 1 }, { id: "a", at: 1 }, { id: "b", name: "n" }]))).toEqual([]);
    // Bad parts of a good one count as unknown; unknown keys go.
    const one = parseSnapshots(
      JSON.stringify([{ id: "a", name: "n", at: 5, cards: [{ name: "X", quantity: 2, set: null, number: null, commander: true, extra: 1 }], bracket: "3", winTurn: 5, eur: "2", more: true }]),
    );
    expect(one).toEqual([{ id: "a", name: "n", at: 5, cards: [{ name: "X", quantity: 2, set: "", number: "", commander: true }], bracket: null, winTurn: null, eur: null }]);
  });

  it("makes ids of 12 random lowercase hex characters", () => {
    const ids = new Set(Array.from({ length: 20 }, () => newSnapshotId()));
    expect(ids.size).toBe(20);
    for (const id of ids) expect(id).toMatch(/^[0-9a-f]{12}$/);
  });
});

describe("a snapshot's cards", () => {
  it("are the main deck only, sorted by name, set and number", () => {
    const cards = cardsOf([
      line("Sol Ring", 1, "CMR", "263"),
      line("Island", 2, "C21", "300"),
      line("Sol Ring", 1, "C21", "120"),
      line("Auntie", 1, "ECL", "201", { commander: true }),
      line("Maybe card", 2, "CMR", "1", { maybeboard: true }),
    ]);
    expect(cards.map((c) => `${c.name} ${c.set} ${c.number}`)).toEqual(["Auntie ecl 201", "Island c21 300", "Sol Ring c21 120", "Sol Ring cmr 263"]);
    expect(cards[0].commander).toBe(true);
    expect(cards.filter((c) => c.commander)).toHaveLength(1);
  });

  it("compare two lists by name, adding up the copies", () => {
    const before = [card("Sol Ring", 1), card("Island", 2), card("Sol Ring", 1, { set: "c21" })];
    const after = [card("Sol Ring", 3), card("Island", 1), card("Forest", 1)];
    expect(compareSnapshots(before, after)).toEqual({ added: { Forest: 1, "Sol Ring": 1 }, removed: { Island: 1 } });
    // Nothing changed: no lists at all.
    expect(compareSnapshots(before, before)).toEqual({ added: {}, removed: {} });
  });

  it("go in a decklist with the commanders first", () => {
    const snapshot = parseSnapshots(
      takeSnapshot(
        null,
        NOW,
        "List",
        [card("Auntie", 2, { set: "ecl", number: "201", commander: true }), card("Sol Ring", 4, { set: "cmm", number: "400" }), card("Forest", 36, { set: "", number: "" })],
        null,
        null,
        null,
        "a",
      ),
    )[0];
    expect(decklistText(snapshot)).toBe(["2 Auntie (ECL) 201", "36 Forest", "4 Sol Ring (CMM) 400"].join("\n"));
  });
});
