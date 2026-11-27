import { gzipSync } from "node:zlib";
import { beforeEach, describe, expect, it, vi } from "vitest";

// One Blob file whose version the tests change; head and get answer from it.
const blob = { etag: '"v1"', data: { n: 1 } as unknown };
const calls = { head: 0, get: 0 };

vi.mock("@vercel/blob", () => ({
  head: vi.fn(async () => {
    calls.head++;
    return { etag: blob.etag, uploadedAt: new Date(), size: 10 };
  }),
  get: vi.fn(async () => {
    calls.get++;
    const bytes = gzipSync(Buffer.from(JSON.stringify(blob.data), "utf8"));
    return { statusCode: 200, stream: new Response(bytes).body, blob: { etag: blob.etag, uploadedAt: new Date(), size: bytes.length } };
  }),
  put: vi.fn(),
}));

import { resetMemoryStore } from "@/lib/accounts";
import { CHECK_MS, blobCache, blobDownloads, countDownload } from "@/lib/blobcache";

describe("Blob files kept in memory (2026-11-27)", () => {
  beforeEach(() => {
    blob.etag = '"v1"';
    blob.data = { n: 1 };
    calls.head = 0;
    calls.get = 0;
    process.env.DATABASE_URL = "memory";
    resetMemoryStore();
  });

  it("downloads once, then only checks the version, and downloads again when it changed", async () => {
    const cache = blobCache<{ n: number }>("test/file.json.gz");
    const t = 1_000_000;
    expect(await cache.load(false, t)).toEqual({ n: 1 });
    expect(calls).toEqual({ head: 0, get: 1 });
    // Within the 10 minutes: nothing asked.
    expect(await cache.load(false, t + CHECK_MS - 1)).toEqual({ n: 1 });
    expect(calls).toEqual({ head: 0, get: 1 });
    // After: only the version, the same, so no download.
    expect(await cache.load(false, t + CHECK_MS)).toEqual({ n: 1 });
    expect(calls).toEqual({ head: 1, get: 1 });
    // A new version: downloaded.
    blob.etag = '"v2"';
    blob.data = { n: 2 };
    expect(await cache.load(true, t + CHECK_MS + 1)).toEqual({ n: 2 });
    expect(calls).toEqual({ head: 2, get: 2 });
  });

  it("a file just saved is kept with its version, without a download", async () => {
    const cache = blobCache<{ n: number }>("test/file.json.gz");
    cache.keep({ n: 1 }, "v1", 0);
    expect(await cache.load(true, 1)).toEqual({ n: 1 });
    expect(calls).toEqual({ head: 1, get: 0 });
  });

  it("counts the day's downloads and starts again the next day", async () => {
    const day = Date.UTC(2026, 10, 27, 12);
    // At the same time, as a page reading several files does.
    await Promise.all([countDownload("a", 100, day), countDownload("a", 50, day), countDownload("b", 10, day)]);
    expect(await blobDownloads(day)).toEqual({ day: "2026-11-27", count: 3, bytes: 160, files: { a: 2, b: 1 } });
    expect(await blobDownloads(day + 86_400_000)).toEqual({ day: "2026-11-28", count: 0, bytes: 0, files: {} });
    await countDownload("a", 5, day + 86_400_000);
    expect((await blobDownloads(day + 86_400_000))?.count).toBe(1);
  });
});
