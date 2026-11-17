"use client";

import { useEffect, useRef, useState } from "react";

/** How long (ms) the mouse rests on a card before its big picture shows. */
const REST_MS = 150;

/**
 * The big picture of the card the mouse rests on, for the playing field's top-right corner (like Moxfield): [follow] for
 * a card's mouse moves, [hide] when it leaves or is pressed, [picture] to render. Always on for a real mouse, as on
 * Moxfield: it doesn't follow the deck page's "card on hover" setting, and it doesn't ask the browser whether there is a
 * mouse, because some Windows laptops with a touchscreen answer "no" while a mouse is in use. A finger's taps (which
 * also send mouse moves) show nothing.
 */
export function useCornerPreview() {
  const [shown, setShown] = useState<string | null>(null);
  /** The kind of pointer that moved or pressed last ("mouse", "pen" or "touch"). */
  const pointerType = useRef<string>("mouse");
  const shownRef = useRef<string | null>(null);
  /** The picture a running timer will show, so its own mouse moves don't restart the wait. */
  const pendingRef = useRef<string | null>(null);
  const timer = useRef<number | null>(null);

  const clearTimer = () => {
    if (timer.current !== null) {
      window.clearTimeout(timer.current);
      timer.current = null;
    }
  };

  const hide = () => {
    clearTimer();
    pendingRef.current = null;
    shownRef.current = null;
    setShown(null);
  };

  // Pointer events come before the mouse events a touch imitates, so this always knows what the next mouse move is from.
  useEffect(() => {
    const note = (e: PointerEvent) => {
      pointerType.current = e.pointerType;
      if (e.pointerType === "touch") hide();
    };
    window.addEventListener("pointerdown", note, true);
    window.addEventListener("pointermove", note, true);
    return () => {
      window.removeEventListener("pointerdown", note, true);
      window.removeEventListener("pointermove", note, true);
      clearTimer();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const follow = (url: string | null) => () => {
    if (!url || pointerType.current === "touch") return;
    // This card's picture is already up, or already on its way: its own mouse moves change nothing (like Moxfield).
    if (shownRef.current === url || pendingRef.current === url) return;
    // Another card: the old picture goes at once, and the new one waits for the mouse to rest. Moving about on the same
    // card leaves the timer alone, so it does show.
    clearTimer();
    pendingRef.current = url;
    shownRef.current = null;
    setShown(null);
    timer.current = window.setTimeout(() => {
      timer.current = null;
      pendingRef.current = null;
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
