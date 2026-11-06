"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/components/api";
import type { ValuePoint } from "@/lib/accounts";

type Range = "1m" | "3m" | "1y" | "all";
const RANGES: { key: Range; label: string; days: number | null }[] = [
  { key: "1m", label: "1 month", days: 31 },
  { key: "3m", label: "3 months", days: 92 },
  { key: "1y", label: "1 year", days: 366 },
  { key: "all", label: "All", days: null },
];

const H = 220;
const PAD = { l: 8, r: 8, t: 12, b: 24 };

const dayLabel = (d: string) => new Date(`${d}T12:00:00Z`).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });

/**
 * Stats → Value over time: the collection's (or one binder's) value per day, from the website's nightly job and the
 * phone's history (the app's value screen). [binder]: "" whole collection, "~" Unsorted, else a binder uid.
 */
export function ValueChart({ binder, money }: { binder: string; money: (eur: number) => string }) {
  const [points, setPoints] = useState<ValuePoint[] | null>(null);
  const [available, setAvailable] = useState(true);
  const [range, setRange] = useState<Range>("3m");
  const [hover, setHover] = useState<number | null>(null);
  // The drawing is as wide as its box (measured), so it keeps its height and text size on any screen.
  const box = useRef<HTMLDivElement>(null);
  const [W, setW] = useState(640);
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setW(Math.max(280, Math.round(e.contentRect.width))));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    setPoints(null);
    (async () => {
      const r = await api<{ points?: ValuePoint[]; available?: boolean }>(`/api/collection/history${binder ? `?binder=${encodeURIComponent(binder)}` : ""}`);
      setAvailable(r.data.available !== false);
      setPoints(Array.isArray(r.data.points) ? r.data.points : []);
    })();
  }, [binder]);

  const shown = useMemo(() => {
    if (!points) return [];
    const days = RANGES.find((x) => x.key === range)!.days;
    if (days === null || points.length === 0) return points;
    const from = new Date(Date.parse(`${points[points.length - 1].day}T00:00:00Z`) - days * 86_400_000).toISOString().slice(0, 10);
    return points.filter((p) => p.day >= from);
  }, [points, range]);

  if (!available) return null;

  const body = () => {
    if (!points) return <p className="muted small">Loading…</p>;
    if (shown.length === 0)
      return <p className="muted small">No days saved yet. The website saves the value every night; the app sends the phone&apos;s history once after logging in with this account.</p>;
    const min = Math.min(...shown.map((p) => p.value));
    const max = Math.max(...shown.map((p) => p.value));
    const lo = min - (max - min) * 0.1 || min * 0.9;
    const hi = max + (max - min) * 0.1 || max * 1.1 || 1;
    const x = (i: number) => PAD.l + (shown.length === 1 ? (W - PAD.l - PAD.r) / 2 : (i / (shown.length - 1)) * (W - PAD.l - PAD.r));
    const y = (v: number) => PAD.t + (1 - (v - lo) / (hi - lo)) * (H - PAD.t - PAD.b);
    const line = shown.map((p, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(" ");
    const area = `${line} L${x(shown.length - 1).toFixed(1)},${H - PAD.b} L${x(0).toFixed(1)},${H - PAD.b} Z`;
    const first = shown[0];
    const last = shown[shown.length - 1];
    const change = last.value - first.value;
    const h = hover !== null ? shown[hover] : null;
    const onMove = (e: React.PointerEvent<SVGSVGElement>) => {
      const box = e.currentTarget.getBoundingClientRect();
      const px = ((e.clientX - box.left) / box.width) * W;
      const i = shown.length === 1 ? 0 : Math.round(((px - PAD.l) / (W - PAD.l - PAD.r)) * (shown.length - 1));
      setHover(Math.min(shown.length - 1, Math.max(0, i)));
    };
    return (
      <>
        <div className="value-head">
          <span className="big">{money(h ? h.value : last.value)}</span>
          <span className="muted small">
            {h ? `${dayLabel(h.day)} · ${h.cards} cards` : `${dayLabel(last.day)} · ${last.cards} cards`}
          </span>
          {!h && shown.length > 1 && (
            <span className={change >= 0 ? "up small" : "down small"}>
              {change >= 0 ? "▲" : "▼"} {money(Math.abs(change))}
              {first.value > 0 ? ` (${((change / first.value) * 100).toFixed(1)}%)` : ""} since {dayLabel(first.day)}
            </span>
          )}
        </div>
        <svg
          className="value-chart"
          viewBox={`0 0 ${W} ${H}`}
          role="img"
          aria-label={`Value from ${money(first.value)} on ${dayLabel(first.day)} to ${money(last.value)} on ${dayLabel(last.day)}`}
          onPointerMove={onMove}
          onPointerLeave={() => setHover(null)}
        >
          <line x1={PAD.l} x2={W - PAD.r} y1={H - PAD.b} y2={H - PAD.b} className="axis" />
          <path d={area} className="area" />
          <path d={line} className="line" />
          {shown.length === 1 && <circle cx={x(0)} cy={y(first.value)} r={4} className="dot" />}
          {h && hover !== null && (
            <>
              <line x1={x(hover)} x2={x(hover)} y1={PAD.t} y2={H - PAD.b} className="cross" />
              <circle cx={x(hover)} cy={y(h.value)} r={4} className="dot" />
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
    <section className="stat-block value-block">
      <div className="value-title">
        <h2>Value over time</h2>
        <div className="seg text" role="group" aria-label="Period">
          {RANGES.map((r) => (
            <button key={r.key} className={range === r.key ? "on" : undefined} aria-pressed={range === r.key} onClick={() => setRange(r.key)}>
              {r.label}
            </button>
          ))}
        </div>
      </div>
      <div ref={box}>{body()}</div>
    </section>
  );
}
