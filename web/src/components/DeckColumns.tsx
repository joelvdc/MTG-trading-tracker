"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { splitColumns } from "@/lib/decklayout";

const GAP = 18;

/**
 * The deck's sections in fixed columns (as many as fit, up to [max], each at least [minWidth] wide), filled top to
 * bottom and then left to right. Each section's place comes from its full size ([weight]), so folding or unfolding
 * one never moves the others to another column.
 */
export function DeckColumns({ items, max, minWidth }: { items: { key: string; weight: number; node: ReactNode }[]; max: number; minWidth: number }) {
  const box = useRef<HTMLDivElement>(null);
  const [n, setN] = useState(1);
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const measure = () => setN(Math.max(1, Math.min(max, Math.floor((el.clientWidth + GAP) / (minWidth + GAP)))));
    measure();
    const watch = new ResizeObserver(measure);
    watch.observe(el);
    return () => watch.disconnect();
  }, [max, minWidth]);
  const columns = splitColumns(items, n);
  return (
    <div ref={box} className="deck-cols" style={{ gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))` }}>
      {columns.map((col, i) => (
        <div key={i} className="deck-col">
          {col.map((x) => (
            <div key={x.key}>{x.node}</div>
          ))}
        </div>
      ))}
    </div>
  );
}
