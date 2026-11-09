"use client";

import { HoverToggle } from "./Icons";

export type Layout = "list" | "images";

/** The list / pictures buttons and, for the list, the "Card on hover" switch (collection, wishlist, scans, recommendations). */
export function ViewSwitch({
  layout,
  onLayout,
  hover,
  onHover,
}: {
  layout: Layout;
  onLayout: (l: Layout) => void;
  hover: boolean;
  onHover: (on: boolean) => void;
}) {
  return (
    <div className="seg" role="group" aria-label="View">
      <button
        className={layout === "list" ? "on" : undefined}
        aria-pressed={layout === "list"}
        aria-label="View as list"
        title="View as list"
        onClick={() => onLayout("list")}
      >
        <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
          <circle cx="5" cy="6" r="1.6" fill="currentColor" />
          <circle cx="5" cy="12" r="1.6" fill="currentColor" />
          <circle cx="5" cy="18" r="1.6" fill="currentColor" />
          <path d="M9.5 6h10M9.5 12h10M9.5 18h10" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
        </svg>
      </button>
      <button
        className={layout === "images" ? "on" : undefined}
        aria-pressed={layout === "images"}
        aria-label="View as images"
        title="View as images"
        onClick={() => onLayout("images")}
      >
        <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
          <rect x="9" y="3" width="11" height="15" rx="2" fill="currentColor" opacity="0.55" transform="rotate(12 14.5 10.5)" />
          <rect x="4" y="5" width="11" height="15" rx="2" fill="currentColor" />
        </svg>
      </button>
      {layout === "list" && <HoverToggle on={hover} onChange={onHover} />}
    </div>
  );
}
