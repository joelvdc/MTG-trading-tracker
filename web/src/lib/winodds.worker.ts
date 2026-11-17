// Works a deck's win turn out with the app's own code (compiled from WinOdds.kt), away from the page.
// The page plays the app's four batches at once (a worker each) and merges their tallies (winMergeJson), so a first
// estimate can come after a few games of each rather than after all 10,000.
import { winBatchJson, winMergeJson } from "./winodds-js/winodds-kt.mjs";

type BatchMessage = { mode: "batch"; cards: string; identity: string; saltCard: string | null; batch: number; snapshotAt: number };
type MergeMessage = { mode: "merge"; cards: string; identity: string; saltCard: string | null; tallies: string };

self.onmessage = (e: MessageEvent<BatchMessage | MergeMessage>) => {
  try {
    // How many games have started, every 100 (for the progress bar), the tally after a few of them (for the first
    // estimate), then the batch's tally; or the answer from a list of tallies (the estimate's, or the 4 batches').
    const m = e.data;
    if (m.mode === "merge") {
      const result = winMergeJson(m.cards, m.identity, m.saltCard, m.tallies);
      self.postMessage({ result });
      return;
    }
    const tally = winBatchJson(
      m.cards,
      m.identity,
      m.saltCard,
      m.batch,
      null,
      m.snapshotAt,
      (started) => self.postMessage({ started }),
      (tallyJson) => self.postMessage({ snapshot: tallyJson }),
    );
    self.postMessage({ tally });
  } catch (err) {
    self.postMessage({ error: err instanceof Error ? err.message : String(err) });
  }
};
