import { describe, expect, it } from "vitest";
import { accountStore, resetMemoryStore } from "@/lib/accounts";
import { DEFAULT_COLUMNS } from "@/lib/collcolumns";
import { EMPTY_FILTER } from "@/lib/colquery";
import { cleanView, cleanViews, MAX_VIEWS } from "@/lib/savedviews";

// Saved views on the Collection page (website only, 2026-11-13).

const view = { id: "v1", name: "Trade fodder", binder: "~", search: "bolt", filter: { ...EMPTY_FILTER, decks: "SPARE", minPrice: 2 }, sorts: [{ field: "VALUE", reversed: false }], columns: ["qty", "price"], grouped: true, layout: "list" };

describe("saved views", () => {
  it("keeps a good view as it is", () => {
    expect(cleanView(view)).toEqual(view);
  });

  it("fills in what's missing and drops what's unknown", () => {
    const v = cleanView({ id: "v2", name: "  Old  ", sorts: [{ field: "NOPE" }], columns: "x", layout: "weird", filter: { decks: "OUT" } })!;
    expect(v).toMatchObject({ name: "Old", binder: "", search: "", grouped: false, layout: "list", columns: DEFAULT_COLUMNS, sorts: [{ field: "NAME", reversed: false }] });
    expect(v.filter).toEqual({ ...EMPTY_FILTER, decks: "OUT" });
  });

  it("refuses views without a name or a proper id", () => {
    expect(cleanView({ ...view, name: " " })).toBeNull();
    expect(cleanView({ ...view, id: "<script>" })).toBeNull();
    expect(cleanView(null)).toBeNull();
  });

  it("keeps each id once and at most 30", () => {
    expect(cleanViews([view, { ...view, name: "Again" }])).toHaveLength(1);
    expect(cleanViews(Array.from({ length: 40 }, (_, i) => ({ ...view, id: `v${i}` })))).toHaveLength(MAX_VIEWS);
  });

  it("stores the list with the account", async () => {
    process.env.DATABASE_URL = "memory";
    resetMemoryStore();
    const store = accountStore()!;
    const a = (await store.create("joel", "x", true, 1))!;
    expect(await store.savedViews(a.id)).toEqual([]);
    await store.setSavedViews(a.id, [view]);
    expect(await store.savedViews(a.id)).toEqual([view]);
    expect(await store.savedViewAccounts()).toBe(1);
    await store.setSavedViews(a.id, []);
    expect(await store.savedViewAccounts()).toBe(0);
  });
});
