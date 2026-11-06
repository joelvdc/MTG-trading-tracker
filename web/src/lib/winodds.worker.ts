// Works a deck's win turn out with the app's own code (compiled from WinOdds.kt), away from the page.
import { winOddsJson } from "./winodds-js/winodds-kt.mjs";

self.onmessage = (e: MessageEvent<{ cards: string; identity: string; saltCard: string | null }>) => {
  try {
    self.postMessage({ result: winOddsJson(e.data.cards, e.data.identity, e.data.saltCard) });
  } catch (err) {
    self.postMessage({ error: err instanceof Error ? err.message : String(err) });
  }
};
