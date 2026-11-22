"use client";

import type { ReactNode } from "react";
import { Globe, Megaphone, ReceiptText } from "lucide-react";
import { CassyMonogram } from "@/components/CassyLogo";
import { useLocale } from "@/components/Providers";

/** Outlet name leads (PRD v4 Q2); elevated card header keeps identity, table pill, and actions unified. */
export function StoreHeader({
  storeName,
  tableLabel,
  tagline,
  announcement,
  orderCount = 0,
  onOpenOrders,
  actionSlot
}: {
  storeName: string;
  tableLabel: string;
  tagline?: string;
  announcement?: string;
  orderCount?: number;
  onOpenOrders?: () => void;
  actionSlot?: ReactNode;
}) {
  const { t, locale, toggleLocale } = useLocale();
  return (
    <header className="pl-[max(1.25rem,env(safe-area-inset-left))] pr-[max(1.25rem,env(safe-area-inset-right))] pt-[max(1.25rem,env(safe-area-inset-top))]">
      <div className="rounded-2xl border border-subtle bg-surface p-4 shadow-xs sm:p-5">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5">
            <CassyMonogram size={36} className="shrink-0" />
            <p className="rounded-full bg-brand px-3.5 py-1.5 text-xs font-bold text-brand-fg">
              {tableLabel}
            </p>
          </div>
          <button
            type="button"
            onClick={toggleLocale}
            aria-label={locale === "en-US" ? "Switch language to Indonesian" : "Ganti bahasa ke Inggris"}
            className="inline-flex min-h-[44px] shrink-0 cursor-pointer items-center gap-1.5 rounded-full border border-subtle bg-canvas px-3 text-xs font-semibold text-ink transition hover:border-brand"
          >
            <Globe size={14} aria-hidden="true" />
            <span>{locale === "en-US" ? "EN | ID" : "ID | EN"}</span>
          </button>
        </div>

        <div className="mt-3 min-w-0">
          <h1 className="text-2xl font-extrabold leading-tight tracking-tight text-ink [text-wrap:balance]">
            {storeName}
          </h1>
          {tagline ? (
            <p className="mt-1 line-clamp-2 text-sm leading-snug text-subink [text-wrap:pretty]">{tagline}</p>
          ) : null}
        </div>

        {announcement ? (
          <p className="mt-3.5 flex items-start gap-2.5 rounded-xl border border-subtle bg-muted/70 px-3.5 py-2.5 text-sm leading-snug text-ink">
            <Megaphone size={18} aria-hidden="true" className="mt-0.5 shrink-0 text-subink" />
            <span className="line-clamp-2">{announcement}</span>
          </p>
        ) : null}

        {actionSlot || (orderCount > 0 && onOpenOrders) ? (
          <div className="mt-3.5 flex flex-wrap items-center gap-2 border-t border-subtle pt-3">
            {actionSlot}
            {orderCount > 0 && onOpenOrders ? (
              <button
                type="button"
                onClick={onOpenOrders}
                className="btn-secondary inline-flex min-h-[44px] items-center gap-2 rounded-full px-4 text-sm font-semibold"
              >
                <ReceiptText size={17} aria-hidden="true" />
                <span>
                  {t.myOrders} ({orderCount})
                </span>
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
    </header>
  );
}
