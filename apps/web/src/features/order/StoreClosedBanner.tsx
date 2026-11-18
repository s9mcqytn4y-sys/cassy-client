"use client";

import React from "react";
import { Clock, PauseCircle, Store } from "lucide-react";
import { useLocale } from "@/components/Providers";
import type { Messages } from "@/lib/messages";

const DAY_KEYS = ["sun", "mon", "tue", "wed", "thu", "fri", "sat"] as const;
const DAY_ALIASES: Record<string, string[]> = {
  sun: ["sun", "sunday", "minggu"],
  mon: ["mon", "monday", "senin"],
  tue: ["tue", "tuesday", "selasa"],
  wed: ["wed", "wednesday", "rabu"],
  thu: ["thu", "thursday", "kamis"],
  fri: ["fri", "friday", "jumat"],
  sat: ["sat", "saturday", "sabtu"]
};
const DAY_LABELS_ID: Record<string, string> = {
  mon: "Senin",
  tue: "Selasa",
  wed: "Rabu",
  thu: "Kamis",
  fri: "Jumat",
  sat: "Sabtu",
  sun: "Minggu"
};
const DAY_LABELS_EN: Record<string, string> = {
  mon: "Mon",
  tue: "Tue",
  wed: "Wed",
  thu: "Thu",
  fri: "Fri",
  sat: "Sat",
  sun: "Sun"
};

function formatDayValue(val: unknown, t: Messages): string | null {
  if (typeof val === "string") return val.trim() || null;
  if (!val || typeof val !== "object") return null;
  const obj = val as Record<string, unknown>;
  if (obj.closed === true) return t.closedDayLabel;
  const open = typeof obj.open === "string" ? obj.open : "";
  const close = typeof obj.close === "string" ? obj.close : "";
  if (open && close) return `${open} - ${close}`;
  return null;
}

function findDayEntry(hours: Record<string, unknown>, dayKey: string): unknown {
  const lower = Object.fromEntries(Object.entries(hours).map(([k, v]) => [k.toLowerCase(), v]));
  for (const alias of DAY_ALIASES[dayKey] ?? [dayKey]) {
    if (lower[alias] !== undefined) return lower[alias];
  }
  return undefined;
}

function closedCopy(reason: string | undefined, t: Messages): { title: string; body: string } {
  if (reason === "paused") return { title: t.closedPausedTitle, body: t.closedPausedBody };
  if (reason === "table_inactive") return { title: t.closedInactiveTitle, body: t.closedInactiveBody };
  return { title: t.closedTitle, body: t.closedBody };
}

/**
 * Informative closed-state banner explaining why checkout is disabled and showing operating hours.
 */
export function StoreClosedBanner({
  closedReason,
  operatingHours
}: {
  closedReason?: string;
  operatingHours?: Record<string, unknown>;
}) {
  const { t, locale } = useLocale();
  const { title, body } = closedCopy(closedReason, t);
  const Icon = closedReason === "paused" ? PauseCircle : Store;

  const todayKey = DAY_KEYS[new Date().getDay()];
  const hoursObj = operatingHours && typeof operatingHours === "object" ? operatingHours : undefined;
  const todayFormatted = hoursObj ? formatDayValue(findDayEntry(hoursObj, todayKey) ?? hoursObj, t) : null;

  const dayRows = hoursObj
    ? (["mon", "tue", "wed", "thu", "fri", "sat", "sun"] as const)
        .map((day) => {
          const val = formatDayValue(findDayEntry(hoursObj, day), t);
          if (!val) return null;
          const label = locale === "en-US" ? DAY_LABELS_EN[day] : DAY_LABELS_ID[day];
          return { day, label, val };
        })
        .filter((row): row is { day: typeof DAY_KEYS[number]; label: string; val: string } => row !== null)
    : [];

  return (
    <section
      role="status"
      aria-live="polite"
      className="rounded-2xl border border-[var(--state-warn-fg)]/25 bg-[var(--state-warn-bg)] p-4 text-ink"
    >
      <div className="flex items-start gap-3">
        <span className="mt-0.5 grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-surface text-[var(--state-warn-fg)]">
          <Icon size={20} aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-base font-bold text-ink">{title}</h2>
          <p className="mt-1 text-sm leading-relaxed text-subink">{body}</p>

          {todayFormatted ? (
            <p className="mt-2.5 inline-flex items-center gap-1.5 rounded-full bg-surface px-3 py-1 text-xs font-bold text-ink">
              <Clock size={13} aria-hidden="true" className="text-[var(--state-warn-fg)]" />
              <span>{t.todayHoursLabel}: {todayFormatted}</span>
            </p>
          ) : null}

          {dayRows.length > 0 ? (
            <details className="mt-2.5 rounded-xl bg-surface/85 px-3 py-2 text-xs">
              <summary className="cursor-pointer font-semibold text-ink">{t.operatingHoursLabel}</summary>
              <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-subink">
                {dayRows.map((row) => (
                  <React.Fragment key={row.day}>
                    <dt className={row.day === todayKey ? "font-bold text-ink" : ""}>{row.label}</dt>
                    <dd className={`text-right tabular-nums ${row.day === todayKey ? "font-bold text-ink" : ""}`}>{row.val}</dd>
                  </React.Fragment>
                ))}
              </dl>
            </details>
          ) : null}
        </div>
      </div>
    </section>
  );
}
