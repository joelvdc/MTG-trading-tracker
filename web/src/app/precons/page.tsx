"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { api } from "@/components/api";
import { COLOUR_NAMES, ColourPips } from "@/components/Mana";
import { TopBar } from "@/components/Nav";

/** One Commander precon as MTGJSON's deck list has it, newest first (the browse list's answer). */
interface PreconEntry {
  fileName: string;
  name: string;
  code: string;
  setName: string | null;
  releaseDate: string | null;
  commanders: { name: string; scryfallId: string }[];
  // WUBRG order; "" for colourless.
  colorIdentity: string;
  cards: number;
}

const COLOURS = ["W", "U", "B", "R", "G", "C"] as const;

/** "Feb 2024" for a release date ("2024-02-09"), or "" when there is none. */
function monthYear(date: string | null): string {
  if (!date) return "";
  const d = new Date(`${date.slice(0, 10)}T00:00:00`);
  return Number.isNaN(d.getTime()) ? "" : d.toLocaleDateString("en-GB", { month: "short", year: "numeric" });
}

/** The art crop Scryfall keeps for a card (4/3), or null when there is no such card. */
function artCrop(id: string | undefined): string | null {
  return id && id.length > 2 ? `https://cards.scryfall.io/art_crop/front/${id[0]}/${id[1]}/${id}.jpg` : null;
}

/** Every Commander precon from MTGJSON, by set, with a search, colour and year filter kept in the address. */
export default function PreconsPage() {
  const router = useRouter();
  const [decks, setDecks] = useState<PreconEntry[] | null>(null);
  const [folder, setFolder] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [colours, setColours] = useState<Set<string>>(new Set());
  const [year, setYear] = useState("");
  // Whether the address has been read: the search, colours and year are in it, and only then written back.
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const p = new URLSearchParams(window.location.search);
    setQuery(p.get("q") ?? "");
    setColours(new Set([...(p.get("c") ?? "").toUpperCase()].filter((c) => (COLOURS as readonly string[]).includes(c))));
    setYear(p.get("year") ?? "");
    setReady(true);
  }, []);

  useEffect(() => {
    (async () => {
      const [s, d] = await Promise.all([api<{ loggedIn: boolean; folder?: string }>("/api/session"), api<{ decks?: PreconEntry[] }>("/api/precons?browse=1")]);
      if (!s.data.loggedIn || d.status === 401 || d.data.loggedOut) return window.location.replace("/login");
      setFolder(s.data.folder ?? "");
      if (d.ok && Array.isArray(d.data.decks)) setDecks(d.data.decks);
      else setError(d.data.error ?? "Could not get MTGJSON's list of decks.");
    })();
  }, []);

  // The search, colours and year go in the address (?q=&c=&year=), so coming back to the page finds them again.
  useEffect(() => {
    if (!ready) return;
    const p = new URLSearchParams();
    if (query.trim()) p.set("q", query.trim());
    const c = COLOURS.filter((x) => x !== "C" && colours.has(x)).join("") + (colours.has("C") ? "C" : "");
    if (c) p.set("c", c);
    if (year) p.set("year", year);
    const s = p.toString();
    const next = s ? `/precons?${s}` : "/precons";
    if (next !== `${window.location.pathname}${window.location.search}`) router.replace(next, { scroll: false });
  }, [ready, query, colours, year, router]);

  const years = useMemo(() => {
    const set = new Set<string>();
    for (const d of decks ?? []) if (d.releaseDate && /^\d{4}/.test(d.releaseDate)) set.add(d.releaseDate.slice(0, 4));
    return [...set].sort((a, b) => b.localeCompare(a));
  }, [decks]);

  const shown = useMemo(() => {
    if (!decks) return [];
    const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
    const picked = COLOURS.filter((c) => colours.has(c));
    return decks.filter((d) => {
      if (year && (d.releaseDate ?? "").slice(0, 4) !== year) return false;
      // Every picked colour must be in the deck's identity; colourless means an empty identity.
      if (!picked.every((c) => (c === "C" ? d.colorIdentity === "" : d.colorIdentity.includes(c)))) return false;
      if (words.length === 0) return true;
      const hay = [d.name, d.code, d.setName ?? "", ...d.commanders.map((c) => c.name)].join(" ").toLowerCase();
      return words.every((w) => hay.includes(w));
    });
  }, [decks, query, colours, year]);

  // One group per set, in the order the decks come in (newest first).
  const groups = useMemo(() => {
    const map = new Map<string, { title: string; date: string | null; decks: PreconEntry[] }>();
    for (const d of shown) {
      const key = `${d.code}|${d.setName ?? ""}`;
      if (!map.has(key)) map.set(key, { title: d.setName ?? d.code, date: d.releaseDate, decks: [] });
      map.get(key)!.decks.push(d);
    }
    return [...map.values()];
  }, [shown]);

  const toggleColour = (c: string) =>
    setColours((old) => {
      const next = new Set(old);
      if (!next.delete(c)) next.add(c);
      return next;
    });

  return (
    <>
      <TopBar page="precons" folder={folder} />
      <main className="page wide">
        <h1 className="section-head">Commander precons</h1>
        <p className="muted small">Every Commander precon, from MTGJSON. Open one to see it with the auto categories, then make it your deck or add its cards to your collection.</p>
        <div className="controls">
          <input type="search" placeholder="Search the deck, set or commander" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search the precons" />
          <div className="precon-colours" role="group" aria-label="Colours">
            {COLOURS.map((c) => (
              <button
                key={c}
                type="button"
                className={colours.has(c) ? "chip on precon-colour" : "chip precon-colour"}
                title={COLOUR_NAMES[c]}
                aria-label={COLOUR_NAMES[c]}
                aria-pressed={colours.has(c)}
                onClick={() => toggleColour(c)}
              >
                <ColourPips identity={c === "C" ? "" : c} size={16} />
              </button>
            ))}
          </div>
          <label className="inline small">
            <span className="muted">Year</span>
            <select value={year} onChange={(e) => setYear(e.target.value)}>
              <option value="">All years</option>
              {years.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </label>
        </div>
        {error && <div className="bar error">{error}</div>}
        {!decks && !error && <p className="muted center">Loading…</p>}
        {decks && (
          <>
            <p className="muted small">{shown.length === decks.length ? `${decks.length} precon${decks.length === 1 ? "" : "s"}` : `${shown.length} of ${decks.length} precons`}</p>
            {shown.length === 0 ? (
              <p className="muted center">No precon matches.</p>
            ) : (
              groups.map((g) => (
                <section key={`${g.title}|${g.date ?? ""}`} className="precon-set">
                  <h2 className="section-head">
                    {g.title} <span className="muted small">{monthYear(g.date)}</span>
                  </h2>
                  <div className="precon-grid">
                    {g.decks.map((d) => {
                      const art = artCrop(d.commanders[0]?.scryfallId);
                      return (
                        <Link key={d.fileName} className="precon-tile" href={`/precons/${encodeURIComponent(d.fileName)}`}>
                          {art ? <img className="precon-art" src={art} alt="" loading="lazy" /> : <div className="precon-art no-art" aria-hidden="true" />}
                          <div className="precon-tile-text">
                            <strong className="precon-name">{d.name}</strong>
                            <span className="precon-commanders">{d.commanders.map((c) => c.name).join(" + ") || "No commander"}</span>
                            <span className="precon-pips">
                              <ColourPips identity={d.colorIdentity} size={14} />
                            </span>
                          </div>
                        </Link>
                      );
                    })}
                  </div>
                </section>
              ))
            )}
          </>
        )}
      </main>
    </>
  );
}
