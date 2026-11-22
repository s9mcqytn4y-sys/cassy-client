"use client";

import { RefreshCw } from "lucide-react";
import { useLocale } from "@/components/Providers";
import { Sheet } from "@/components/Sheet";
import { formatIdr } from "@/lib/format";
import type { Messages } from "@/lib/messages";
import type { GuestOrderSummary } from "./types";

function statusMeta(order: GuestOrderSummary, t: Messages): { label: string; tone: string } {
  if (order.status === "cancelled" || order.payment_status === "expired" || order.payment_status === "failed") {
    return { label: t.orderStatusCancelled, tone: "bg-[var(--state-err-bg)] text-[var(--state-err-fg)]" };
  }
  if (order.status === "served" || order.status === "completed") {
    return { label: t.orderStatusServed, tone: "bg-[var(--state-ok-bg)] text-[var(--state-ok-fg)]" };
  }
  if (order.status === "ready") {
    return { label: t.orderStatusReady, tone: "bg-[var(--state-ok-bg)] text-[var(--state-ok-fg)]" };
  }
  if (order.status === "preparing") {
    return { label: t.orderStatusPreparing, tone: "bg-[var(--state-info-bg)] text-[var(--state-info-fg)]" };
  }
  if (order.status === "queued" || order.payment_status === "paid") {
    return { label: t.orderStatusQueued, tone: "bg-[var(--state-info-bg)] text-[var(--state-info-fg)]" };
  }
  return { label: t.orderStatusPending, tone: "bg-[var(--state-warn-bg)] text-[var(--state-warn-fg)]" };
}

function formatTime(iso: string, locale: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat(locale, { hour: "2-digit", minute: "2-digit" }).format(date);
}

/** Shows the guest's session orders from GET /api/v1/public/guest/orders with quick navigation to the active order. */
export function GuestOrdersSheet({
  orders,
  activeOrderId,
  loading,
  onRefresh,
  onOpenActive,
  onClose
}: {
  orders: GuestOrderSummary[];
  activeOrderId?: string;
  loading: boolean;
  onRefresh: () => void;
  onOpenActive: () => void;
  onClose: () => void;
}) {
  const { t, locale } = useLocale();

  return (
    <Sheet title={t.ordersTitle} onClose={onClose}>
      <div className="mb-4 flex items-center justify-end">
        <button
          type="button"
          onClick={onRefresh}
          disabled={loading}
          className="btn-secondary min-h-[44px] gap-2 rounded-xl px-3.5 text-sm font-semibold disabled:opacity-50"
        >
          <RefreshCw size={15} aria-hidden="true" className={loading ? "animate-spin" : ""} />
          {t.refreshOrders}
        </button>
      </div>

      {orders.length === 0 ? (
        <p className="rounded-2xl border border-subtle bg-muted px-4 py-6 text-center text-sm text-subink">{t.ordersEmpty}</p>
      ) : (
        <ul className="space-y-3">
          {orders.map((order) => {
            const { label, tone } = statusMeta(order, t);
            const time = formatTime(order.created_at, locale);
            const isActive = Boolean(activeOrderId && order.id === activeOrderId);
            return (
              <li key={order.id} className="rounded-2xl border border-subtle bg-surface p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-base font-bold tabular-nums text-ink">{order.order_number}</p>
                    <p className="mt-0.5 text-xs tabular-nums text-subink">
                      {order.queue_number ? `#${order.queue_number}` : ""}
                      {order.queue_number && time ? " · " : ""}
                      {time}
                    </p>
                  </div>
                  <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${tone}`}>{label}</span>
                </div>

                <div className="mt-3 flex items-center justify-between gap-3 border-t border-subtle pt-3">
                  <span className="text-base font-bold tabular-nums text-ink">{formatIdr(order.total_amount)}</span>
                  {isActive ? (
                    <button
                      type="button"
                      onClick={() => {
                        onClose();
                        onOpenActive();
                      }}
                      className="btn-primary min-h-[44px] rounded-xl px-4 text-sm font-bold"
                    >
                      {t.openActiveOrder}
                    </button>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </Sheet>
  );
}
