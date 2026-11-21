// How the website's own requests to EDHREC and Archidekt went (since app 1.105). The weekly health check doesn't fetch
// those services (their terms forbid automated requests): the website notes what the real requests, made when a person
// taps something, answered, and /api/health reports it. Kept in the jobs table, like the nightly job and the AI check.
import { accountStore, type AccountStore } from "./accounts";

export type WatchedService = "edhrec" | "archidekt";

export const WATCHED: WatchedService[] = ["edhrec", "archidekt"];

/** What one service's last requests showed: when it last answered, when it last failed, and the latest problem. */
interface ServiceNote {
  okAt: number | null;
  failAt: number | null;
  failsSinceOk: number;
  problem: string | null;
}

type JobStore = Pick<AccountStore, "job" | "setJob">;

/** A success within this long after the last one isn't written again (it saves database writes). */
const QUIET_MS = 6 * 3_600_000;

const jobName = (service: WatchedService) => `svc-${service}`;

const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);

/** A stored note, read defensively (a wrong type counts as missing). */
function readNote(info: Record<string, unknown> | null | undefined): ServiceNote {
  const i = info && typeof info === "object" ? info : {};
  return {
    okAt: num(i.okAt),
    failAt: num(i.failAt),
    failsSinceOk: Number.isInteger(i.failsSinceOk) && (i.failsSinceOk as number) > 0 ? (i.failsSinceOk as number) : 0,
    problem: typeof i.problem === "string" ? i.problem : null,
  };
}

async function previous(store: JobStore, service: WatchedService): Promise<ServiceNote> {
  const last = await store.job(jobName(service)).catch(() => null);
  return readNote(last?.info);
}

/** problem null = the service answered and its answer was read. Never throws; never delays the caller. */
export async function noteService(service: WatchedService, problem: string | null, now = Date.now(), store: JobStore | null = accountStore()): Promise<void> {
  try {
    if (!store) return;
    const prev = await previous(store, service);
    if (problem === null) {
      if (prev.failsSinceOk === 0 && prev.okAt !== null && now - prev.okAt < QUIET_MS) return;
      await store.setJob(jobName(service), now, { okAt: now, failAt: prev.failAt, failsSinceOk: 0, problem: prev.problem });
    } else {
      await store.setJob(jobName(service), now, { okAt: prev.okAt, failAt: now, failsSinceOk: prev.failsSinceOk + 1, problem: problem.slice(0, 300) });
    }
  } catch {
    // A note is a nicety: it never breaks (or slows down) the request someone is waiting on.
  }
}

/** For /api/health: each service's note in ages (whole hours), or null for one the website hasn't used yet. */
export async function serviceNotes(
  now = Date.now(),
  store: JobStore | null = accountStore(),
): Promise<Record<WatchedService, { lastOkAgeHours: number | null; lastFailAgeHours: number | null; failsSinceOk: number; problem: string | null } | null>> {
  const out: Record<WatchedService, { lastOkAgeHours: number | null; lastFailAgeHours: number | null; failsSinceOk: number; problem: string | null } | null> = {
    edhrec: null,
    archidekt: null,
  };
  if (!store) return out;
  for (const service of WATCHED) {
    try {
      const last = await store.job(jobName(service));
      if (!last) continue;
      const note = readNote(last.info);
      const age = (at: number | null) => (at === null ? null : Math.round((now - at) / 3_600_000));
      out[service] = { lastOkAgeHours: age(note.okAt), lastFailAgeHours: age(note.failAt), failsSinceOk: note.failsSinceOk, problem: note.problem };
    } catch {
      out[service] = null;
    }
  }
  return out;
}
