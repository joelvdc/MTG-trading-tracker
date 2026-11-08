"use client";

import { useState } from "react";
import { api } from "@/components/api";
import { Dialog } from "@/components/Dialog";
import { ColourPips } from "@/components/Mana";
import {
  choiceOf,
  clock,
  outReasonLabel,
  WIN_TYPES,
  type Game,
  type GamesView,
  type GameSeat,
  type GuestCommander,
  type SeatChoice,
} from "@/lib/gameshared";
import type { GameEdit, SeatInput } from "@/lib/games";

const MAX_SEATS = 8;
const ME = "me";
const GUEST = "guest";

/** One seat being filled in: who ("me", a player's uid or "guest") and which deck ("d:<id>", "f:<uid>" or ""). */
interface SeatForm {
  seat?: number;
  who: string;
  name: string;
  deck: string;
  commander: string;
  partner: string;
  /** The guest's commander as recorded (found on Scryfall then), kept while its name isn't changed. */
  guest: GuestCommander | null;
  place: number | null;
  recorded?: GameSeat;
}

function formOf(s: GameSeat): SeatForm {
  const ch = choiceOf(s);
  return {
    seat: s.seat,
    who: ch.me ? ME : (ch.playerUid ?? GUEST),
    name: ch.playerUid ? "" : ch.name,
    deck: ch.deckId != null ? `d:${ch.deckId}` : ch.friendDeckUid ? `f:${ch.friendDeckUid}` : "",
    commander: ch.guest?.commander ?? "",
    partner: ch.guest?.partner ?? "",
    guest: ch.guest,
    place: s.place ?? null,
    recorded: s,
  };
}

const blank = (who: string): SeatForm => ({ who, name: "", deck: "", commander: "", partner: "", guest: null, place: null });

function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** A game played without the tracker gets 20:00 on its day, like the app's "Add a past game". */
function dayAt8pm(day: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
  if (!m) return null;
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 20, 0).getTime();
}

const whole = (s: string): number | null => (s.trim() === "" ? null : Number.isInteger(Number(s)) ? Number(s) : NaN);

/**
 * A recorded game to look at and change (the app's game page: places, draw, how it was won, note, minutes, turns,
 * who played which deck), or a past game to add by hand ([game] null).
 */
export function GameDialog({
  game,
  view,
  busy,
  onClose,
  onSave,
  onDelete,
}: {
  game: Game | null;
  view: GamesView;
  busy: boolean;
  onClose: () => void;
  onSave: (e: GameEdit) => Promise<boolean>;
  onDelete: (g: Game) => void;
}) {
  const me = view.players.find((p) => p.me);
  const friends = view.players.filter((p) => !p.me);
  const [seats, setSeats] = useState<SeatForm[]>(() => (game ? [...game.seats].sort((a, b) => a.seat - b.seat).map(formOf) : [blank(ME), blank(GUEST), blank(GUEST), blank(GUEST)]));
  const [draw, setDraw] = useState(game?.draw ?? false);
  const [winType, setWinType] = useState<string | null>(game?.winType ?? null);
  const [note, setNote] = useState(game?.note ?? "");
  const [minutes, setMinutes] = useState(game ? String(Math.floor(game.durationMs / 60_000)) : "");
  const [rounds, setRounds] = useState(game?.rounds != null ? String(game.rounds) : "");
  const [day, setDay] = useState(today());
  const [error, setError] = useState<string | null>(null);
  const [looking, setLooking] = useState(false);

  const change = (i: number, s: Partial<SeatForm>) => setSeats((all) => all.map((x, j) => (j === i ? { ...x, ...s } : x)));
  const takenBy = (i: number) => new Set(seats.filter((_, j) => j !== i).map((s) => s.who).filter((w) => w !== GUEST));

  /** A guest's commander: as recorded while unchanged, else looked up on Scryfall now. */
  async function guestOf(s: SeatForm): Promise<GuestCommander | null> {
    const c = s.commander.trim();
    if (!c) return null;
    const p = s.partner.trim() || null;
    if (s.guest && s.guest.commander === c && (s.guest.partner ?? null) === p) return s.guest;
    const r = await api<{ found: GuestCommander }>("/api/games", { lookup: { commander: c, partner: p } });
    if (!r.ok) throw new Error(r.data.error ?? "The commander could not be looked up.");
    return r.data.found;
  }

  async function save() {
    setError(null);
    const m = whole(minutes);
    const t = whole(rounds);
    if (Number.isNaN(m) || Number.isNaN(t)) return setError("Minutes and turns are whole numbers.");
    const at = game ? undefined : dayAt8pm(day);
    if (!game && at == null) return setError("Pick the day the game was played.");
    let inputs: SeatInput[];
    try {
      setLooking(true);
      inputs = await Promise.all(
        seats.map(async (s): Promise<SeatInput> => {
          const deckId = s.deck.startsWith("d:") ? Number(s.deck.slice(2)) : null;
          const friendDeckUid = s.deck.startsWith("f:") ? s.deck.slice(2) : null;
          const choice: SeatChoice =
            s.who === ME
              ? { playerUid: me?.uid ?? null, name: "", me: true, deckId, friendDeckUid, guest: null }
              : s.who === GUEST
                ? { playerUid: null, name: s.name.trim(), me: false, deckId, friendDeckUid: null, guest: deckId != null ? null : await guestOf(s) }
                : { playerUid: s.who, name: "", me: false, deckId, friendDeckUid, guest: null };
          return { seat: s.seat, choice, place: s.place };
        }),
      );
    } catch (e) {
      setLooking(false);
      return setError(e instanceof Error ? e.message : "The commander could not be looked up.");
    } finally {
      setLooking(false);
    }
    await onSave({ type: "saveGame", uid: game?.uid ?? null, startedAt: at ?? undefined, minutes: m, rounds: t, draw, winType: draw ? null : winType, note: note.trim() || null, seats: inputs });
  }

  /** The owner's decks other seats play: a deck is at one seat only. */
  const decksTaken = (i: number) => new Set(seats.filter((_, j) => j !== i).map((s) => s.deck));
  const deckOptions = (s: SeatForm, i: number) => {
    const opts: [string, string][] = [["", s.who === GUEST ? "Own commander (below)" : "No deck"]];
    const taken = decksTaken(i);
    const mine = view.myDecks.filter((d) => s.deck === `d:${d.id}` || (!taken.has(`d:${d.id}`) && !d.retired));
    if (s.who === ME) for (const d of mine) opts.push([`d:${d.id}`, d.name]);
    else {
      if (s.who !== GUEST) for (const d of view.friendDecks.filter((f) => f.playerUid === s.who)) opts.push([`f:${d.uid}`, d.name]);
      // Friends and guests can borrow the owner's decks (app 1.82).
      for (const d of mine) opts.push([`d:${d.id}`, `Your deck (lent): ${d.name}`]);
    }
    // A deck recorded but deleted since (or another player's): still shown, so the seat can stay as it was.
    if (s.deck && !opts.some((o) => o[0] === s.deck)) opts.push([s.deck, `${s.recorded?.deckName || s.recorded?.commander || "Deck"} (as recorded)`]);
    return opts;
  };

  const turnsBySeat = new Map<number, number[]>();
  for (const t of game?.turns ?? []) turnsBySeat.set(t.seat, [...(turnsBySeat.get(t.seat) ?? []), t.ms]);
  const locked = busy || looking || view.readOnly;

  return (
    <Dialog title={game ? `Game of ${new Date(game.startedAt).toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short", year: "numeric" })}` : "Add a past game"} onClose={onClose}>
      <div className="stack game-form">
        {game && (
          <p className="muted small">
            {game.manual ? "Added by hand" : `Played with the tracker, starting life ${game.startingLife}`}
            {game.turnsOn && game.rounds != null ? ` · ${game.rounds} rounds` : ""}
          </p>
        )}
        {!game && (
          <label className="field">
            <span>Day played</span>
            <input type="date" value={day} max={today()} onChange={(e) => setDay(e.target.value)} />
          </label>
        )}

        <div className="mini-head">Players, their places and decks</div>
        {seats.map((s, i) => {
          const taken = takenBy(i);
          return (
            <div key={i} className="seat-form">
              <select aria-label={`Place of player ${i + 1}`} className="seat-place" value={s.place ?? ""} onChange={(e) => change(i, { place: e.target.value ? Number(e.target.value) : null })}>
                <option value="">–</option>
                {seats.map((_, p) => (
                  <option key={p} value={p + 1}>
                    {p + 1}
                  </option>
                ))}
              </select>
              <div className="grow stack tight">
                <div className="inline wrap">
                  <select
                    aria-label={`Player ${i + 1}`}
                    value={s.who}
                    onChange={(e) => change(i, { who: e.target.value, deck: "", name: "", commander: "", partner: "", guest: null })}
                  >
                    {(!taken.has(ME) || s.who === ME) && <option value={ME}>{me?.name ?? "Me"} (you)</option>}
                    {friends
                      .filter((p) => !taken.has(p.uid!) || s.who === p.uid)
                      .map((p) => (
                        <option key={p.uid} value={p.uid!}>
                          {p.name}
                        </option>
                      ))}
                    {s.who !== ME && s.who !== GUEST && !view.players.some((p) => p.uid === s.who) && <option value={s.who}>{s.recorded?.name ?? "Player"} (deleted)</option>}
                    <option value={GUEST}>A guest…</option>
                  </select>
                  {s.who === GUEST && (
                    <input aria-label={`Guest name ${i + 1}`} placeholder={`Player ${i + 1}`} value={s.name} maxLength={60} onChange={(e) => change(i, { name: e.target.value })} />
                  )}
                  <select aria-label={`Deck of player ${i + 1}`} value={s.deck} onChange={(e) => change(i, { deck: e.target.value })}>
                    {deckOptions(s, i).map(([v, l]) => (
                      <option key={v} value={v}>
                        {l}
                      </option>
                    ))}
                  </select>
                  {!game && seats.length > 2 && (
                    <button className="link" disabled={locked} onClick={() => setSeats((all) => all.filter((_, j) => j !== i))} aria-label={`Remove player ${i + 1}`}>
                      Remove
                    </button>
                  )}
                </div>
                {s.who === GUEST && !s.deck && (
                  <div className="inline wrap">
                    <input aria-label={`Commander of player ${i + 1}`} placeholder="Commander (optional)" value={s.commander} maxLength={120} onChange={(e) => change(i, { commander: e.target.value })} />
                    <input aria-label={`Partner of player ${i + 1}`} placeholder="Partner" value={s.partner} maxLength={120} onChange={(e) => change(i, { partner: e.target.value })} />
                  </div>
                )}
                {s.recorded && <SeatFacts s={s.recorded} turnsOn={!!game?.turnsOn || !!game?.manual} turns={turnsBySeat.get(s.recorded.seat)} />}
              </div>
              {s.place === 1 && !draw && (
                <span className="trophy" title="Won" aria-label="Won">
                  🏆
                </span>
              )}
            </div>
          );
        })}
        {!game && seats.length < MAX_SEATS && (
          <div>
            <button className="chip add" disabled={locked} onClick={() => setSeats((all) => [...all, blank(GUEST)])}>
              ＋ Player
            </button>
          </div>
        )}

        <label className="check">
          <input type="checkbox" checked={draw} onChange={(e) => setDraw(e.target.checked)} />
          <span>A draw (nobody won)</span>
        </label>
        {!draw && (
          <>
            <div className="mini-head">How was it won? (optional)</div>
            <div className="chips">
              {WIN_TYPES.map(([k, l]) => (
                <button key={k} className={winType === k ? "chip on" : "chip"} aria-pressed={winType === k} onClick={() => setWinType(winType === k ? null : k)}>
                  {l}
                </button>
              ))}
            </div>
          </>
        )}
        <div className="form-grid">
          <label className="field">
            <span>Minutes</span>
            <input inputMode="numeric" value={minutes} onChange={(e) => setMinutes(e.target.value.replace(/\D/g, ""))} />
          </label>
          <label className="field">
            <span>Turns (rounds)</span>
            <input inputMode="numeric" value={rounds} onChange={(e) => setRounds(e.target.value.replace(/\D/g, ""))} />
          </label>
        </div>
        <label className="field">
          <span>Note</span>
          <textarea rows={2} value={note} maxLength={1000} onChange={(e) => setNote(e.target.value)} />
        </label>
        {error && (
          <div className="bar error" role="alert">
            {error}
          </div>
        )}
        {view.readOnly && <p className="muted small">A newer app wrote your data; update the app before changing games here.</p>}
      </div>
      <div className="dialog-actions">
        <button className="btn primary" disabled={locked} onClick={save}>
          {looking ? "Looking up…" : game ? "Save" : "Add the game"}
        </button>
        <button className="btn" onClick={onClose}>
          Cancel
        </button>
        <span className="spacer" />
        {game && (
          <button className="btn danger" disabled={locked} onClick={() => onDelete(game)}>
            Delete…
          </button>
        )}
      </div>
    </Dialog>
  );
}

/** What the tracker recorded for a seat: colours, how they went out, their turn times. */
function SeatFacts({ s, turnsOn, turns }: { s: GameSeat; turnsOn: boolean; turns?: number[] }) {
  const out = [
    s.started ? "started" : null,
    s.outRound != null && turnsOn ? `out in round ${s.outRound}` : null,
    outReasonLabel(s.outReason),
    s.finalLife != null && !s.outReason ? `${s.finalLife} life` : null,
    s.bracket != null ? `bracket ${s.bracket}` : null,
    s.power != null ? `power ${s.power.toFixed(1)}` : null,
  ].filter(Boolean);
  const t = turns?.length ? `${turns.length} turns, ${clock(turns.reduce((a, b) => a + b, 0) / turns.length)} on average, longest ${clock(Math.max(...turns))}` : null;
  if (!out.length && !t && !s.colorIdentity) return null;
  return (
    <div className="muted small">
      {s.colorIdentity ? <ColourPips identity={s.colorIdentity} size={12} /> : null} {[s.commander && s.commander !== s.deckName ? s.commander : null, ...out].filter(Boolean).join(" · ")}
      {t && <div>{t}</div>}
    </div>
  );
}
