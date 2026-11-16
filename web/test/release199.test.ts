import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

// The card links in a primer (app 1.99): [[Sol Ring]] is a link to a deck card. The cases are shared with the Android
// app, which reads the same fixture, so the website and the app split a primer exactly the same way.

import { linkMatches, linkSegments, type PrimerSegment } from "@/lib/decknotes";

const fixture = path.join(__dirname, "fixtures");
const read = <T>(name: string): T => JSON.parse(readFileSync(path.join(fixture, name), "utf8")) as T;

interface LinkCases {
  segments: { name: string; input: string; output: PrimerSegment[] }[];
  matches: { name: string; link: string; card: string; match: boolean }[];
}

describe("the shared card-link cases (DeckNotes.linkSegments)", () => {
  const cases = read<LinkCases>("primerlinks-cases.json");
  for (const c of cases.segments) {
    it(c.name, () => expect(linkSegments(c.input)).toEqual(c.output));
  }
});

describe("the shared card-link cases (DeckNotes.linkMatches)", () => {
  const cases = read<LinkCases>("primerlinks-cases.json");
  for (const c of cases.matches) {
    it(c.name, () => expect(linkMatches(c.link, c.card)).toBe(c.match));
  }
});
