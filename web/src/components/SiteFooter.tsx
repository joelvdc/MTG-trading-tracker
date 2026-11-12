// The footer on every page (2026-11-13): the data sources, Wizards of the Coast's Fan Content Policy wording, and links
// to the privacy and legal pages and to the project's GitHub issues (the contact).

export const ISSUES_URL = "https://github.com/joelvdc/MTG-trading-tracker/issues";

/** The sources named in the footer; the legal page lists them all with what each gives. */
const SOURCES: [string, string][] = [
  ["Scryfall", "https://scryfall.com"],
  ["Cardmarket", "https://www.cardmarket.com"],
  ["TCGplayer", "https://www.tcgplayer.com"],
  ["Card Kingdom", "https://www.cardkingdom.com"],
  ["EDHREC", "https://edhrec.com"],
  ["Commander Salt", "https://commandersalt.com"],
  ["Commander Spellbook", "https://commanderspellbook.com"],
  ["Archidekt", "https://archidekt.com"],
  ["MTGJSON", "https://mtgjson.com"],
];

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer-in">
        <p>
          Data from{" "}
          {SOURCES.map(([name, url], i) => (
            <span key={name}>
              {i > 0 && " · "}
              <a href={url} target="_blank" rel="noreferrer">
                {name}
              </a>
            </span>
          ))}
          . Not affiliated with any of them.
        </p>
        <p>
          MTG Trader is unofficial Fan Content permitted under the{" "}
          <a href="https://company.wizards.com/en/legal/fancontentpolicy" target="_blank" rel="noreferrer">
            Fan Content Policy
          </a>
          . Not approved or endorsed by Wizards. Portions of the materials used are property of Wizards of the Coast. © Wizards of the Coast LLC.
        </p>
        <p className="site-footer-links">
          <a href="/privacy">Privacy</a>
          <a href={ISSUES_URL} target="_blank" rel="noreferrer">
            Contact
          </a>
          <a href="/legal">Legal &amp; attribution</a>
        </p>
      </div>
    </footer>
  );
}
