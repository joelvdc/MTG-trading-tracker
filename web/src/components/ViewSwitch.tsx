"use client";

import { useEffect, useState } from "react";
import { HoverToggle } from "./Icons";

export type Layout = "list" | "images";
export type PicSize = "s" | "m" | "l";

const SIZE_KEY = "mtg.picSize";
const SIZE_EVENT = "mtg-pic-size";
const SIZES: { size: PicSize; label: string; box: number }[] = [
  { size: "s", label: "Small pictures", box: 8 },
  { size: "m", label: "Medium pictures", box: 12 },
  { size: "l", label: "Large pictures", box: 16 },
];

/**
 * The picture size of the Images views (collection, wishlist, scans, recommendations), remembered per browser and
 * set on the page as html[data-pic-size], which the picture grids' CSS reads.
 */
export function usePicSize(): [PicSize, (s: PicSize) => void] {
  const [size, setSize] = useState<PicSize>("m");
  useEffect(() => {
    const read = () => {
      let s: PicSize = "m";
      try {
        const v = localStorage.getItem(SIZE_KEY);
        if (v === "s" || v === "m" || v === "l") s = v;
      } catch {
        // Storage unavailable: medium.
      }
      setSize(s);
      document.documentElement.dataset.picSize = s;
    };
    read();
    window.addEventListener(SIZE_EVENT, read);
    return () => window.removeEventListener(SIZE_EVENT, read);
  }, []);
  const set = (s: PicSize) => {
    try {
      localStorage.setItem(SIZE_KEY, s);
    } catch {
      // Not remembered.
    }
    document.documentElement.dataset.picSize = s;
    setSize(s);
    window.dispatchEvent(new Event(SIZE_EVENT));
  };
  return [size, set];
}

/** Small / medium / large pictures, as three round buttons with a growing square. */
function SizeSwitch() {
  const [size, setSize] = usePicSize();
  return (
    <div className="seg" role="group" aria-label="Picture size">
      {SIZES.map((s) => (
        <button key={s.size} type="button" className={size === s.size ? "on" : undefined} aria-pressed={size === s.size} aria-label={s.label} title={s.label} onClick={() => setSize(s.size)}>
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
            <rect x={12 - s.box / 2} y={12 - s.box / 2} width={s.box} height={s.box} rx="2" fill="currentColor" />
          </svg>
        </button>
      ))}
    </div>
  );
}

/**
 * The list / pictures buttons with, for the list, the "Card on hover" switch and, for the pictures, the picture size
 * (collection, wishlist, scans, recommendations).
 */
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
    <>
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
    {layout === "images" && <SizeSwitch />}
    </>
  );
}
