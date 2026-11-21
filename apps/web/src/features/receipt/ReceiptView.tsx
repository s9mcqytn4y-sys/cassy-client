"use client";

import { useCallback, useEffect, useState } from "react";
import { Download, Printer } from "lucide-react";
import { useLocale } from "@/components/Providers";
import { ApiError, apiFetch, isMaintenance } from "@/lib/api";
import { formatIdr } from "@/lib/format";
import type { Messages } from "@/lib/messages";
import { isFinal, orderPhase, progressStep } from "@/features/order/status";

interface Receipt {
  store: { name: string; address?: string };
  order_number: string;
  status: string;
  payment_status: string;
  issued_at: string;
  lines: { name: string; variant?: string; options?: string[]; quantity: number; amount: number }[];
  subtotal_amount: number;
  discount_amount: number;
  service_charge_amount: number;
  tax_amount: number;
  total_amount: number;
  payments: { method: string }[];
}

// Receipts also come from POS orders, so the labels cover more than the guest methods.
const METHOD: Record<string, string> = {
  qris: "QRIS", static_qris: "QRIS", gopay: "GoPay", shopeepay: "ShopeePay", cash: "Tunai", edc: "EDC", bank_transfer: "Transfer"
};
const REFRESH_MS = 20_000;

// The receipt does not know the outlet's service mode, so "ready" gets a label true for both modes.
function statusText(t: Messages, r: Receipt): string {
  const phase = orderPhase(r);
  if (phase === "cancelled") return t.cancelledTitle;
  if (phase === "refunded") return t.refundedTitle;
  return [t.statusReceived, t.statusPreparing, t.statusReadyShort, t.statusServed][progressStep(r.status)];
}

function Row({ label, value, strong }: { label: string; value: number; strong?: boolean }) {
  return (
    <div className={`flex justify-between py-1 ${strong ? "text-base font-bold" : "text-sm"}`}>
      <span className={strong ? undefined : "text-subink"}>{label}</span>
      <span className="tabular-nums">{formatIdr(value)}</span>
    </div>
  );
}

export function ReceiptView({ token, mode = "token" }: { token: string; mode?: "token" | "short" }) {
  const { t, locale } = useLocale();
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "maintenance" | "failed">("loading");
  const apiBasePath =
    mode === "short"
      ? `/api/v1/public/receipts/short/${encodeURIComponent(token)}`
      : `/api/v1/public/receipts/${encodeURIComponent(token)}`;

  const load = useCallback(async () => {
    try {
      setReceipt(await apiFetch<Receipt>(apiBasePath));
      setState("ready");
    } catch (e) {
      setState(e instanceof ApiError && e.status === 404 ? "missing" : isMaintenance(e) ? "maintenance" : "failed");
    }
  }, [apiBasePath]);

  useEffect(() => {
    void load();
  }, [load]);

  // Follow the order until it is done, only while the guest is looking; no push, no email.
  const done = receipt ? isFinal(receipt) : true;
  useEffect(() => {
    if (done) return;
    const id = setInterval(() => {
      if (!document.hidden) void load();
    }, REFRESH_MS);
    return () => clearInterval(id);
  }, [done, load]);

  if (state === "loading") {
    return <p className="mx-auto max-w-md px-5 py-10 text-subink">{t.receiptLoading}</p>;
  }
  if (state !== "ready" || !receipt) {
    const [title, body] =
      state === "missing"
        ? [t.receiptMissingTitle, t.receiptMissingBody]
        : state === "maintenance"
          ? [t.maintenanceTitle, t.maintenanceBody]
          : [t.loadFailedTitle, t.loadFailedBody];
    return (
      <section className="mx-auto max-w-md px-5 py-10">
        <h1 className="text-2xl font-bold">{title}</h1>
        <p className="mt-2 text-base">{body}</p>
        {state !== "missing" ? (
          <button type="button" onClick={() => void load()} className="btn-primary mt-6 min-h-[48px] px-6 font-bold">
            {t.retry}
          </button>
        ) : null}
      </section>
    );
  }

  const methods = [...new Set(receipt.payments.map((p) => METHOD[p.method] ?? p.method))];
  const isPaid = receipt.payment_status?.toLowerCase() === "paid";
  return (
    <section className="mx-auto max-w-md px-5 py-8">
      <div className="rounded-3xl border border-subtle bg-surface p-6">
        <div className="flex items-start justify-between gap-3 border-b border-subtle pb-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-subink">{t.receiptTitle}</p>
            <h1 className="mt-0.5 text-2xl font-bold text-ink">{receipt.store.name}</h1>
            {receipt.store.address ? (
              <p className="mt-1 text-xs leading-relaxed text-subink">{receipt.store.address}</p>
            ) : null}
          </div>
          <span
            className={`inline-flex shrink-0 items-center rounded-full px-3 py-1 text-xs font-bold ${
              isPaid
                ? "bg-[var(--state-ok-bg)] text-[var(--state-ok-fg)]"
                : "bg-[var(--state-warn-bg)] text-[var(--state-warn-fg)]"
            }`}
          >
            {isPaid ? "LUNAS" : receipt.payment_status.toUpperCase()}
          </span>
        </div>

        <div className="mt-4 flex flex-wrap items-baseline justify-between gap-2">
          <p className="text-sm text-subink">
            {t.orderNumber}
            <span className="block text-3xl font-extrabold tabular-nums text-ink">{receipt.order_number}</span>
          </p>
          <div className="text-right">
            <p aria-live="polite" className="text-sm font-semibold text-ink">
              {t.statusLabel}: <strong>{statusText(t, receipt)}</strong>
            </p>
            <p className="mt-0.5 text-xs tabular-nums text-subink">
              {new Date(receipt.issued_at).toLocaleString(locale, { dateStyle: "medium", timeStyle: "short" })}
            </p>
          </div>
        </div>

        <ul className="mt-5 border-t border-subtle">
        {receipt.lines.map((line, i) => {
          const details = [line.variant, ...(line.options ?? [])].filter(Boolean).join(", ");
          return (
            <li key={i} className="flex justify-between gap-3 border-b border-subtle py-3 text-sm">
              <span>
                <span className="font-semibold">
                  {line.quantity} x {line.name}
                </span>
                {details ? <span className="block text-xs text-subink">{details}</span> : null}
              </span>
              <span className="shrink-0 tabular-nums">{formatIdr(line.amount)}</span>
            </li>
          );
        })}
      </ul>

      <div className="mt-3">
        <Row label={t.subtotal} value={receipt.subtotal_amount} />
        {receipt.discount_amount > 0 ? <Row label={t.discount} value={-receipt.discount_amount} /> : null}
        {receipt.service_charge_amount > 0 ? <Row label={t.service} value={receipt.service_charge_amount} /> : null}
        {receipt.tax_amount > 0 ? <Row label={t.tax} value={receipt.tax_amount} /> : null}
        <div className="mt-2 border-t border-subtle pt-2">
          <Row label={t.total} value={receipt.total_amount} strong />
        </div>
      </div>
      {methods.length > 0 ? (
        <p className="mt-3 text-sm">
          {t.paidWith}: <strong>{methods.join(", ")}</strong>
        </p>
      ) : null}

      <div className="mt-6 flex flex-col gap-3 sm:flex-row print:hidden">
        <a
          href={`${apiBasePath}/pdf`}
          download={`Struk-${receipt.order_number}.pdf`}
          className="btn-primary inline-flex min-h-[48px] flex-1 items-center justify-center gap-2 rounded-xl px-5 py-3 text-sm font-bold"
        >
          <Download className="h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{t.downloadReceiptPdf}</span>
        </a>
        <button
          type="button"
          onClick={() => window.print()}
          className="inline-flex min-h-[48px] items-center justify-center gap-2 rounded-xl border border-subtle bg-surface px-5 py-3 text-sm font-bold text-ink transition hover:bg-canvas"
        >
          <Printer className="h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{t.printReceipt}</span>
        </button>
      </div>
      </div>
    </section>
  );
}
