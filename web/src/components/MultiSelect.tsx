"use client";

import { useEffect, useRef, useState } from "react";
import type { JSX } from "react";

/**
 * A drop-down for choosing several of a list (the Filter window's Type, Rarity, Condition…): a button shaped like an
 * input shows the chosen texts, and a panel under it holds one tick box per option. Closes on a click outside or
 * Escape; the tick boxes are real checkboxes, so Tab and Space work as usual.
 */
export function MultiSelect<T extends string>({
  label,
  options,
  value,
  onChange,
  anyText = "Any",
}: {
  /** For the button's and the panel's aria-label. */
  label: string;
  /** [value, text] pairs, in the order they are shown. */
  options: [T, string][] | readonly (readonly [T, string])[];
  value: T[];
  onChange: (next: T[]) => void;
  anyText?: string;
}): JSX.Element {
  const [open, setOpen] = useState(false);
  const wrap = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const list: readonly (readonly [T, string])[] = options;
  const texts = list.filter(([k]) => value.includes(k)).map(([, text]) => text);
  const shown = texts.length === 0 ? anyText : texts.join(", ");

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [open]);

  const close = () => {
    setOpen(false);
    button.current?.focus();
  };

  return (
    <div
      className="multiselect"
      ref={wrap}
      onKeyDown={(e) => {
        // Only this panel closes; the Filter window (which also listens for Escape) stays open. preventDefault tells
        // that listener the Escape was handled, which is how it knows to stay open.
        if (e.key === "Escape" && open) {
          e.preventDefault();
          e.stopPropagation();
          close();
        }
      }}
    >
      <button
        type="button"
        ref={button}
        className="btn multiselect-button"
        aria-label={label}
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        <span className={texts.length === 0 ? "multiselect-value muted" : "multiselect-value"}>{shown}</span>
        <span className="multiselect-caret" aria-hidden="true">
          ▾
        </span>
      </button>
      {open && (
        <div className="multiselect-panel" role="group" aria-label={label}>
          {list.map(([key, text]) => (
            <label key={key} className="check multiselect-option">
              <input
                type="checkbox"
                checked={value.includes(key)}
                onChange={() => onChange(value.includes(key) ? value.filter((v) => v !== key) : [...value, key])}
              />
              <span>{text}</span>
            </label>
          ))}
          {value.length > 0 && (
            <button type="button" className="link multiselect-clear" onClick={() => onChange([])}>
              Clear
            </button>
          )}
        </div>
      )}
    </div>
  );
}
