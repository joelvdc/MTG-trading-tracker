"use client";

import { useEffect } from "react";

/** How close to the window's top or bottom edge (px) the page starts scrolling, and its fastest speed. */
const EDGE = 90;
const FASTEST = 22; // px a step (about 60 steps a second)

/** How far to scroll this frame for a pointer at [y] in a window [height] tall: faster closer to the edge, 0 away from it. */
export function edgeSpeed(y: number, height: number, edge = EDGE, fastest = FASTEST): number {
  const zone = Math.min(edge, height / 4);
  if (y < zone) return -Math.ceil(((zone - Math.max(0, y)) / zone) * fastest);
  if (y > height - zone) return Math.ceil(((Math.min(height, y) - (height - zone)) / zone) * fastest);
  return 0;
}

/**
 * While [dragging] (the browser's drag and drop, which on Windows ignores the mouse wheel and hardly scrolls by itself):
 * the page scrolls when the dragged thing is near the window's top or bottom edge. (Where the browser does scroll with
 * the wheel during a drag, it keeps doing so.) Stops on drop, on Esc (the drag ends) and when [dragging] turns false.
 */
export function useDragScroll(dragging: boolean): void {
  useEffect(() => {
    if (!dragging) return;
    let y: number | null = null;
    // About 60 steps a second.
    const timer = window.setInterval(() => {
      const by = y == null ? 0 : edgeSpeed(y, window.innerHeight);
      if (by) window.scrollBy(0, by);
    }, 16);
    const onOver = (e: DragEvent) => {
      y = e.clientY;
    };
    const stopAt = () => {
      y = null;
    };
    // Dragged out of the window: no more dragover events, so stop rather than keep scrolling.
    const onLeave = (e: DragEvent) => {
      if (e.relatedTarget === null) y = null;
    };
    document.addEventListener("dragover", onOver);
    document.addEventListener("dragleave", onLeave);
    document.addEventListener("drop", stopAt);
    document.addEventListener("dragend", stopAt);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("dragover", onOver);
      document.removeEventListener("dragleave", onLeave);
      document.removeEventListener("drop", stopAt);
      document.removeEventListener("dragend", stopAt);
    };
  }, [dragging]);
}
