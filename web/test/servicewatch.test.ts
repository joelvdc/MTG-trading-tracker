import { describe, expect, it } from "vitest";
import { noteService, serviceNotes } from "@/lib/servicewatch";

const NOW = 1_750_000_000_000;
const HOUR = 3_600_000;

/** A store with only the jobs table, kept in memory; counts the writes. */
function fakeStore(initial: Record<string, { at: number; info: Record<string, unknown> }> = {}) {
  const jobs = new Map(Object.entries(initial));
  const writes: { name: string; at: number; info: Record<string, unknown> }[] = [];
  return {
    writes,
    jobs,
    job: async (name: string) => jobs.get(name) ?? null,
    setJob: async (name: string, at: number, info: Record<string, unknown>) => {
      writes.push({ name, at, info });
      jobs.set(name, { at, info });
    },
  };
}

describe("how the website's own requests to EDHREC and Archidekt went", () => {
  it("writes the first success, and a second one within 6 hours writes nothing", async () => {
    const store = fakeStore();
    await noteService("edhrec", null, NOW, store);
    expect(store.writes).toHaveLength(1);
    expect(store.writes[0]).toMatchObject({ name: "svc-edhrec", at: NOW, info: { okAt: NOW, failAt: null, failsSinceOk: 0, problem: null } });
    await noteService("edhrec", null, NOW + 5 * HOUR, store);
    expect(store.writes).toHaveLength(1);
    expect(store.jobs.get("svc-edhrec")!.info.okAt).toBe(NOW);
    // After 7 hours the age is written again.
    await noteService("edhrec", null, NOW + 7 * HOUR, store);
    expect(store.writes).toHaveLength(2);
    expect(store.jobs.get("svc-edhrec")!.info.okAt).toBe(NOW + 7 * HOUR);
  });

  it("counts problems, keeps the last success, and a success then keeps the problem's time and words", async () => {
    const store = fakeStore();
    await noteService("archidekt", null, NOW, store);
    await noteService("archidekt", "couldn't be reached", NOW + HOUR, store);
    await noteService("archidekt", "answered 503", NOW + 2 * HOUR, store);
    expect(store.jobs.get("svc-archidekt")!.info).toEqual({ okAt: NOW, failAt: NOW + 2 * HOUR, failsSinceOk: 2, problem: "answered 503" });
    await noteService("archidekt", null, NOW + 3 * HOUR, store);
    expect(store.jobs.get("svc-archidekt")!.info).toEqual({ okAt: NOW + 3 * HOUR, failAt: NOW + 2 * HOUR, failsSinceOk: 0, problem: "answered 503" });
  });

  it("remembers a success within 6 hours as quiet, after a problem too", async () => {    const store = fakeStore();
    await noteService("edhrec", "answered 503", NOW, store);
    await noteService("edhrec", null, NOW + HOUR, store);
    expect(store.jobs.get("svc-edhrec")!.info).toMatchObject({ okAt: NOW + HOUR, failsSinceOk: 0 });
    // The next hour's success writes nothing (failsSinceOk is 0 and the last success is recent).
    const before = store.writes.length;
    await noteService("edhrec", null, NOW + 4 * HOUR, store);
    expect(store.writes).toHaveLength(before);
  });

  it("cuts a long problem to 300 characters", async () => {
    const store = fakeStore();
    await noteService("edhrec", "x".repeat(500), NOW, store);
    expect((store.jobs.get("svc-edhrec")!.info.problem as string).length).toBe(300);
  });

  it("reports ages in whole hours, and null for a service the website hasn't used", async () => {
    const store = fakeStore({ "svc-edhrec": { at: NOW - 2 * HOUR, info: { okAt: NOW - 3 * HOUR - 20 * 60_000, failAt: NOW - HOUR - 40 * 60_000, failsSinceOk: 1, problem: "answered 429" } } });
    expect(await serviceNotes(NOW, store)).toEqual({
      edhrec: { lastOkAgeHours: 3, lastFailAgeHours: 2, failsSinceOk: 1, problem: "answered 429" },
      archidekt: null,
    });
  });

  it("reads a stored note with the wrong types defensively", async () => {
    const store = fakeStore({ "svc-edhrec": { at: NOW, info: { okAt: "yesterday", failAt: null, failsSinceOk: -3, problem: 42 } } });
    expect(await serviceNotes(NOW, store)).toEqual({ edhrec: { lastOkAgeHours: null, lastFailAgeHours: null, failsSinceOk: 0, problem: null }, archidekt: null });
  });

  it("never throws: neither a broken store nor none at all", async () => {
    const broken = {
      job: async () => {
        throw new Error("database down");
      },
      setJob: async () => {
        throw new Error("database down");
      },
    };
    await expect(noteService("edhrec", null, NOW, broken)).resolves.toBeUndefined();
    await expect(noteService("edhrec", "couldn't be reached", NOW, broken)).resolves.toBeUndefined();
    expect(await serviceNotes(NOW, broken)).toEqual({ edhrec: null, archidekt: null });
    await expect(noteService("edhrec", null, NOW, null)).resolves.toBeUndefined();
    expect(await serviceNotes(NOW, null)).toEqual({ edhrec: null, archidekt: null });
  });
});
