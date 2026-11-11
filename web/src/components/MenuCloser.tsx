"use client";

import { useEffect } from "react";

/**
 * Every drop-down menu (`<details class="menu">`) closes on a click elsewhere or on Esc, as menus do in the app.
 * Mounted once, in the root layout.
 */
export function MenuCloser() {
  useEffect(() => {
    const openMenus = () => [...document.querySelectorAll<HTMLDetailsElement>("details.menu[open]")];
    const away = (e: Event) => {
      for (const m of openMenus()) if (!m.contains(e.target as Node)) m.open = false;
    };
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") for (const m of openMenus()) m.open = false;
    };
    document.addEventListener("mousedown", away);
    document.addEventListener("touchstart", away, { passive: true });
    document.addEventListener("keydown", key);
    return () => {
      document.removeEventListener("mousedown", away);
      document.removeEventListener("touchstart", away);
      document.removeEventListener("keydown", key);
    };
  }, []);
  return null;
}
