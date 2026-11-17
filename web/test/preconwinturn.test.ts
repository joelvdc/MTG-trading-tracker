import { describe, expect, it } from "vitest";
import { memoryStore } from "@/lib/accounts";

const NOW = 1_793_968_645_412;

describe("a precon's win turn kept on the website", () => {
  it("keeps one result per precon and replaces it when the list changes", async () => {
    const s = memoryStore();
    expect(await s.preconWinTurn("Blast from the Past.txt")).toBeNull();
    await s.putPreconWinTurn("Blast from the Past.txt", { key: "13|abc", result: '{"label":"a"}', at: NOW });
    await s.putPreconWinTurn("Other Precon.txt", { key: "13|other", result: "null", at: NOW });
    expect(await s.preconWinTurn("Blast from the Past.txt")).toEqual({ key: "13|abc", result: '{"label":"a"}', at: NOW });
    // The same precon with a different list (a new key) replaces its result.
    await s.putPreconWinTurn("Blast from the Past.txt", { key: "13|def", result: '{"label":"b"}', at: NOW + 1 });
    expect(await s.preconWinTurn("Blast from the Past.txt")).toEqual({ key: "13|def", result: '{"label":"b"}', at: NOW + 1 });
    expect(await s.preconWinTurn("Other Precon.txt")).toEqual({ key: "13|other", result: "null", at: NOW });
  });
});
