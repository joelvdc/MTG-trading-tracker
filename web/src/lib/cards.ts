// Lists and names shared by the server and the pages (the app's Models.kt CONDITIONS and LANGUAGES).
import type { Finish } from "./pricing";

export const CONDITIONS: [string, string][] = [
  ["MT", "Mint"],
  ["NM", "Near Mint"],
  ["EX", "Excellent"],
  ["GD", "Good"],
  ["LP", "Light Played"],
  ["PL", "Played"],
  ["PO", "Poor"],
];

export const LANGUAGES: [string, string][] = [
  ["EN", "English"],
  ["DE", "German"],
  ["FR", "French"],
  ["IT", "Italian"],
  ["ES", "Spanish"],
  ["PT", "Portuguese"],
  ["JA", "Japanese"],
  ["KO", "Korean"],
  ["RU", "Russian"],
  ["ZHS", "Chinese (S)"],
  ["ZHT", "Chinese (T)"],
];

export const FINISHES: Finish[] = ["NONFOIL", "FOIL", "ETCHED"];

export const FINISH_LABELS: Record<Finish, string> = { NONFOIL: "Non-foil", FOIL: "Foil", ETCHED: "Etched" };

export function conditionName(code: string): string {
  return CONDITIONS.find((c) => c[0] === code)?.[1] ?? code;
}

export function languageName(code: string): string {
  return LANGUAGES.find((c) => c[0] === code)?.[1] ?? code;
}

/** The finishes a printing is made in (the app's CardRef.finishes). */
export function finishesOf(card: { hasNonFoil: boolean; hasFoil: boolean; hasEtched: boolean }): Finish[] {
  const out: Finish[] = [];
  if (card.hasNonFoil) out.push("NONFOIL");
  if (card.hasFoil) out.push("FOIL");
  if (card.hasEtched) out.push("ETCHED");
  return out.length ? out : ["NONFOIL"];
}

/** [want] if the printing has it, else the nearest finish it has. */
export function resolveFinish(card: { hasNonFoil: boolean; hasFoil: boolean; hasEtched: boolean }, want: Finish): Finish {
  const has = finishesOf(card);
  if (has.includes(want)) return want;
  if (want === "NONFOIL") return has.includes("FOIL") ? "FOIL" : has[0];
  if (want === "FOIL") return has.includes("ETCHED") ? "ETCHED" : has[0];
  return has.includes("FOIL") ? "FOIL" : has[0];
}
