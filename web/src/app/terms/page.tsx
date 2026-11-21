import type { Metadata } from "next";
import Link from "next/link";
import { CONTACT_EMAIL } from "@/components/SiteFooter";

export const metadata: Metadata = { title: "Terms · MTG Trader" };

/** The terms of use for the app and the website, in plain words (app 1.106). */
export default function TermsPage() {
  return (
    <main className="page narrow legal-page">
      <p>
        <Link href="/">← MTG Trader</Link>
      </p>
      <h1>Terms</h1>
      <p className="muted small">Last changed 21 November 2026.</p>
      <p>
        These terms are for the MTG Trader Android app and this website. By using them you agree to them.
      </p>

      <h2>A free hobby project</h2>
      <p>
        MTG Trader is free. It is made by one person, joelvdc, in their spare time, and given &quot;as is&quot;, without promises that it always works, stays available or
        is free of mistakes. It can change or stop at any time. If the website stops, account holders get notice where possible, so they can take their data.
      </p>

      <h2>Accounts</h2>
      <ul>
        <li>Accounts are by invitation.</li>
        <li>Keep your password safe. You are responsible for what happens with your account.</li>
        <li>One person per account.</li>
      </ul>

      <h2>Your data stays yours</h2>
      <p>
        You own what you put in. You allow MTG Trader to store and process it only to run the app and the website for you (see the{" "}
        <Link href="/privacy">Privacy page</Link>). You can take it with you and delete it.
      </p>

      <h2>Fair use</h2>
      <p>Don&apos;t misuse the service:</p>
      <ul>
        <li>no attacks on it;</li>
        <li>no trying to get into other accounts;</li>
        <li>no automated mass requests;</li>
        <li>no unlawful or hurtful content in shared links, deck names or notes.</li>
      </ul>
      <p>Accounts that misuse it can be switched off.</p>

      <h2>Prices and card information</h2>
      <p>
        Prices and card details come from outside sources (see <Link href="/legal">Legal &amp; attribution</Link>). They are for guidance only; check before you buy,
        sell or trade. MTG Trader is not responsible for trades or purchases you make.
      </p>

      <h2>Outside services</h2>
      <p>
        Links and data from Scryfall, Cardmarket, EDHREC and others are theirs, under their own terms. When you add your own AI key or CardTrader token, that
        service&apos;s terms apply to you.
      </p>

      <h2>Wizards of the Coast</h2>
      <p>
        MTG Trader is unofficial Fan Content permitted under the Fan Content Policy. Not approved or endorsed by Wizards. Portions of the materials used are
        property of Wizards of the Coast. © Wizards of the Coast LLC.
      </p>

      <h2>Responsibility</h2>
      <p>
        As far as the law allows, MTG Trader is not liable for loss of data or other damage from using it. Keep your own backups (the app can make them). Nothing in
        these terms limits rights you have under the consumer law of the country where you live.
      </p>

      <h2>Changes and contact</h2>
      <p>
        Changes are posted here with a new date. Questions: <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
      </p>
    </main>
  );
}
