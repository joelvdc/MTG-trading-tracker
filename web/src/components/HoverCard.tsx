"use client";

import { useCallback, useEffect, useState, type MouseEvent } from "react";

const KEY = "mtg.hoverCard";

/** The "Card on hover" choice, remembered per browser; on unless switched off. */
export function useHoverSetting(): [boolean, (on: boolean) => void] {
  const [on, setOn] = useState(true);
  useEffect(() => {
    try {
      if (localStorage.getItem(KEY) === "off") setOn(false);
    } catch {
      // Storage unavailable: stays on.
    }
  }, []);
  const change = useCallback((v: boolean) => {
    setOn(v);
    try {
      localStorage.setItem(KEY, v ? "on" : "off");
    } catch {
      // Not remembered; fine.
    }
  }, []);
  return [on, change];
}

const W = 240;
const H = (W * 680) / 488;

/**
 * A card picture that follows the mouse over a list (like Archidekt's): [follow] for a row's mouse events, [hide] when
 * it leaves or opens, [picture] to render. Does nothing when [enabled] is false or there's no mouse.
 */
export function useHoverCard(enabled: boolean) {
  const [at, setAt] = useState<{ url: string; x: number; y: number } | null>(null);
  useEffect(() => {
    if (!enabled) setAt(null);
  }, [enabled]);
  const follow = (url: string | null) => (e: MouseEvent) => {
    if (!enabled || !url || !window.matchMedia("(hover: hover)").matches) return;
    const x = e.clientX + 24 + W > window.innerWidth ? e.clientX - 24 - W : e.clientX + 24;
    const y = Math.max(8, Math.min(e.clientY - H / 2, window.innerHeight - H - 8));
    setAt({ url, x, y });
  };
  const hide = () => setAt(null);
  const picture = at ? (
    // eslint-disable-next-line @next/next/no-img-element
    <img className="hover-card" src={at.url} alt="" style={{ left: at.x, top: at.y }} />
  ) : null;
  return { follow, hide, picture };
}
