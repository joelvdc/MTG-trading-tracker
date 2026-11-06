// Works a deck's win turn out with the app's own code (compiled from WinOdds.kt), away from the page.
import { winOddsJson } from "./winodds-js/winodds-kt.mjs";

self.onmessage = (e: MessageEvent<{ cards: string; identity: string; saltCard: string | null }>) => {
  try {
    // How many games have started, every 100 (for the progress bar), then the answer.
    const result = winOddsJson(e.data.cards, e.data.identity, e.data.saltCard, null, (started) => self.postMessage({ started }));
    self.postMessage({ result });
  } catch (err) {
    self.postMessage({ error: err instanceof Error ? err.message : String(err) });
  }
};
