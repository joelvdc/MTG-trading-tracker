import type { Metadata } from "next";
import Link from "next/link";
import { CONTACT_EMAIL, ISSUES_URL } from "@/components/SiteFooter";

export const metadata: Metadata = { title: "Privacy · MTG Trader" };

/** What the app and the website keep, where, why and for how long, in plain words (2026-11-21). */
export default function PrivacyPage() {
  return (
    <main className="page narrow legal-page">
      <p>
        <Link href="/">← MTG Trader</Link>
      </p>
      <h1>Privacy</h1>
      <p className="muted small">Last changed 21 November 2026.</p>
      <p>
        MTG Trader is a free, private hobby project: an Android app and this website for keeping a Magic: The Gathering collection, wishlist, decks, trades and games.
        There are no adverts, no tracking and no selling of data.
      </p>

      <h2>Who is responsible</h2>
      <p>
        MTG Trader is made and run by one private person, joelvdc, as a hobby. They decide what is kept and why; in EU privacy law that makes them the
        &quot;controller&quot;. Contact:{" "}
        <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>. Questions and requests about your data go there. The{" "}
        <a href={ISSUES_URL} target="_blank" rel="noreferrer">
          project&apos;s GitHub page
        </a>{" "}
        is for bugs and ideas; never post private things there.
      </p>

      <h2>The Android app</h2>
      <p>
        Everything stays on your phone: your collection, decks, wishlist, trades, games, scans and settings. The app has no account of its own and sends nothing to
        the developer. Phone backups by Android are switched off for the app.
      </p>
      <ul>
        <li>
          <strong>Camera:</strong> used only for the card scanner. Pictures are read on the phone (Google&apos;s ML Kit text recognition, which runs on the device)
          and are not stored or sent.
        </li>
        <li>
          <strong>Notifications:</strong> price alerts and reminders, made on the phone.
        </li>
        <li>
          <strong>Syncing</strong> is optional and you choose where: to your own Nextcloud (the app password is stored encrypted on the phone and only sent to your
          Nextcloud), or to a website account (then &quot;What the website keeps&quot; below applies; the login token is stored encrypted on the phone).
        </li>
        <li>
          <strong>Card and price sites:</strong> the app asks Scryfall, Cardmarket, TCGplayer, Card Kingdom, CardTrader, EDHREC, recommander.cards, Commander Salt,
          Commander Spellbook, EDH Power Level, Archidekt, MTGJSON and the European Central Bank for card details, prices, deck lists and exchange rates. They get
          the cards, deck or Archidekt user name asked about, plus what every website gets (your internet address); not your collection. If you add a CardTrader
          token, it is stored encrypted on the phone and sent only to CardTrader.
        </li>
        <li>
          <strong>Removing:</strong> uninstalling the app removes its data from the phone.
        </li>
      </ul>

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
          <strong>Your AI key:</strong> if you add one under Settings → AI key, it is stored encrypted, used only for your own requests and never shown
          again. Remove it there at any time.
        </li>
        <li>
          <strong>In your browser:</strong> a login cookie, and small settings such as the columns and sort you chose. Nothing is used to follow you around the web.
        </li>
      </ul>

      <h2>Why it is kept (legal bases)</h2>
      <ul>
        <li>
          <strong>To give you the service you asked for</strong> (your account, sync, prices and shared links): this is needed to run the account you created.
        </li>
        <li>
          <strong>Because you chose to</strong> (the weekly email, an AI key, a Nextcloud backup): your permission, which you can switch off at any time.
        </li>
        <li>
          <strong>To keep the site safe and working</strong> (the login cookie and short technical logs at Vercel): our legitimate interest.
        </li>
      </ul>

      <h2>How long</h2>
      <ul>
        <li>Account data is kept until you delete your account or ask for it to be removed. Then everything goes within 30 days, backup copies included (the daily backups roll over after 10 days anyway).</li>
        <li>Price history is kept for up to a year.</li>
        <li>Shared link counts are kept until you remove the link.</li>
        <li>Vercel keeps its technical logs for a short time (days).</li>
        <li>The app&apos;s data stays on your phone until you remove it or uninstall the app.</li>
      </ul>

      <h2>Where it is kept and who helps</h2>
      <ul>
        <li>Vercel (Vercel Inc., an American company; servers in Stockholm) runs the website; its database is Neon (in the EU); daily price lists are kept in Vercel&apos;s file storage.</li>
        <li>If you connect a Nextcloud backup, a copy of your sync file goes to your own Nextcloud every night.</li>
        <li>The weekly email is sent through Resend (an American company).</li>
        <li>When you use AI for your account, the AI service for your account handles that request.</li>
      </ul>
      <p>
        Where an American company may handle data, it is covered by the EU–US Data Privacy Framework or the EU&apos;s standard contractual clauses. Nothing is sold or
        shared for advertising.
      </p>

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
        <li>
          <strong>AI services:</strong> when you press Write with AI, the deck&apos;s name, cards, card notes, scores, combos and your current primer text go to the AI
          service used for your account (OpenCode Go, Mistral or OpenRouter). Prices and your collection are not sent. That service&apos;s own privacy terms
          apply.
        </li>
      </ul>

      <h2>Your rights</h2>
      <p>You can ask:</p>
      <ul>
        <li>to see a copy of your data;</li>
        <li>to correct it;</li>
        <li>to have it deleted;</li>
        <li>to get it in a file you can take elsewhere (the sync file does this);</li>
        <li>to limit or object to how it is used;</li>
        <li>to withdraw a choice (consent) at any time.</li>
      </ul>
      <p>
        Email <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>; you get an answer within a month. You can also complain to the data protection authority in your
        country.
      </p>

      <h2>Removing your data</h2>
      <p>
        You can log out devices and switch off shared links and the email in Settings. To delete your account and everything kept for it, email{" "}
        <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> (or ask the person who invited you).
      </p>

      <h2>Children</h2>
      <p>MTG Trader is not meant for children under 16. Accounts are by invitation.</p>

      <h2>Changes</h2>
      <p>Changes are posted on this page with a new date. Big changes are mentioned in the app&apos;s release notes.</p>
    </main>
  );
}
