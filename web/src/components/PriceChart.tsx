"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { PricePoint } from "@/lib/pricehistory";

type Range = "1m" | "3m" | "6m" | "1y";
const RANGES: { key: Range; label: string; days: number }[] = [
  { key: "1m", label: "1M", days: 31 },
  { key: "3m", label: "3M", days: 92 },
  { key: "6m", label: "6M", days: 183 },
  { key: "1y", label: "1Y", days: 366 },
];

const H = 140;
const PAD = { l: 6, r: 6, t: 10, b: 22 };

const dayLabel = (d: string) => new Date(`${d}T12:00:00Z`).toLocaleDateString("en-GB", { day: "numeric", month: "short", timeZone: "UTC" });

/** The points from [days] before the newest one. */
export function lastDays(points: PricePoint[], days: number): PricePoint[] {
  if (points.length === 0) return points;
  const from = new Date(Date.parse(`${points[points.length - 1].day}T00:00:00Z`) - days * 86_400_000).toISOString().slice(0, 10);
  return points.filter((p) => p.day >= from);
}

/**
 * The card window's price chart: one copy of this pile (printing, finish, condition) at the owner's price source, as the
 * nightly job saved it (every day for 3 months, then weekly up to a year).
 */
export function PriceChart({ points, money }: { points: PricePoint[]; money: (eur: number) => string }) {
  const [range, setRange] = useState<Range>("3m");
  const [hover, setHover] = useState<number | null>(null);
  const box = useRef<HTMLDivElement>(null);
  const [W, setW] = useState(420);
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setW(Math.max(240, Math.round(e.contentRect.width))));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  const shown = useMemo(() => lastDays(points, RANGES.find((r) => r.key === range)!.days), [points, range]);

  const body = () => {
    if (shown.length < 2)
      return <p className="muted small">Not enough days saved yet. The website saves each card&apos;s price every night, so the chart fills in over the coming days.</p>;
    const vals = shown.map((p) => p.eur);
    const min = Math.min(...vals);
    const max = Math.max(...vals);
    const lo = min - (max - min) * 0.1 || min * 0.9;
    const hi = max + (max - min) * 0.1 || max * 1.1 || 1;
    const x = (i: number) => PAD.l + (i / (shown.length - 1)) * (W - PAD.l - PAD.r);
    const y = (v: number) => PAD.t + (1 - (v - lo) / (hi - lo)) * (H - PAD.t - PAD.b);
    const line = shown.map((p, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(p.eur).toFixed(1)}`).join(" ");
    const area = `${line} L${x(shown.length - 1).toFixed(1)},${H - PAD.b} L${x(0).toFixed(1)},${H - PAD.b} Z`;
    const first = shown[0];
    const last = shown[shown.length - 1];
    const change = last.eur - first.eur;
    const h = hover !== null ? shown[hover] : null;
    return (
      <>
        <div className="value-head">
          <span>{money(h ? h.eur : last.eur)}</span>
          <span className="muted small">{dayLabel(h ? h.day : last.day)}</span>
          {!h && (
            <span className={change > 0 ? "up small" : change < 0 ? "down small" : "muted small"}>
              {change > 0 ? "▲" : change < 0 ? "▼" : ""} {money(Math.abs(change))}
              {first.eur > 0 ? ` (${((change / first.eur) * 100).toFixed(1)}%)` : ""} since {dayLabel(first.day)}
            </span>
          )}
          {!h && (
            <span className="muted small">
              low {money(min)} · high {money(max)}
            </span>
          )}
        </div>
        <svg
          className="value-chart"
          viewBox={`0 0 ${W} ${H}`}
          role="img"
          aria-label={`Price from ${money(first.eur)} on ${dayLabel(first.day)} to ${money(last.eur)} on ${dayLabel(last.day)}`}
          onPointerMove={(e) => {
            const b = e.currentTarget.getBoundingClientRect();
            const px = ((e.clientX - b.left) / b.width) * W;
            setHover(Math.min(shown.length - 1, Math.max(0, Math.round(((px - PAD.l) / (W - PAD.l - PAD.r)) * (shown.length - 1)))));
          }}
          onPointerLeave={() => setHover(null)}
        >
          <line x1={PAD.l} x2={W - PAD.r} y1={H - PAD.b} y2={H - PAD.b} className="axis" />
          <path d={area} className="area" />
          <path d={line} className="line" />
          {h && hover !== null && (
            <>
              <line x1={x(hover)} x2={x(hover)} y1={PAD.t} y2={H - PAD.b} className="cross" />
              <circle cx={x(hover)} cy={y(h.eur)} r={4} className="dot" />
            </>
          )}
          <text x={PAD.l} y={H - 6} className="tick">
            {dayLabel(first.day)}
          </text>
          <text x={W - PAD.r} y={H - 6} className="tick" textAnchor="end">
            {dayLabel(last.day)}
          </text>
        </svg>
      </>
    );
  };

  return (
    <div className="price-chart">
      <div className="value-title">
        <h3 className="mini-head">Price over time</h3>
        <div className="seg text" role="group" aria-label="Period">
          {RANGES.map((r) => (
            <button key={r.key} className={range === r.key ? "on" : undefined} aria-pressed={range === r.key} onClick={() => setRange(r.key)}>
              {r.label}
            </button>
          ))}
        </div>
      </div>
      <div ref={box}>{body()}</div>
    </div>
  );
}
