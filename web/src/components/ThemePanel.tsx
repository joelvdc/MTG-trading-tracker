"use client";

import { useEffect, useState } from "react";
import { applyTheme, DEFAULT_THEME, storedTheme, THEME_CHOICES, themeById, type Theme } from "@/lib/themes";

/** A small picture of a theme: its page, a card on it, and its two accents. */
function Swatch({ t }: { t: Theme }) {
  const c = t.colors;
  return (
    <span className="theme-swatch" style={{ background: c.bg }} aria-hidden>
      <span className="theme-swatch-card" style={{ background: c.surface, borderColor: c.line }}>
        <span style={{ background: c.accent }} />
        <span style={{ background: c.accent2 }} />
      </span>
    </span>
  );
}

/** The website's look, kept in this browser only (the phones keep their own). */
export function ThemePanel() {
  const [chosen, setChosen] = useState(DEFAULT_THEME);
  useEffect(() => setChosen(storedTheme()), []);

  const pick = (id: string) => {
    applyTheme(id);
    setChosen(id);
  };
  const blurb = THEME_CHOICES.find((t) => t.id === chosen)?.blurb;

  return (
    <section className="card stack">
      <h2 className="section-head">Theme</h2>
      <div className="theme-grid" role="radiogroup" aria-label="Theme">
        {THEME_CHOICES.map((choice) => {
          const t = themeById(choice.id);
          return (
            <button
              key={choice.id}
              type="button"
              role="radio"
              aria-checked={chosen === choice.id}
              className={chosen === choice.id ? "theme-choice on" : "theme-choice"}
              onClick={() => pick(choice.id)}
            >
              {t ? (
                <Swatch t={t} />
              ) : (
                <span className="theme-swatch theme-swatch-split" aria-hidden>
                  <Swatch t={themeById("light")!} />
                  <Swatch t={themeById("dark")!} />
                </span>
              )}
              <span>{choice.name}</span>
            </button>
          );
        })}
      </div>
      <p className="muted small">{blurb} Kept in this browser only; the app on your phones keeps its own theme.</p>
    </section>
  );
}
