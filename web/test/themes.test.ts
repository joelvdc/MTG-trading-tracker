import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { contrast, SYSTEM_THEME_COLORS, THEME_CHOICES, THEME_TOKENS, themeBootScript, themeById, themeCss, THEMES } from "@/lib/themes";

const SRC = join(__dirname, "..", "src");

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((f) => {
    const p = join(dir, f);
    return statSync(p).isDirectory() ? files(p) : /\.(tsx?|css)$/.test(f) ? [p] : [];
  });
}

describe("themes", () => {
  it("has the eight choices, System first", () => {
    expect(THEME_CHOICES.map((t) => t.id)).toEqual(["system", "dark", "light", "plains", "island", "swamp", "mountain", "forest"]);
  });

  it("gives every token as a six-digit colour in every theme", () => {
    for (const t of THEMES) {
      expect(Object.keys(t.colors).sort(), t.id).toEqual([...THEME_TOKENS].sort());
      for (const v of Object.values(t.colors)) expect(v, t.id).toMatch(/^#[0-9a-f]{6}$/);
    }
  });

  // WCAG AA for normal text: 4.5 to 1.
  const pairs: [string, string][] = [
    ["text", "bg"], ["text", "surface"], ["text", "raised"], ["text", "hover"], ["text", "selected"],
    ["muted", "bg"], ["muted", "surface"],
    ["accent", "bg"], ["accent", "surface"], ["accent", "raised"], ["accent", "selected"],
    ["on-accent", "accent"], ["on-accent", "accent-hover"],
    ["accent2", "bg"], ["accent2", "surface"],
    ["on-accent2", "accent2"],
    ["red-text", "bg"], ["red-text", "surface"],
    ["green-text", "bg"], ["green-text", "surface"],
    ["blue-text", "bg"], ["blue-text", "surface"],
  ];
  for (const t of THEMES) {
    it(`${t.name} is readable`, () => {
      const c = t.colors as Record<string, string>;
      const low = pairs.filter(([fg, bg]) => contrast(c[fg], c[bg]) < 4.5).map(([fg, bg]) => `${fg} on ${bg}: ${contrast(c[fg], c[bg]).toFixed(2)}`);
      expect(low).toEqual([]);
    });
  }

  it("dark themes are dark and light themes light", () => {
    for (const t of THEMES) expect(contrast(t.colors.bg, "#000000") < contrast(t.colors.bg, "#ffffff"), t.id).toBe(t.dark);
  });

  it("follows the computer when nothing is chosen, with matching browser bar colours", () => {
    const css = themeCss();
    expect(css).toContain(`:root{--bg:${themeById("dark")!.colors.bg};`);
    expect(css).toContain("@media (prefers-color-scheme: light){:root:not([data-theme]){--bg:" + themeById("light")!.colors.bg);
    for (const t of THEMES) expect(css).toContain(`:root[data-theme="${t.id}"]{`);
    expect(SYSTEM_THEME_COLORS.map((s) => s.color)).toEqual([themeById("light")!.colors.bg, themeById("dark")!.colors.bg]);
    expect(themeBootScript()).toContain('"mtg.theme"');
  });

  it("every colour the site uses is defined", () => {
    const css = readFileSync(join(SRC, "app", "globals.css"), "utf-8");
    const root = css.slice(css.indexOf(":root {"), css.indexOf("}"));
    const fixed = [...root.matchAll(/--([a-z0-9-]+):/g)].map((m) => m[1]);
    const defined = new Set([...THEME_TOKENS, ...fixed]);
    const used = new Set(files(SRC).flatMap((f) => [...readFileSync(f, "utf-8").matchAll(/var\(--([a-z0-9-]+)/g)].map((m) => m[1])));
    expect([...used].filter((u) => !defined.has(u))).toEqual([]);
  });
});
