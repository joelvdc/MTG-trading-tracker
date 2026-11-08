"use client";

import { useEffect, useRef } from "react";

/** A centered modal that closes with Esc or a click on the dark background. With [full], it takes (nearly) the whole screen, like Archidekt's and Moxfield's card views. */
export function Dialog({ title, onClose, children, full = false }: { title: string; onClose: () => void; children: React.ReactNode; full?: boolean }) {
  const box = useRef<HTMLDivElement>(null);
  const close = useRef(onClose);
  close.current = onClose;

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    box.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      // With dialogs stacked (a card page over the recommendations window), only the top one closes.
      const backdrops = document.querySelectorAll(".backdrop");
      if (backdrops.length > 0 && backdrops[backdrops.length - 1] !== box.current?.closest(".backdrop")) return;
      close.current();
    };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      previous?.focus?.();
    };
  }, []);

  return (
    <div className="backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={full ? "dialog full" : "dialog"} role="dialog" aria-modal="true" aria-label={title} ref={box} tabIndex={-1}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}
