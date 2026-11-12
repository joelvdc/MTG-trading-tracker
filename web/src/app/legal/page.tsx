import type { Metadata } from "next";
import { ISSUES_URL } from "@/components/SiteFooter";

export const metadata: Metadata = { title: "Legal & attribution · MTG Trader" };

const SOURCES: { name: string; url: string; what: string }[] = [
  { name: "Scryfall", url: "https://scryfall.com", what: "card details, pictures, search, EDHREC ranks and TCGplayer prices" },
  { name: "Cardmarket", url: "https://www.cardmarket.com", what: "the public daily price guide (prices in euros)" },
  { name: "TCGplayer", url: "https://www.tcgplayer.com", what: "prices in dollars (through Scryfall)" },
  { name: "Card Kingdom", url: "https://www.cardkingdom.com", what: "its price list and what it pays for cards" },
  { name: "CardTrader", url: "https://www.cardtrader.com", what: "links to its listings (prices only in the app, with your own token)" },
  { name: "EDHREC", url: "https://edhrec.com", what: "Commander recommendations" },
  { name: "recommander.cards", url: "https://recommander.cards", what: "Commander recommendations" },
  { name: "Commander Salt", url: "https://commandersalt.com", what: "deck brackets, scores and combos" },
  { name: "Commander Spellbook", url: "https://commanderspellbook.com", what: "the combos the deck pages link to" },
  { name: "Archidekt", url: "https://archidekt.com", what: "decklists and the collection export" },
  { name: "MTGJSON", url: "https://mtgjson.com", what: "preconstructed decks" },
  { name: "European Central Bank", url: "https://www.ecb.europa.eu", what: "exchange rates" },
];

/** Who the data and pictures come from, the fonts' licences, and the Fan Content Policy (2026-11-13). */
export default function LegalPage() {
  return (
    <main className="page narrow legal-page">
      <p>
        <a href="/">← MTG Trader</a>
      </p>
      <h1>Legal &amp; attribution</h1>

      <h2>Wizards of the Coast</h2>
      <p>
        MTG Trader is unofficial Fan Content permitted under the{" "}
        <a href="https://company.wizards.com/en/legal/fancontentpolicy" target="_blank" rel="noreferrer">
          Fan Content Policy
        </a>
        . Not approved or endorsed by Wizards. Portions of the materials used are property of Wizards of the Coast. © Wizards of the Coast LLC. Magic: The Gathering, card
        names, card pictures and mana symbols belong to Wizards of the Coast.
      </p>

      <h2>Where the data comes from</h2>
      <p>MTG Trader is free and not affiliated with or endorsed by any of these. Prices are for guidance only.</p>
      <ul>
        {SOURCES.map((s) => (
          <li key={s.name}>
            <a href={s.url} target="_blank" rel="noreferrer">
              {s.name}
            </a>
            : {s.what}
          </li>
        ))}
      </ul>
      <p className="small muted">The Android app also uses EDH Power Level and ScrollVault for power levels.</p>

      <h2>Fonts</h2>
      <ul>
        <li>
          <a href="https://mana.andrewgioia.com" target="_blank" rel="noreferrer">
            Mana
          </a>{" "}
          by Andrew Gioia, for the mana symbols, under the SIL Open Font License 1.1.
        </li>
        <li>
          <a href="https://github.com/fponticelli/dicefont" target="_blank" rel="noreferrer">
            Dicefont
          </a>{" "}
          by Franco Ponticelli (in the app), under the SIL Open Font License 1.1.
        </li>
      </ul>

      <h2>The project</h2>
      <p>
        The app and this website are a hobby project; the code is on{" "}
        <a href="https://github.com/joelvdc/MTG-trading-tracker" target="_blank" rel="noreferrer">
          GitHub
        </a>
        . Questions, mistakes or a request from a data source:{" "}
        <a href={ISSUES_URL} target="_blank" rel="noreferrer">
          open an issue there
        </a>
        . See also <a href="/privacy">Privacy</a>.
      </p>
    </main>
  );
}
