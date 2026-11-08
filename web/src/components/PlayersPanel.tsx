"use client";

import { useMemo, useState } from "react";
import { api } from "@/components/api";
import { Dialog } from "@/components/Dialog";
import { ColourPips } from "@/components/Mana";
import { playerResults, type FriendDeck, type GamesView, type GuestCommander, type Player } from "@/lib/gameshared";
import type { GameEdit } from "@/lib/games";

/**
 * The players and their decks (the app's Players screen): add, rename and delete friends, and add or delete their
 * decks (a commander found on Scryfall). The owner's own decks are the Decks page's decks.
 */
export function PlayersPanel({ view, busy, onEdit }: { view: GamesView; busy: boolean; onEdit: (e: GameEdit) => Promise<boolean> }) {
  const [naming, setNaming] = useState<{ uid: string | null; name: string } | null>(null);
  const [deleting, setDeleting] = useState<Player | null>(null);
  const [deckFor, setDeckFor] = useState<Player | null>(null);
  const [dropDeck, setDropDeck] = useState<FriendDeck | null>(null);
  const results = useMemo(() => new Map(playerResults(view.games).map((r) => [r.key, r])), [view.games]);
  const locked = busy || view.readOnly;

  return (
    <>
      <div className="controls">
        <button className="btn" disabled={locked} onClick={() => setNaming({ uid: null, name: "" })}>
          ＋ Add a player
        </button>
        <span className="muted small">Players and their decks are shared with your phones. Your own decks are the decks on the Decks page.</span>
      </div>
      {view.players.length === 0 && <p className="muted center">No players yet.</p>}
      <div className="player-list">
        {view.players.map((p) => {
          const r = results.get(p.uid!);
          const decks = view.friendDecks.filter((d) => d.playerUid === p.uid);
          return (
            <section key={p.uid} className="stat-block player-card">
              <div className="inline">
                <h2 className="grow">
                  {p.name}
                  {p.me && <span className="muted small"> (you)</span>}
                </h2>
                <button className="link" disabled={locked} onClick={() => setNaming({ uid: p.uid, name: p.name })}>
                  Rename
                </button>
                {!p.me && (
                  <button className="link" disabled={locked} onClick={() => setDeleting(p)}>
                    Delete
                  </button>
                )}
              </div>
              <p className="muted small">{r ? `${r.games} game${r.games === 1 ? "" : "s"}, ${r.wins} won` : "No games yet"}</p>
              {!p.me && (
                <>
                  <ul className="plain-list">
                    {decks.map((d) => (
                      <li key={d.uid}>
                        <span className="grow">
                          {d.colorIdentity && <ColourPips identity={d.colorIdentity} size={12} />} {d.name}
                          {d.commander !== d.name && (
                            <span className="muted small">
                              {" "}
                              · {d.commander}
                              {d.partner ? ` & ${d.partner}` : ""}
                            </span>
                          )}
                          {d.archidektUrl && (
                            <>
                              {" "}
                              <a className="small" href={d.archidektUrl} target="_blank" rel="noreferrer">
                                Archidekt
                              </a>
                            </>
                          )}
                        </span>
                        <button className="link" disabled={locked} onClick={() => setDropDeck(d)} aria-label={`Delete ${d.name}`}>
                          Delete
                        </button>
                      </li>
                    ))}
                  </ul>
                  <button className="link left" disabled={locked} onClick={() => setDeckFor(p)}>
                    ＋ Add a deck
                  </button>
                </>
              )}
            </section>
          );
        })}
      </div>

      {naming && (
        <NameDialog
          title={naming.uid ? "Rename the player" : "Add a player"}
          start={naming.name}
          busy={busy}
          onClose={() => setNaming(null)}
          onSave={async (name) => {
            if (await onEdit(naming.uid ? { type: "renamePlayer", uid: naming.uid, name } : { type: "addPlayer", name })) setNaming(null);
          }}
        />
      )}
      {deleting && (
        <Dialog title={`Delete ${deleting.name}?`} onClose={() => setDeleting(null)}>
          <p>Their decks go too, on your phones as well. Their past games keep their name and stay in the stats.</p>
          <div className="dialog-actions">
            <button
              className="btn danger"
              disabled={locked}
              onClick={async () => {
                if (await onEdit({ type: "deletePlayer", uid: deleting.uid! })) setDeleting(null);
              }}
            >
              Delete
            </button>
            <button className="btn" onClick={() => setDeleting(null)}>
              Cancel
            </button>
          </div>
        </Dialog>
      )}
      {dropDeck && (
        <Dialog title={`Delete ${dropDeck.name}?`} onClose={() => setDropDeck(null)}>
          <p>Its past games keep it in the stats.</p>
          <div className="dialog-actions">
            <button
              className="btn danger"
              disabled={locked}
              onClick={async () => {
                if (await onEdit({ type: "deleteFriendDeck", uid: dropDeck.uid! })) setDropDeck(null);
              }}
            >
              Delete
            </button>
            <button className="btn" onClick={() => setDropDeck(null)}>
              Cancel
            </button>
          </div>
        </Dialog>
      )}
      {deckFor && <FriendDeckDialog player={deckFor} busy={busy} onClose={() => setDeckFor(null)} onEdit={onEdit} />}
    </>
  );
}

function NameDialog({ title, start, busy, onClose, onSave }: { title: string; start: string; busy: boolean; onClose: () => void; onSave: (name: string) => void }) {
  const [name, setName] = useState(start);
  return (
    <Dialog title={title} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (name.trim()) onSave(name.trim());
        }}
      >
        <label className="field">
          <span>Name</span>
          <input autoFocus value={name} maxLength={60} onChange={(e) => setName(e.target.value)} />
        </label>
        <div className="dialog-actions">
          <button className="btn primary" type="submit" disabled={busy || !name.trim()}>
            Save
          </button>
          <button className="btn" type="button" onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </Dialog>
  );
}

/** A friend's deck: its commander (and partner) looked up on Scryfall, as the app does. */
function FriendDeckDialog({ player, busy, onClose, onEdit }: { player: Player; busy: boolean; onClose: () => void; onEdit: (e: GameEdit) => Promise<boolean> }) {
  const [name, setName] = useState("");
  const [commander, setCommander] = useState("");
  const [partner, setPartner] = useState("");
  const [link, setLink] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [looking, setLooking] = useState(false);

  async function save() {
    setError(null);
    setLooking(true);
    const r = await api<{ found: GuestCommander & { partnerScryfallId: string | null } }>("/api/games", { lookup: { commander, partner: partner || null } });
    setLooking(false);
    if (!r.ok) return setError(r.data.error ?? "The commander could not be looked up.");
    const f = r.data.found;
    if (!f.scryfallId) return setError(`Scryfall doesn't know a card called "${commander.trim()}". Check the name.`);
    if (
      await onEdit({
        type: "saveFriendDeck",
        deck: {
          uid: null,
          playerUid: player.uid!,
          name: name.trim(),
          commander: f.commander,
          scryfallId: f.scryfallId,
          partner: f.partner,
          partnerScryfallId: f.partnerScryfallId,
          colorIdentity: f.colorIdentity,
          archidektUrl: link.trim() || null,
        },
      })
    )
      onClose();
  }

  return (
    <Dialog title={`A deck of ${player.name}`} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (commander.trim()) save();
        }}
      >
        <label className="field">
          <span>Commander</span>
          <input autoFocus value={commander} maxLength={120} placeholder="e.g. Atraxa, Praetors' Voice" onChange={(e) => setCommander(e.target.value)} />
        </label>
        <label className="field">
          <span>Partner (optional)</span>
          <input value={partner} maxLength={120} onChange={(e) => setPartner(e.target.value)} />
        </label>
        <label className="field">
          <span>Deck name (optional, else the commander)</span>
          <input value={name} maxLength={120} onChange={(e) => setName(e.target.value)} />
        </label>
        <label className="field">
          <span>Archidekt link (optional)</span>
          <input value={link} maxLength={300} placeholder="https://archidekt.com/decks/…" onChange={(e) => setLink(e.target.value)} />
        </label>
        {error && (
          <div className="bar error" role="alert">
            {error}
          </div>
        )}
        <div className="dialog-actions">
          <button className="btn primary" type="submit" disabled={busy || looking || !commander.trim()}>
            {looking ? "Looking up…" : "Add the deck"}
          </button>
          <button className="btn" type="button" onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </Dialog>
  );
}
