"use client";

import { createElement, useEffect, useRef, useState, type ReactNode } from "react";

/** How close to the window's top or bottom edge (px) the page starts scrolling, and its fastest speed. */
const EDGE = 90;
const FASTEST = 22; // px a step (about 60 steps a second)
/** How far the mouse moves with the button down before a press becomes a drag (a shorter press stays a click). */
const START_PX = 6;

/** How far to scroll this step for a pointer at [y] in a window [height] tall: faster closer to the edge, 0 away from it. */
export function edgeSpeed(y: number, height: number, edge = EDGE, fastest = FASTEST): number {
  const zone = Math.min(edge, height / 4);
  if (y < zone) return -Math.ceil(((zone - Math.max(0, y)) / zone) * fastest);
  if (y > height - zone) return Math.ceil(((Math.min(height, y) - (height - zone)) / zone) * fastest);
  return 0;
}

interface DragOptions<P, T> {
  /** A drag of [p] started. */
  begin: (p: P) => void;
  /** Where [p] would land with the pointer at (x, y) in the window (asked again after the page scrolled under it). */
  over: (p: P, x: number, y: number) => T | null;
  /** [p] was let go over [target] (null: nowhere it can land). */
  drop: (p: P, target: T | null) => void;
  /** The drag ended without a drop (Esc, or the browser took the pointer away). */
  cancel: () => void;
}

/**
 * Dragging with the mouse done by the page itself rather than the browser's drag and drop, which on Windows takes the
 * mouse away (no wheel) and hardly scrolls. While dragging, the mouse wheel scrolls the page as usual, the page also
 * scrolls by itself near the window's top or bottom edge, and a small label with the dragged thing's name follows the
 * pointer. A press only becomes a drag after the mouse moved a few pixels, so a click stays a click. Mouse and pen
 * only: on a touch screen a swipe keeps scrolling the page.
 *
 * Returns [press] (for onPointerDown: what is dragged and its label) and the label element to render.
 */
export function usePointerDrag<P, T>(options: DragOptions<P, T>): { press: (p: P, label: string) => (e: React.PointerEvent) => void; ghost: ReactNode; dragging: boolean } {
  const opts = useRef(options);
  opts.current = options;
  const ghostRef = useRef<HTMLDivElement | null>(null);
  const [label, setLabel] = useState<string | null>(null);
  const cleanup = useRef<(() => void) | null>(null);

  useEffect(() => () => cleanup.current?.(), []);

  const press = (p: P, text: string) => (e: React.PointerEvent) => {
    if (e.button !== 0 || e.pointerType === "touch" || cleanup.current) return;
    const startX = e.clientX;
    const startY = e.clientY;
    let x = startX;
    let y = startY;
    let started = false;
    let inside = true;
    let target: T | null = null;
    let timer = 0;

    const place = () => {
      const g = ghostRef.current;
      if (g) g.style.transform = `translate(${x + 14}px, ${y + 10}px)`;
    };
    const aim = () => {
      target = opts.current.over(p, x, y);
    };
    const start = () => {
      started = true;
      window.getSelection()?.removeAllRanges();
      document.body.classList.add("pointer-dragging");
      setLabel(text);
      opts.current.begin(p);
      window.setTimeout(place, 0);
      // Near the top or bottom edge the page scrolls by itself. Whenever the page moved (by itself or with the mouse
      // wheel), what's under the pointer is aimed at again (scroll events alone come late in some browsers).
      let seen = window.scrollY;
      timer = window.setInterval(() => {
        const by = inside ? edgeSpeed(y, window.innerHeight) : 0;
        if (by) window.scrollBy(0, by);
        if (window.scrollY !== seen) {
          seen = window.scrollY;
          aim();
        }
      }, 16);
    };
    const finish = () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
      window.removeEventListener("pointercancel", onCancel);
      window.removeEventListener("keydown", onKey, true);
      window.removeEventListener("scroll", onScroll, true);
      document.documentElement.removeEventListener("pointerleave", onLeave);
      window.clearInterval(timer);
      document.body.classList.remove("pointer-dragging");
      setLabel(null);
      cleanup.current = null;
    };
    const onMove = (ev: PointerEvent) => {
      x = ev.clientX;
      y = ev.clientY;
      inside = true;
      if (!started) {
        if (Math.hypot(x - startX, y - startY) < START_PX) return;
        start();
      }
      ev.preventDefault();
      place();
      aim();
    };
    const onScroll = () => {
      if (started) aim();
    };
    const onUp = () => {
      const was = started;
      const at = target;
      finish();
      if (!was) return;
      // The click that follows the release would open the card under the pointer: swallow it.
      const swallow = (ev: MouseEvent) => {
        ev.stopPropagation();
        ev.preventDefault();
      };
      window.addEventListener("click", swallow, { capture: true, once: true });
      window.setTimeout(() => window.removeEventListener("click", swallow, true), 0);
      opts.current.drop(p, at);
    };
    const onCancel = () => {
      const was = started;
      finish();
      if (was) opts.current.cancel();
    };
    const onKey = (ev: KeyboardEvent) => {
      if (ev.key !== "Escape" || !started) return;
      ev.preventDefault();
      ev.stopPropagation();
      onCancel();
    };
    // Out of the window: no edge scrolling until the pointer comes back (the next move).
    const onLeave = () => {
      inside = false;
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onCancel);
    window.addEventListener("keydown", onKey, true);
    window.addEventListener("scroll", onScroll, true);
    document.documentElement.addEventListener("pointerleave", onLeave);
    cleanup.current = finish;
  };

  const ghost = label === null ? null : createElement("div", { ref: ghostRef, className: "drag-ghost", "aria-hidden": true }, label);
  return { press, ghost, dragging: label !== null };
}
