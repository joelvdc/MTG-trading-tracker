"use client";

import { useEffect, useState } from "react";
import { DEFAULT_COUNTERS, parseCounters } from "@/lib/playtestcounters";

/** Where the browser remembers the quick counter list. */
const KEY = "mtg.playtest.counters";
/** Tells the other parts of the page (the menu, the dialogs) that the list changed. */
const EVENT = "mtg-playtest-counters";

function read(): string[] {
  try {
    return parseCounters(localStorage.getItem(KEY));
  } catch {
    return DEFAULT_COUNTERS.slice();
  }
}

/** The quick counter list and a way to change it; every part of the page using it stays in step. */
export function useQuickCounters(): [string[], (list: string[]) => void] {
  const [list, setList] = useState<string[]>(DEFAULT_COUNTERS);
  useEffect(() => {
    setList(read());
    const changed = () => setList(read());
    window.addEventListener(EVENT, changed);
    return () => window.removeEventListener(EVENT, changed);
  }, []);
  const save = (next: string[]) => {
    setList(next);
    try {
      localStorage.setItem(KEY, JSON.stringify(next));
    } catch {
      // Not remembered after this visit; fine.
    }
    window.dispatchEvent(new Event(EVENT));
  };
  return [list, save];
}
