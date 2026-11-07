import { describe, expect, it } from "vitest";
import { isQueryError, matches, parseQuery, type QueryCard, scryfallBatches, unsupportedKeys } from "@/lib/cardquery";

const base: QueryCard = {
  name: "", typeLine: "", oracleText: "", manaCost: "", mv: 0, colors: [], colorIdentity: [], power: null, toughness: null, loyalty: null,
  rarity: null, setCode: "", commander: false, gameChanger: false, priceEur: null, category: "",
};
const card = (o: Partial<QueryCard>): QueryCard => ({ ...base, ...o });

const goblin = card({
  name: "Goblin Guide", typeLine: "Creature — Goblin Scout", oracleText: "Haste\nWhenever Goblin Guide attacks, defending player reveals the top card of their library.",
  manaCost: "{R}", mv: 1, colors: ["R"], colorIdentity: ["R"], power: "2", toughness: "2", rarity: "rare", setCode: "zen", priceEur: 3.5, category: "Aggro",
});
const jace = card({
  name: "Jace, the Mind Sculptor", typeLine: "Legendary Planeswalker — Jace", oracleText: "+2: Look at the top card of target player's library.\n−1: Return target creature to its owner's hand.",
  manaCost: "{2}{U}{U}", mv: 4, colors: ["U"], colorIdentity: ["U", "B"], loyalty: "3", rarity: "mythic", setCode: "a25", priceEur: 40, gameChanger: true, category: "Planeswalkers",
});
const fire = card({
  name: "Fire // Ice", typeLine: "Instant // Instant", oracleText: "Fire deals 2 damage divided as you choose.\nTap target permanent. Draw a card.",
  manaCost: "{1}{R} // {1}{U}", mv: 4, colors: ["R", "U"], colorIdentity: ["R", "U"], rarity: "uncommon", setCode: "mh2", priceEur: 0.5, category: "Removal",
});
const solRing = card({ name: "Sol Ring", typeLine: "Artifact", oracleText: "{T}: Add {C}{C}.", manaCost: "{1}", mv: 1, rarity: "uncommon", setCode: "c21", priceEur: 1.5, category: "Ramp" });
const forest = card({ name: "Forest", typeLine: "Basic Land — Forest", oracleText: "({T}: Add {G}.)", colorIdentity: ["G"], rarity: "common", setCode: "afc", priceEur: 0.1, category: "Lands" });
const golem = card({ name: "Gruul Golem", typeLine: "Artifact Creature — Golem", manaCost: "{4}", mv: 4, power: "2", toughness: "3", commander: true, category: "Ramp" });
const all = [goblin, jace, fire, solRing, forest, golem];

const run = (q: string): string[] => {
  const p = parseQuery(q);
  if (isQueryError(p)) throw new Error(p.error);
  return all.filter((c) => matches(p, c)).map((c) => c.name);
};
const err = (q: string): string => {
  const p = parseQuery(q);
  if (!isQueryError(p)) throw new Error("expected an error for " + q);
  return p.error;
};

describe("parseQuery and matches", () => {
  it("empty filter keeps everything", () => expect(run("  ")).toHaveLength(all.length));
  it("bare words and phrases search the name", () => {
    expect(run("goblin")).toEqual(["Goblin Guide"]);
    expect(run('"mind sculptor"')).toEqual(["Jace, the Mind Sculptor"]);
    expect(run("sol ring")).toEqual(["Sol Ring"]);
  });
  it("exact name", () => {
    expect(run('!"Sol Ring"')).toEqual(["Sol Ring"]);
    expect(run('!"Sol"')).toEqual([]);
    expect(run("!forest")).toEqual(["Forest"]);
  });
  it("oracle text", () => {
    expect(run("o:haste c:red")).toEqual(["Goblin Guide"]);
    expect(run('o:"draw a card"')).toEqual(["Fire // Ice"]);
    expect(run("o:~ attacks")).toEqual([]);
    expect(run('o:"whenever ~ attacks"')).toEqual(["Goblin Guide"]);
  });
  it("type line", () => {
    expect(run("t:creature -t:legendary")).toEqual(["Goblin Guide", "Gruul Golem"]);
    expect(run("t:planeswalker")).toEqual(["Jace, the Mind Sculptor"]);
  });
  it("colours", () => {
    expect(run("c:red")).toEqual(["Goblin Guide", "Fire // Ice"]);
    expect(run("c:ur")).toEqual(["Fire // Ice"]);
    expect(run("c=u")).toEqual(["Jace, the Mind Sculptor"]);
    expect(run("c<=ur")).toEqual(["Goblin Guide", "Jace, the Mind Sculptor", "Fire // Ice", "Sol Ring", "Forest", "Gruul Golem"]);
    expect(run("c=c")).toEqual(["Sol Ring", "Forest", "Gruul Golem"]);
    expect(run("c:m")).toEqual(["Fire // Ice"]);
    expect(run("c>r")).toEqual(["Fire // Ice"]);
    expect(run("c<ur")).toEqual(["Goblin Guide", "Jace, the Mind Sculptor", "Sol Ring", "Forest", "Gruul Golem"]);
    expect(run("-c:w")).toHaveLength(all.length);
    expect(run("c:izzet")).toEqual(["Fire // Ice"]);
  });
  it("colour identity defaults to fitting inside", () => {
    expect(run("id<=ub")).toEqual(["Jace, the Mind Sculptor", "Sol Ring", "Gruul Golem"]);
    expect(run("id:esper")).toEqual(["Jace, the Mind Sculptor", "Sol Ring", "Gruul Golem"]);
    expect(run("id>=ub")).toEqual(["Jace, the Mind Sculptor"]);
    expect(run("id:g")).toEqual(["Sol Ring", "Forest", "Gruul Golem"]);
  });
  it("numbers", () => {
    expect(run("mv>=3")).toEqual(["Jace, the Mind Sculptor", "Fire // Ice", "Gruul Golem"]);
    expect(run("mv:1")).toEqual(["Goblin Guide", "Sol Ring"]);
    expect(run("mv!=1 mv<4")).toEqual(["Forest"]);
    expect(run("pow>2 or loy>=4")).toEqual([]);
    expect(run("pow>=2 or loy>=3")).toEqual(["Goblin Guide", "Jace, the Mind Sculptor", "Gruul Golem"]);
    expect(run("tou>pow")).toEqual(["Gruul Golem"]);
    expect(run("mv:even")).toEqual(["Jace, the Mind Sculptor", "Fire // Ice", "Forest", "Gruul Golem"]);
    expect(run("mv:odd")).toEqual(["Goblin Guide", "Sol Ring"]);
  });
  it("grouping and or", () => {
    expect(run("(t:instant or t:sorcery) mv<=2")).toEqual([]);
    expect(run("(t:instant or t:artifact) mv<=2")).toEqual(["Sol Ring"]);
    expect(run("t:instant OR t:land")).toEqual(["Fire // Ice", "Forest"]);
    expect(run("-(t:instant or t:land)")).toEqual(["Goblin Guide", "Jace, the Mind Sculptor", "Sol Ring", "Gruul Golem"]);
    expect(run("t:creature (c:r or (t:artifact (mv>3 or mv<1)))")).toEqual(["Goblin Guide", "Gruul Golem"]);
  });
  it("rarity", () => {
    expect(run("r>=r")).toEqual(["Goblin Guide", "Jace, the Mind Sculptor"]);
    expect(run("r:uncommon")).toEqual(["Fire // Ice", "Sol Ring"]);
    expect(run("r<u")).toEqual(["Forest"]);
  });
  it("mana cost", () => {
    expect(run("m:{U}{U}")).toEqual(["Jace, the Mind Sculptor"]);
    expect(run("m:{U}")).toEqual(["Jace, the Mind Sculptor", "Fire // Ice"]);
    expect(run("m:2UU")).toEqual(["Jace, the Mind Sculptor"]);
  });
  it("set, price, category, is:", () => {
    expect(run("s:afc")).toEqual(["Forest"]);
    expect(run("e:zen")).toEqual(["Goblin Guide"]);
    expect(run("eur<1")).toEqual(["Fire // Ice", "Forest"]);
    expect(run("eur>=3.5")).toEqual(["Goblin Guide", "Jace, the Mind Sculptor"]);
    expect(run("cat:ramp")).toEqual(["Sol Ring", "Gruul Golem"]);
    expect(run("is:commander")).toEqual(["Gruul Golem"]);
    expect(run("is:gamechanger")).toEqual(["Jace, the Mind Sculptor"]);
    expect(run("is:split")).toEqual(["Fire // Ice"]);
  });
  it("tags", () => {
    const p = parseQuery("tag:burn");
    if (isQueryError(p)) throw new Error(p.error);
    expect(matches(p, card({ tags: ["Burn spell"] }))).toBe(true);
    expect(matches(p, card({}))).toBe(false);
  });

  it("reports keys it can't answer and doesn't exclude on them", () => {
    const p = parseQuery("otag:ramp c:r f:commander is:reprint");
    if (isQueryError(p)) throw new Error(p.error);
    expect(unsupportedKeys(p)).toEqual(["f", "is:reprint", "otag"]);
    expect(all.filter((c) => matches(p, c)).map((c) => c.name)).toEqual(["Goblin Guide", "Fire // Ice"]);
    const neg = parseQuery("-otag:ramp");
    if (isQueryError(neg)) throw new Error(neg.error);
    expect(matches(neg, solRing)).toBe(true);
    const either = parseQuery("art:x or c:w");
    if (isQueryError(either)) throw new Error(either.error);
    expect(matches(either, solRing)).toBe(true);
  });

  it("gives plain parse errors", () => {
    expect(err("(t:instant")).toMatch(/^Missing \) after "t:instant"/);
    expect(err("t:instant)")).toMatch(/^Unexpected \)/);
    expect(err("()")).toBe("Empty ( ).");
    expect(err('o:"draw')).toMatch(/^Missing closing "/);
    expect(err("c:q")).toMatch(/isn't a colour/);
    expect(err("mv>=x")).toMatch(/Expected a number after mv>=/);
    expect(err("r:legendary")).toMatch(/isn't a rarity/);
    expect(err("c:")).toBe("Nothing after c:");
    expect(err("t:a or")).toBe("Nothing after or.");
    expect(err("or t:a")).toBe("Nothing before or.");
    expect(err("t>creature")).toMatch(/only works with :/);
  });
});

describe("scryfallBatches", () => {
  it("makes one search for a few names", () => {
    expect(scryfallBatches("otag:ramp", ["Sol Ring", "Arcane Signet"])).toEqual(['(otag:ramp) (!"Sol Ring" or !"Arcane Signet")']);
  });
  it("escapes quotes and backslashes in names", () => {
    expect(scryfallBatches("art:x", ['Say "Hi"', "A\\B"])).toEqual(['(art:x) (!"Say \\"Hi\\"" or !"A\\\\B")']);
  });
  it("drops duplicates and gives nothing for no names", () => {
    expect(scryfallBatches("art:x", ["A", "A"])).toEqual(['(art:x) (!"A")']);
    expect(scryfallBatches("art:x", [])).toEqual([]);
  });
  it("splits so each search URL stays short, keeping every name once", () => {
    const names = Array.from({ length: 250 }, (_, i) => `Some Fairly Long Card Name Number ${i}`);
    const batches = scryfallBatches("otag:ramp", names);
    expect(batches.length).toBeGreaterThan(1);
    const seen: string[] = [];
    for (const b of batches) {
      const url = `https://api.scryfall.com/cards/search?${new URLSearchParams({ q: b, unique: "cards" })}`;
      expect(url.length).toBeLessThan(1500);
      seen.push(...[...b.matchAll(/!"([^"]*)"/g)].map((m) => m[1]));
    }
    expect(seen).toEqual(names);
  });
});
