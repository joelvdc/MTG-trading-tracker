"use client";

import { useEffect, useRef, useState, type MouseEvent } from "react";

/** How long (ms) the mouse rests on a card before its big picture shows. */
const REST_MS = 300;

/**
 * The big picture of the card the mouse rests on, for the playing field's top-right corner (like Moxfield): [follow] for
 * a card's mouse moves, [hide] when it leaves or is pressed, [picture] to render. Does nothing when [enabled] is false
 * or there's no mouse.
 */
export function useCornerPreview(enabled: boolean) {
  const [shown, setShown] = useState<string | null>(null);
  const shownRef = useRef<string | null>(null);
  const timer = useRef<number | null>(null);

  const clearTimer = () => {
    if (timer.current !== null) {
      window.clearTimeout(timer.current);
      timer.current = null;
    }
  };

  const hide = () => {
    clearTimer();
    shownRef.current = null;
    setShown(null);
  };

  // Cleared when the setting turns it off, and on unmount.
  useEffect(() => {
    if (!enabled) hide();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);
  useEffect(() => clearTimer, []);

  const follow = (url: string | null) => (e: MouseEvent) => {
    if (!enabled || !url || !window.matchMedia("(hover: hover)").matches) return;
    if (shownRef.current === url) return;
    // Another card: the old picture goes at once, and the new one waits for the mouse to rest (every move restarts it).
    clearTimer();
    shownRef.current = null;
    setShown(null);
    timer.current = window.setTimeout(() => {
      timer.current = null;
      shownRef.current = url;
      setShown(url);
    }, REST_MS);
  };

  const picture = shown ? (
    // eslint-disable-next-line @next/next/no-img-element
    <img className="pt-preview" src={shown} alt="" />
  ) : null;

  return { follow, hide, picture };
}
