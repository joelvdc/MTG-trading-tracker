/**
 * The website's colour themes (2026-11-13). Each theme gives every colour token the stylesheet uses; the colours
 * that mean something (verdict green and red, foil, the trend arrows, mana symbols) stay the same in every theme and
 * live in globals.css. The choice is kept per browser in localStorage, not in the sync file.
 */

/** Every colour a theme must give, as CSS custom properties (`--<name>`). */
export const THEME_TOKENS = [
  "bg", // page background
  "surface", // cards and dialogs
  "raised", // inputs, buttons, raised parts
  "text",
  "muted", // secondary text
  "accent", // links, primary buttons, highlights (gold in Dark)
  "accent-hover",
  "on-accent", // text on an accent background
  "accent2", // second accent: tabs, chips, heatmaps (violet in Dark)
  "on-accent2",
  "line", // strong borders (inputs, buttons)
  "line-soft", // softer borders
  "divider", // table row lines
  "hover", // background of a hovered button
  "selected", // background of a chosen segment
  "row-hover", // background of a hovered list row
  "red-text", // warnings and delete text on the page background
  "green-text", // "good" text on the page background
  "blue-text",
] as const;

export type ThemeToken = (typeof THEME_TOKENS)[number];

export interface Theme {
  id: string;
  name: string;
  /** One line for the settings page. */
  blurb: string;
  dark: boolean;
  colors: Record<ThemeToken, string>;
}

export const THEMES: Theme[] = [
  {
    id: "dark",
    name: "Dark",
    blurb: "The app's dark theme: near-black violet with gold.",
    dark: true,
    colors: {
      bg: "#15131a",
      surface: "#211e28",
      raised: "#2a2633",
      text: "#ece6f0",
      muted: "#a9a3b2",
      accent: "#e0a526",
      "accent-hover": "#eab33a",
      "on-accent": "#231a00",
      accent2: "#b39ddb",
      "on-accent2": "#1b1523",
      line: "#4a4457",
      "line-soft": "#3a3446",
      divider: "#2f2a3a",
      hover: "#352f41",
      selected: "#443e50",
      "row-hover": "#272331",
      "red-text": "#ff8a80",
      "green-text": "#6fcf97",
      "blue-text": "#8ec9f0",
    },
  },
  {
    id: "light",
    name: "Light",
    blurb: "The app's light theme: soft white, dark gold and violet.",
    dark: false,
    colors: {
      bg: "#f7f3fa",
      surface: "#ffffff",
      raised: "#ede7f2",
      text: "#1d1b20",
      muted: "#5c5664",
      accent: "#7a5900",
      "accent-hover": "#8f6a00",
      "on-accent": "#ffffff",
      accent2: "#6a45b8",
      "on-accent2": "#ffffff",
      line: "#c3bccb",
      "line-soft": "#d8d2df",
      divider: "#e6e0eb",
      hover: "#e4ddeb",
      selected: "#e0d7ec",
      "row-hover": "#f2edf6",
      "red-text": "#b3261e",
      "green-text": "#1d7340",
      "blue-text": "#1f639e",
    },
  },
  {
    id: "plains",
    name: "Plains",
    blurb: "Light and warm: ivory and parchment with sunny gold.",
    dark: false,
    colors: {
      bg: "#f5eedd",
      surface: "#fffaf0",
      raised: "#ece2cb",
      text: "#2b2418",
      muted: "#655b49",
      accent: "#7a5300",
      "accent-hover": "#8c5f00",
      "on-accent": "#ffffff",
      accent2: "#7d4f22",
      "on-accent2": "#ffffff",
      line: "#cfc09d",
      "line-soft": "#ddd0b3",
      divider: "#e8ddc6",
      hover: "#e9ddc2",
      selected: "#e6d8b6",
      "row-hover": "#f3ead6",
      "red-text": "#a3301d",
      "green-text": "#2c6e37",
      "blue-text": "#285f8c",
    },
  },
  {
    id: "island",
    name: "Island",
    blurb: "Dark navy with sky blue.",
    dark: true,
    colors: {
      bg: "#0f1724",
      surface: "#172234",
      raised: "#1f2d43",
      text: "#e4ecf6",
      muted: "#9aabc2",
      accent: "#5bb4f0",
      "accent-hover": "#7cc4f4",
      "on-accent": "#04192b",
      accent2: "#8fd3e0",
      "on-accent2": "#062026",
      line: "#3a4c68",
      "line-soft": "#2c3c55",
      divider: "#223047",
      hover: "#26374f",
      selected: "#2f4059",
      "row-hover": "#1a2639",
      "red-text": "#ff8a80",
      "green-text": "#6fcf97",
      "blue-text": "#8ec9f0",
    },
  },
  {
    id: "swamp",
    name: "Swamp",
    blurb: "Deepest black and grey with bone white and dull gold.",
    dark: true,
    colors: {
      bg: "#0e0e10",
      surface: "#19181c",
      raised: "#232227",
      text: "#e8e4dc",
      muted: "#a29d94",
      accent: "#c9a85a",
      "accent-hover": "#d6b76c",
      "on-accent": "#1d1606",
      accent2: "#b9a7c9",
      "on-accent2": "#1a1420",
      line: "#424047",
      "line-soft": "#333138",
      divider: "#27262b",
      hover: "#2c2b31",
      selected: "#403e45",
      "row-hover": "#1e1d22",
      "red-text": "#ff8a80",
      "green-text": "#6fcf97",
      "blue-text": "#8ec9f0",
    },
  },
  {
    id: "mountain",
    name: "Mountain",
    blurb: "Dark charcoal red with ember orange.",
    dark: true,
    colors: {
      bg: "#1a1212",
      surface: "#261a19",
      raised: "#322221",
      text: "#f3e6e1",
      muted: "#b9a29c",
      accent: "#ff8a3d",
      "accent-hover": "#ff9f5e",
      "on-accent": "#2a1100",
      accent2: "#e8a89a",
      "on-accent2": "#2a0f0a",
      line: "#583e3a",
      "line-soft": "#46322f",
      divider: "#382826",
      hover: "#402e2b",
      selected: "#4e3734",
      "row-hover": "#2d1f1e",
      "red-text": "#ff9a8f",
      "green-text": "#6fcf97",
      "blue-text": "#8ec9f0",
    },
  },
  {
    id: "forest",
    name: "Forest",
    blurb: "Dark moss green with leaf green.",
    dark: true,
    colors: {
      bg: "#111812",
      surface: "#19231a",
      raised: "#222f23",
      text: "#e3eedf",
      muted: "#9fb39b",
      accent: "#7fcf6a",
      "accent-hover": "#97da85",
      "on-accent": "#0b2105",
      accent2: "#c9d98f",
      "on-accent2": "#1c2208",
      line: "#3d513d",
      "line-soft": "#304130",
      divider: "#263427",
      hover: "#2b3b2c",
      selected: "#3d513d",
      "row-hover": "#1d281e",
      "red-text": "#ff8a80",
      "green-text": "#8fe0ae",
      "blue-text": "#8ec9f0",
    },
  },
];

/** "system" follows the computer's light or dark setting. */
export const THEME_CHOICES = [{ id: "system", name: "System", blurb: "Light or Dark, following your computer's setting." }, ...THEMES];

export const THEME_KEY = "mtg.theme";
export const DEFAULT_THEME = "system";

export function themeById(id: string): Theme | undefined {
  return THEMES.find((t) => t.id === id);
}

function vars(t: Theme): string {
  return `${THEME_TOKENS.map((k) => `--${k}:${t.colors[k]};`).join("")}color-scheme:${t.dark ? "dark" : "light"};`;
}

/**
 * The stylesheet for all themes. Without a chosen theme (no `data-theme` on <html>) the page follows the computer:
 * Dark, or Light when the computer asks for light.
 */
export function themeCss(): string {
  const dark = themeById("dark")!;
  const light = themeById("light")!;
  return [
    `:root{${vars(dark)}}`,
    `@media (prefers-color-scheme: light){:root:not([data-theme]){${vars(light)}}}`,
    ...THEMES.map((t) => `:root[data-theme="${t.id}"]{${vars(t)}}`),
  ].join("\n");
}

/**
 * Runs before the page paints, so a chosen theme shows without a flash of the default. Also sets the browser's
 * top-bar colour.
 */
export function themeBootScript(): string {
  const bgs = Object.fromEntries(THEMES.map((t) => [t.id, t.colors.bg]));
  return `(function(){try{var t=localStorage.getItem(${JSON.stringify(THEME_KEY)});var b=${JSON.stringify(bgs)};if(t&&b[t]){document.documentElement.setAttribute("data-theme",t);document.addEventListener("DOMContentLoaded",function(){document.querySelectorAll('meta[name="theme-color"]').forEach(function(m){m.setAttribute("content",b[t]);});});}}catch(e){}})();`;
}

/** The browser top-bar colours when following the computer (Next writes them as two meta tags). */
export const SYSTEM_THEME_COLORS = [
  { media: "(prefers-color-scheme: light)", color: "#f7f3fa" },
  { media: "(prefers-color-scheme: dark)", color: "#15131a" },
];

/** Applies a choice at once and remembers it in this browser. */
export function applyTheme(id: string): void {
  const root = document.documentElement;
  const t = themeById(id);
  if (t) root.setAttribute("data-theme", t.id);
  else root.removeAttribute("data-theme");
  try {
    if (t) localStorage.setItem(THEME_KEY, t.id);
    else localStorage.removeItem(THEME_KEY);
  } catch {
    // Private windows can refuse storage; the theme still applies for this visit.
  }
  document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]').forEach((m) => {
    const sys = SYSTEM_THEME_COLORS.find((s) => s.media === m.media)?.color;
    m.content = t ? t.colors.bg : (sys ?? m.content);
  });
}

/** The stored choice, or "system". */
export function storedTheme(): string {
  try {
    const t = localStorage.getItem(THEME_KEY);
    return t && themeById(t) ? t : DEFAULT_THEME;
  } catch {
    return DEFAULT_THEME;
  }
}

// ---- Contrast (WCAG), used by the tests ----

function channel(c: number): number {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
}

export function luminance(hex: string): number {
  const h = hex.replace("#", "");
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16));
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

export function contrast(a: string, b: string): number {
  const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
}
