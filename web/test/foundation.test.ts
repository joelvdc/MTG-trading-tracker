import { randomBytes } from "node:crypto";
import { readFileSync } from "node:fs";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanFolder } from "@/lib/config";
import { format, toEur } from "@/lib/money";
import { davUrl, parseEntries, parseListing, sameOrigin } from "@/lib/nextcloud";
import { best, compactGuide, parseEcb, priceOf, type PriceFile } from "@/lib/prices";
import { pickFoilType, toCardRef } from "@/lib/scryfall";
import { seal, unseal, type Account } from "@/lib/session";
import { decode, encode, parse, wishlist, WRITER } from "@/lib/syncfile";

const secret = randomBytes(32).toString("base64");
const account: Account = { server: "https://cloud.example.com", loginName: "me@example.com", userId: "joel", appPassword: "x", folder: "MTG Trader" };

describe("the login cookie", () => {
  it("reads back what it sealed", () => {
    expect(unseal(seal(account, 60_000, 1000, secret), 2000, secret)).toEqual(account);
  });
  it("expires", () => {
    expect(unseal(seal(account, 60_000, 1000, secret), 61_001, secret)).toBeNull();
  });
  it("can't be changed or read with another key", () => {
    const token = seal(account, 60_000, 1000, secret);
    const parts = token.split(".");
    const body = Buffer.from(parts[2], "base64url");
    body[0] ^= 1;
    expect(unseal([parts[0], parts[1], body.toString("base64url")].join("."), 2000, secret)).toBeNull();
    expect(unseal(token, 2000, randomBytes(32).toString("base64"))).toBeNull();
    expect(unseal("rubbish", 2000, secret)).toBeNull();
  });
  it("refuses a missing or short key", () => {
    expect(() => seal(account, 1, 0, "c2hvcnQ=")).toThrow();
  });
});

describe("Nextcloud", () => {
  it("builds WebDAV addresses in the sync folder", () => {
    expect(davUrl(account, "sync.json.gz")).toBe("https://cloud.example.com/remote.php/dav/files/joel/MTG%20Trader/sync.json.gz");
    expect(davUrl({ ...account, folder: "Games/MTG Trader web test" }, "web-backups/a.gz")).toBe(
      "https://cloud.example.com/remote.php/dav/files/joel/Games/MTG%20Trader%20web%20test/web-backups/a.gz",
    );
  });
  it("only accepts addresses on the same server", () => {
    expect(sameOrigin("https://cloud.example.com/login/v2/poll", "https://cloud.example.com")).toBe(true);
    expect(sameOrigin("https://evil.example/login", "https://cloud.example.com")).toBe(false);
    expect(sameOrigin("http://cloud.example.com/x", "https://cloud.example.com")).toBe(false);
  });
  it("reads file names from a folder listing", () => {
    const xml = `<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
      <d:response><d:href>/remote.php/dav/files/joel/MTG%20Trader/web-backups/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
      <d:response><d:href>/remote.php/dav/files/joel/MTG%20Trader/web-backups/sync-2026-11-05.json.gz</d:href><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
    </d:multistatus>`;
    expect(parseListing(xml)).toEqual(["sync-2026-11-05.json.gz"]);
  });
  it("lists folders and files for the folder picker, without the folder itself", () => {
    const xml = `<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
      <d:response><d:href>/remote.php/dav/files/joel/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
      <d:response><d:href>/remote.php/dav/files/joel/MTG%20Trader%20web%20test/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
      <d:response><d:href>/remote.php/dav/files/joel/Documents/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
      <d:response><d:href>/remote.php/dav/files/joel/sync.json.gz</d:href><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
    </d:multistatus>`;
    expect(parseEntries(xml)).toEqual({ folders: ["Documents", "MTG Trader web test"], files: ["sync.json.gz"] });
  });
  it("accepts sensible folder names only", () => {
    expect(cleanFolder(" MTG Trader ")).toBe("MTG Trader");
    expect(cleanFolder("/Games//MTG Trader/")).toBe("Games/MTG Trader");
    expect(cleanFolder("../other")).toBeNull();
    expect(cleanFolder("")).toBeNull();
  });
});

describe("prices", () => {
  const guide = {
    createdAt: "2026-11-05T02:49:44+0100",
    priceGuides: [
      { idProduct: 1, avg: 0.07, low: 0.02, trend: 0.07, avg1: 0.04, avg7: 0.08, avg30: 0.05, "avg-foil": null, "low-foil": 0.05, "trend-foil": 0.38, "avg1-foil": 0.59, "avg7-foil": 0.33, "avg30-foil": 0.36 },
      { idProduct: 2, avg: null, low: null, trend: null, avg1: null, avg7: null, avg30: null },
    ],
  };
  const file: PriceFile = { ...compactGuide(guide), madeAt: 0, rates: { date: "", perEuro: { DKK: 7.46 } } };
  const card = { ...toCardRef({ id: "1", name: "X", set: "abc", cardmarket_id: 1, finishes: ["nonfoil", "foil"], prices: { eur: "9.99", eur_foil: null } }) };

  it("keep only products with a price", () => {
    expect(Object.keys(file.products)).toEqual(["1"]);
    expect(file.products["1"]).toEqual([0.07, 0.07, 0.02, 0.04, 0.08, 0.05, 0.38, null, 0.05, 0.59, 0.33, 0.36]);
  });
  it("pick the chosen figure, else the app's fallbacks", () => {
    expect(best([null, 2, 3, 4, 5, 6], "trend")).toBe(2);
    expect(best([null, null, 3, null, null, 6], "avg1")).toBe(6);
    expect(priceOf(card, false, "low", file).eur).toBe(0.02);
    expect(priceOf(card, true, "avg", file).eur).toBe(0.38); // no foil average: trend
    expect(priceOf(card, false, "trend", file).trendPct).toBeCloseTo(40);
  });
  it("fall back to Scryfall's saved price", () => {
    expect(priceOf({ ...card, cardmarketId: 99 }, false, "trend", file).eur).toBe(9.99);
    expect(priceOf({ ...card, cardmarketId: 99 }, false, "trend", null).eur).toBe(9.99);
  });
  it("read the ECB's rates", () => {
    const xml = `<Cube time='2026-11-04'><Cube currency='USD' rate='1.0812'/><Cube currency='DKK' rate='7.4601'/></Cube>`;
    expect(parseEcb(xml)).toEqual({ date: "2026-11-04", perEuro: { USD: 1.0812, DKK: 7.4601 } });
  });
  it("show money like the app", () => {
    expect(format(1234.5, "EUR")).toBe("€1,234.50");
    expect(format(11.2, "DKK")).toBe("11.20 kr.");
    expect(toEur(74.6, "DKK", { date: "", perEuro: { DKK: 7.46 } })).toBeCloseTo(10);
    expect(toEur(3, null, null)).toBe(3);
  });
});

describe("Scryfall cards", () => {
  it("become the app's CardRef", () => {
    const ref = toCardRef({
      id: "abc",
      name: "Sol Ring",
      set: "c21",
      set_name: "Commander 2021",
      collector_number: "263",
      rarity: "uncommon",
      cardmarket_id: 556001,
      card_faces: [{ image_uris: { normal: "https://img/front.jpg" } }],
      prices: { eur: "1.50", eur_foil: "" },
      finishes: ["foil", "etched"],
      promo_types: ["boosterfun", "surgefoil"],
      flavor_name: "Barrow-Downs",
    });
    expect(ref).toMatchObject({ imageUrl: "https://img/front.jpg", fallbackEur: 1.5, fallbackEurFoil: null, hasNonFoil: false, hasFoil: true, hasEtched: true, foilType: "surgefoil", flavorName: "Barrow-Downs" });
  });
  it("pick special foils like the app", () => {
    expect(pickFoilType(["rainbowfoil", "surgefoil"])).toBe("surgefoil");
    expect(pickFoilType(["newshinyfoil"])).toBe("newshinyfoil");
    expect(pickFoilType(["foil"])).toBeNull();
  });
});

describe("saving the sync file", () => {
  const fixture = readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8");
  let remote: { bytes: Uint8Array; etag: string };
  let files: Map<string, Uint8Array>;
  let phoneWritesBeforeNextSave: number;

  beforeEach(() => {
    vi.resetModules();
    remote = { bytes: encode(parse(fixture)), etag: '"v1"' };
    files = new Map();
    phoneWritesBeforeNextSave = 0;
    vi.doMock("@/lib/nextcloud", async (orig) => ({
      ...(await orig<typeof import("@/lib/nextcloud")>()),
      getFile: vi.fn(async (_a: Account, name: string) => (name === "sync.json.gz" ? { ...remote } : null)),
      putFile: vi.fn(async (_a: Account, _n: string, bytes: Uint8Array, ifMatch: string | null) => {
        if (phoneWritesBeforeNextSave > 0) {
          // A phone saves first: it changed the file, so the website's version is out of date.
          phoneWritesBeforeNextSave--;
          const f = decode(remote.bytes);
          wishlist(f)[0].quantity = 7;
          remote = { bytes: encode(f), etag: `"p${phoneWritesBeforeNextSave}"` };
          return { ok: false, conflict: true };
        }
        if (ifMatch !== remote.etag) return { ok: false, conflict: true };
        remote = { bytes, etag: '"v2"' };
        return { ok: true, etag: '"v2"' };
      }),
      listFiles: vi.fn(async () => [...files.keys()].map((k) => k.split("/").pop()!)),
      makeFolder: vi.fn(async () => undefined),
      putPlain: vi.fn(async (_a: Account, name: string, bytes: Uint8Array) => void files.set(name, bytes)),
      deleteFile: vi.fn(async (_a: Account, name: string) => void files.delete(name)),
    }));
  });
  afterEach(() => vi.doUnmock("@/lib/nextcloud"));

  it("saves, signs the file and keeps a backup of the day", async () => {
    const { editSync } = await import("@/lib/store");
    await editSync(account, (f) => (wishlist(f)[0].notes = "hi"), () => Date.UTC(2026, 10, 5));
    const saved = decode(remote.bytes);
    expect(wishlist(saved)[0].notes).toBe("hi");
    expect(saved.writtenBy).toBe(WRITER);
    expect([...files.keys()]).toEqual(["web-backups/sync-2026-11-05.json.gz"]);
    expect(decode(files.get("web-backups/sync-2026-11-05.json.gz")!).writtenBy).toBe("Test phone");
  });

  it("redoes the change on the phone's newer file instead of overwriting it", async () => {
    const { editSync } = await import("@/lib/store");
    phoneWritesBeforeNextSave = 1;
    await editSync(account, (f) => (wishlist(f)[1].notes = "web"), () => Date.UTC(2026, 10, 5));
    const saved = decode(remote.bytes);
    expect(wishlist(saved)[0].quantity).toBe(7); // the phone's change
    expect(wishlist(saved)[1].notes).toBe("web"); // and the website's
  });

  it("gives up after a few tries", async () => {
    const { editSync } = await import("@/lib/store");
    phoneWritesBeforeNextSave = 10;
    await expect(editSync(account, () => undefined, () => Date.UTC(2026, 10, 5))).rejects.toThrow(/kept changing/);
  });

  it("keeps ten daily backups", async () => {
    const { editSync } = await import("@/lib/store");
    for (let d = 1; d <= 12; d++) await editSync(account, () => undefined, () => Date.UTC(2026, 10, d));
    const names = [...files.keys()].sort();
    expect(names.length).toBe(10);
    expect(names[0]).toBe("web-backups/sync-2026-11-03.json.gz");
  });

  it("won't write a file from a newer app", async () => {
    const f = parse(fixture);
    f.format = 99;
    remote = { bytes: encode(f), etag: '"v9"' };
    const { editSync, ReadOnlyFile } = await import("@/lib/store");
    await expect(editSync(account, () => undefined)).rejects.toBeInstanceOf(ReadOnlyFile);
  });
});
