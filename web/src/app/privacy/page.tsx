import type { Metadata } from "next";
import { ISSUES_URL } from "@/components/SiteFooter";

export const metadata: Metadata = { title: "Privacy · MTG Trader" };

/** What the website keeps and where, in plain words (2026-11-13). */
export default function PrivacyPage() {
  return (
    <main className="page narrow legal-page">
      <p>
        <a href="/">← MTG Trader</a>
      </p>
      <h1>Privacy</h1>
      <p className="muted small">Last changed 13 November 2026.</p>
      <p>
        MTG Trader is a free, private hobby project: an Android app and this website for keeping a Magic: The Gathering collection, wishlist, decks, trades and games.
        There are no adverts, no tracking and no selling of data.
      </p>

      <h2>What the website keeps</h2>
      <ul>
        <li>
          <strong>Your account:</strong> your user name, your password (only as a scrambled hash, never the password itself) and the devices you are logged in on.
        </li>
        <li>
          <strong>Your MTG Trader data:</strong> the sync file the app and the website share (collection, binders, wishlist, decks, trades, games, scans and app settings),
          with up to 10 daily backup copies.
        </li>
        <li>
          <strong>Prices and history:</strong> your collection&apos;s value per day, each card&apos;s price per day (up to a year), and the wishlist price alerts that were reached.
        </li>
        <li>
          <strong>Website settings:</strong> your saved views, your shared links (and how often each was opened), and, if you switch it on, the weekly email&apos;s address and
          day.
        </li>
        <li>
          <strong>In your browser:</strong> a login cookie, and small settings such as the columns and sort you chose. Nothing is used to follow you around the web.
        </li>
      </ul>

      <h2>Where it is kept</h2>
      <ul>
        <li>The website runs on Vercel (servers in Stockholm); its database is Neon (in the EU); daily price lists are kept in Vercel&apos;s file storage.</li>
        <li>If you connect a Nextcloud backup, a copy of your sync file goes to your own Nextcloud every night.</li>
        <li>The weekly email is sent through Resend.</li>
      </ul>

      <h2>Who else sees what</h2>
      <ul>
        <li>
          <strong>Shared links:</strong> anyone with a link you made can see that deck, game, binder, collection or wishlist, with prices but never what you paid, your notes
          or your alerts. Switch links off in Settings → Shared links.
        </li>
        <li>
          <strong>Card and price sites:</strong> to show card details and prices the website asks Scryfall, EDHREC, Commander Salt, Archidekt and the others for card
          names and lists. They get the card or deck asked about, not your account.
        </li>
      </ul>

      <h2>Removing your data</h2>
      <p>
        You can log out devices and switch off shared links and the email in Settings. To have your account and everything kept for it removed, ask through the{" "}
        <a href={ISSUES_URL} target="_blank" rel="noreferrer">
          project&apos;s GitHub page
        </a>{" "}
        (without posting anything private there) or ask the person who invited you.
      </p>
    </main>
  );
}
