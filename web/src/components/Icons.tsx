"use client";

/**
 * The website's shared round buttons, so they look the same on every page: the "Card on hover" switch, the refresh
 * button and the flip icon for double-faced cards.
 */

/** The "Card on hover" switch, as a round button inside a `.seg` pill (gold when on, crossed out when off). */
export function HoverToggle({ on, onChange }: { on: boolean; onChange: (on: boolean) => void }) {
  return (
    <button
      type="button"
      className={on ? "on hover-toggle" : "hover-toggle"}
      aria-pressed={on}
      aria-label="Card on hover"
      title={on ? "Card on hover: on" : "Card on hover: off"}
      onClick={() => onChange(!on)}
    >
      <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
        <rect x="11" y="6" width="10" height="14" rx="1.8" fill="currentColor" opacity={on ? 1 : 0.45} />
        <path d="M3 3l4.2 11 1.6-4.4L13 8z" fill="currentColor" stroke="var(--raised)" strokeWidth="1" strokeLinejoin="round" />
        {!on && <path d="M3 21L21 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />}
      </svg>
    </button>
  );
}

/** The "Card on hover" switch on its own (pages without the list / pictures buttons). */
export function HoverSwitch({ on, onChange }: { on: boolean; onChange: (on: boolean) => void }) {
  return (
    <div className="seg hover-toggle" role="group" aria-label="Card on hover">
      <HoverToggle on={on} onChange={onChange} />
    </div>
  );
}

/**
 * The refresh button: round and grey like the `.seg` pills, with an arrow that spins while [busy]. With a [label]
 * (nothing loaded yet) it widens to show the label beside the arrow.
 */
export function RefreshButton({ onClick, busy, label, title }: { onClick: () => void; busy: boolean; label?: string; title: string }) {
  return (
    <button
      type="button"
      className={`refresh-btn${label ? " wide" : ""}${busy ? " busy" : ""}`}
      onClick={onClick}
      disabled={busy}
      title={title}
      aria-label={title}
    >
      <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
        <path d="M20 12a8 8 0 1 1-2.34-5.66" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
        <path d="M20 4v5h-5" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {label && <span>{label}</span>}
    </button>
  );
}

/** "Turn the card over": a card with a curved arrow under it (not ⟳, which means refresh). */
export function FlipIcon({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
      <rect x="7" y="2.5" width="10" height="13" rx="1.6" fill="none" stroke="currentColor" strokeWidth="2" />
      <path d="M3.5 16.5c3.5 4 13.5 4 17 0" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <path d="M17 15.5l3.5 1 -0.8 3.6" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
