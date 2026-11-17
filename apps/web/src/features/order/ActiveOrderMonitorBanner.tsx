"use client";

import { useEffect, useState } from "react";
import { ArrowRight, Clock, RefreshCw } from "lucide-react";
import { useLocale } from "@/components/Providers";
import { formatIdr } from "@/lib/format";
import { orderPhase } from "./status";

function formatCountdown(ms: number): string {
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${String(min).padStart(2, "0")}:${String(sec).padStart(2, "0")}`;
}

/**
 * Sticky monitor shown above the menu whenever the guest returns from checkout while an order
 * is still pending payment, paid in progress, expired, cancelled, or refunded.
 */
export function ActiveOrderMonitorBanner({
  orderNumber,
  amount,
  expiresAt,
  status,
  paymentStatus,
  busy,
  onOpenPayment,
  onRenewQr
}: {
  orderNumber?: string;
  amount?: number;
  expiresAt?: string;
  status?: string;
  paymentStatus?: string;
  busy?: boolean;
  onOpenPayment: () => void;
  onRenewQr?: () => void;
}) {
  const { t, locale } = useLocale();
  const [now, setNow] = useState(() => Date.now());

  const phase = orderPhase({ status, payment_status: paymentStatus });
  const deadlineMs = expiresAt ? Date.parse(expiresAt) : Number.NaN;
  const hasDeadline = Number.isFinite(deadlineMs);
  const remainingMs = hasDeadline ? deadlineMs - now : Number.POSITIVE_INFINITY;
  const isExpired = paymentStatus === "expired" || (phase === "awaiting" && hasDeadline && remainingMs <= 0);
  const isExpiringSoon = phase === "awaiting" && !isExpired && hasDeadline && remainingMs <= 60_000;

  useEffect(() => {
    if (phase !== "awaiting" || !hasDeadline) return;
    setNow(Date.now());
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [phase, hasDeadline]);

  let toneClasses = "border-[var(--border-strong)] bg-surface text-ink";
  let badgeClasses = "bg-[var(--state-warn-bg)] text-[var(--state-warn-fg)]";
  const deadlineTime = expiresAt
    ? new Date(expiresAt).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" })
    : "";
  let statusLine = hasDeadline ? `${t.payBefore} ${deadlineTime}` : t.waiting;

  if (phase === "paid") {
    toneClasses = "border-[var(--state-ok-fg)]/35 bg-[var(--state-ok-bg)] text-[var(--state-ok-fg)]";
    badgeClasses = "bg-white/80 text-[var(--state-ok-fg)]";
    statusLine = t.activeOrderPaid;
  } else if (isExpired || phase === "cancelled" || phase === "refunded") {
    toneClasses = "border-[var(--state-err-fg)]/35 bg-[var(--state-err-bg)] text-[var(--state-err-fg)]";
    badgeClasses = "bg-white/80 text-[var(--state-err-fg)]";
    statusLine = isExpired
      ? t.activeOrderExpired
      : phase === "cancelled"
        ? t.activeOrderCancelled
        : t.activeOrderRefunded;
  } else if (isExpiringSoon) {
    toneClasses = "border-[var(--state-warn-fg)]/50 bg-[var(--state-warn-bg)] text-[var(--state-warn-fg)]";
  }

  return (
    <div className="px-4 pt-3">
      <section
        aria-label={t.activeOrderTitle}
        aria-live="polite"
        className={`rounded-2xl border p-3.5 shadow-sm motion-safe:transition-colors ${toneClasses}`}
      >
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-bold ${badgeClasses}`}>
                <Clock size={12} aria-hidden="true" />
                {statusLine}
              </span>
              {phase === "awaiting" && !isExpired && hasDeadline ? (
                <span className="text-xs font-bold tabular-nums opacity-90">
                  {t.payBeforeCountdown}: {formatCountdown(remainingMs)}
                </span>
              ) : null}
            </div>
            <p className="mt-1.5 text-sm font-bold text-ink">
              {t.activeOrderTitle}
              {orderNumber ? ` #${orderNumber}` : ""}
              {typeof amount === "number" ? ` | ${formatIdr(amount)}` : ""}
            </p>
          </div>
          {isExpired && onRenewQr ? (
            <button
              type="button"
              disabled={busy}
              onClick={onRenewQr}
              className="btn-primary inline-flex min-h-[44px] items-center gap-1.5 rounded-xl px-4 text-sm font-bold"
            >
              <RefreshCw size={15} aria-hidden="true" />
              {t.newQr}
            </button>
          ) : (
            <button
              type="button"
              disabled={busy}
              onClick={onOpenPayment}
              className="btn-primary inline-flex min-h-[44px] items-center gap-1.5 rounded-xl px-4 text-sm font-bold"
            >
              <span>{t.resumePayment}</span>
              <ArrowRight size={15} aria-hidden="true" />
            </button>
          )}
        </div>
      </section>
    </div>
  );
}
