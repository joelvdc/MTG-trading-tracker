"use client";

import { useEffect, useState } from "react";
import { api } from "@/components/api";
import { TopBar } from "@/components/Nav";
import { BracketCard, PowerCard, SpeedSection } from "@/components/RuleZeroCards";
import type { PowerSource, RuleZeroDeck } from "@/lib/decks";
import type { WinResult } from "@/lib/winodds";

type Which = "BRACKET" | "POWER" | "BOTH";

/** A deck's win turn, if this browser has worked it out (the deck page keeps it); it may be from before a change. */
function keptWin(id: number): WinResult | null {
  try {
    const kept = JSON.parse(localStorage.getItem(`mtg.winOdds.${id}`) ?? "null") as { win: WinResult | null } | null;
    return kept?.win ?? null;
  } catch {
    return null;
  }
}

/** The bracket and power cards of every scored deck, to look at or print together (the app's "… cards of all decks"). */
export default function AllRuleZeroCardsPage() {
  const [data, setData] = useState<{ decks: RuleZeroDeck[]; powerSource: PowerSource } | null>(null);
  const [wins, setWins] = useState<Record<number, WinResult | null>>({});
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [which, setWhich] = useState<Which>("BRACKET");
  const [inkSaver, setInkSaver] = useState(false);

  useEffect(() => {
    const w = new URLSearchParams(window.location.search).get("cards");
    if (w === "POWER" || w === "BOTH") setWhich(w);
    (async () => {
      const [s, d] = await Promise.all([
        api<{ loggedIn: boolean; folder?: string }>("/api/session"),
        api<{ decks?: RuleZeroDeck[]; powerSource?: PowerSource; noFolder?: boolean }>("/api/decks/cards"),
      ]);
      if (!s.data.loggedIn || d.status === 401 || d.data.loggedOut) return window.location.replace("/login");
      if (!s.data.folder || d.data.noFolder) return window.location.replace("/settings");
      setFolder(s.data.folder ?? "");
      if (d.ok && Array.isArray(d.data.decks)) {
        setData({ decks: d.data.decks, powerSource: d.data.powerSource ?? "COMMANDER_SALT" });
        setWins(Object.fromEntries(d.data.decks.map((x) => [x.id, keptWin(x.id)])));
      } else setError(d.data.error ?? "Could not load the decks.");
    })();
  }, []);

  return (
    <>
      <TopBar page="decks" folder={folder} />
      <main className="page wide">
        <a href="/decks" className="small">
          ← Decks
        </a>
        <h1 className="section-head">Rule-zero cards of all decks</h1>
        {error && <div className="bar error">{error}</div>}
        {!data && !error && <p className="muted center">Loading…</p>}
        {data && (
          <>
            <div className="controls">
              <div className="seg text" role="group" aria-label="Cards">
                {(
                  [
                    ["BRACKET", "Bracket"],
                    ["POWER", "Power level"],
                    ["BOTH", "Both"],
                  ] as const
                ).map(([k, l]) => (
                  <button key={k} className={which === k ? "on" : ""} onClick={() => setWhich(k)}>
                    {l}
                  </button>
                ))}
              </div>
              <label className="check small">
                <input type="checkbox" checked={inkSaver} onChange={(e) => setInkSaver(e.target.checked)} />
                <span>Ink saver (light headers without the commander art)</span>
              </label>
              <span className="spacer" />
              <button className="btn primary small-btn" disabled={data.decks.length === 0} onClick={() => window.print()}>
                Print
              </button>
            </div>
            <p className="muted small">
              {data.decks.length} deck{data.decks.length === 1 ? "" : "s"} scored by Commander Salt. The speed on a bracket card
              {data.powerSource === "APP" ? " and the app's own power level show" : " shows"} for decks whose win turn this browser has worked out (open the
              deck once).
            </p>
            {data.decks.length === 0 ? (
              <p className="muted center">No deck has been scored yet.</p>
            ) : (
              <div className="rz-print">
                {data.decks.map((d) => {
                  const win = wins[d.id] ?? null;
                  const power = d.power ?? (data.powerSource === "APP" && win ? win.power.power : null);
                  const sv = d.scrollVault as Parameters<typeof PowerCard>[0]["scrollVault"];
                  return [
                    which !== "POWER" && (
                      <BracketCard key={`b${d.id}`} deck={d.deck} cover={d.cover} data={d.salt} inkSaver={inkSaver} speed={win ? <SpeedSection win={win} /> : undefined} />
                    ),
                    which !== "BRACKET" && (
                      <PowerCard key={`p${d.id}`} deck={d.deck} cover={d.cover} data={d.salt} source={data.powerSource} power={power} error={d.powerError} scrollVault={sv} inkSaver={inkSaver} />
                    ),
                  ];
                })}
              </div>
            )}
          </>
        )}
      </main>
    </>
  );
}
