"use client";

import React, { useEffect, useRef, useState } from "react";
import { CircleCheck } from "lucide-react";
import { useLocale } from "@/components/Providers";
import { StateIllustration } from "@/components/StateIllustration";
import { apiFetch } from "@/lib/api";
import type { Messages } from "@/lib/messages";
import { isValidEmail, normalizeSubmissionText, stripControlChars } from "@/lib/sanitize";
import { progressStep } from "./status";
import type { ServiceMode } from "./types";

function stepCopy(t: Messages, mode: ServiceMode) {
  return mode === "table"
    ? { status: [t.statusReceived, t.statusPreparing, t.statusOnTheWay, t.statusDelivered], line: 4 }
    : { status: [t.statusReceived, t.statusPreparing, t.statusReady, t.statusServed], line: 3 };
}

function ProgressSteps({ status, mode }: { status?: string; mode: ServiceMode }) {
  const { t, locale } = useLocale();
  const step = progressStep(status);
  const copy = stepCopy(t, mode);
  const labels = copy.status;
  const [reachedAt, setReachedAt] = useState<Record<number, string>>({});
  const firstStep = useRef(step);
  useEffect(() => {
    if (step === firstStep.current) return;
    const now = new Date().toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" });
    setReachedAt((prev) => (prev[step] ? prev : { ...prev, [step]: now }));
  }, [step, locale]);
  return (
    <>
      <p aria-live="polite" className="mt-4 text-base">
        {t.statusLabel}: <strong>{labels[step]}</strong>
      </p>
      <ol aria-label={t.progressLabel} className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {labels.slice(0, copy.line).map((label, i) => (
          <li
            key={label}
            aria-current={i === step ? "step" : undefined}
            className={`border-t-4 pt-2 text-xs font-semibold leading-snug motion-safe:transition-colors motion-safe:duration-150 ${
              i <= step ? "border-brand text-ink" : "border-subtle text-subink"
            }`}
          >
            {label}
            {reachedAt[i] ? <span className="mt-0.5 block font-normal tabular-nums text-subink">{reachedAt[i]}</span> : null}
          </li>
        ))}
      </ol>
    </>
  );
}

export function PaymentCancelledOrRefundedSection({
  phase,
  moneyTaken,
  orderNumber,
  onOrderAgain
}: {
  phase: "cancelled" | "refunded";
  moneyTaken: boolean;
  orderNumber?: string;
  onOrderAgain: () => void;
}) {
  const { t } = useLocale();
  const title = phase === "refunded" ? t.refundedTitle : t.cancelledTitle;
  const body = phase === "refunded" ? t.refundedBody : moneyTaken ? t.cancelledPaidBody : t.cancelledBody;
  return (
    <section className="mx-auto max-w-md px-5 py-8 md:max-w-lg">
      <div className="rounded-3xl border border-subtle bg-surface p-6 text-center shadow-sm md:p-8">
        <StateIllustration kind="paymentFailed" width={152} height={114} className="mx-auto mb-4" />
        <h1 className="text-2xl font-bold text-ink">{title}</h1>
        {orderNumber ? (
          <p className="mt-5 text-sm text-subink">
            {t.orderNumber}
            <span className="mt-0.5 block text-4xl font-extrabold tabular-nums text-ink">{orderNumber}</span>
          </p>
        ) : null}
        <p role="status" className="mt-4 text-base text-ink">{body}</p>
        <button type="button" onClick={onOrderAgain} className="btn-primary mt-8 min-h-[56px] w-full rounded-xl text-base font-bold">
          {t.orderAgain}
        </button>
      </div>
    </section>
  );
}

export function PaymentPaidSection({
  orderId,
  orderNumber,
  status,
  serviceMode,
  muted,
  onToggleSound,
  onOrderAgain
}: {
  orderId: string;
  orderNumber?: string;
  status?: string;
  serviceMode: ServiceMode;
  muted: boolean;
  onToggleSound: () => void;
  onOrderAgain: () => void;
}) {
  const { t } = useLocale();
  const [email, setEmail] = useState("");
  const [emailError, setEmailError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<"idle" | "sending" | "sent" | "failed">("idle");

  const sendReceipt = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanEmail = normalizeSubmissionText(email, 254);
    if (!isValidEmail(cleanEmail)) {
      setEmailError(t.invalidEmail);
      return;
    }
    setEmailError(null);
    setReceipt("sending");
    try {
      await apiFetch(`/api/v1/public/guest/orders/${encodeURIComponent(orderId)}/receipt/send`, {
        method: "POST",
        body: { channel: "email", to: cleanEmail }
      });
      setReceipt("sent");
    } catch {
      setReceipt("failed");
    }
  };

  return (
    <section className="mx-auto max-w-md px-5 py-8 md:max-w-lg">
      <div className="rounded-3xl border border-subtle bg-surface p-6 shadow-sm md:p-8">
        <div className="flex items-center gap-3">
          <span className="inline-flex shrink-0 text-[var(--state-ok-fg)] motion-safe:animate-pop">
            <CircleCheck size={44} />
          </span>
          <div>
            <h1 className="text-2xl font-extrabold tracking-tight text-ink">{t.paidTitle}</h1>
            {orderNumber ? (
              <p className="mt-0.5 text-xs font-semibold uppercase tracking-wider text-subink">
                {t.orderNumber}
              </p>
            ) : null}
          </div>
        </div>
        {orderNumber ? (
          <p className="mt-3 text-4xl font-extrabold tabular-nums tracking-tight text-ink">{orderNumber}</p>
        ) : null}
        <ProgressSteps status={status} mode={serviceMode} />
        <button type="button" onClick={onToggleSound} className="btn-secondary mt-4 min-h-[44px] rounded-xl px-4 text-sm font-semibold">
          {muted ? t.unmuteSound : t.muteSound}
        </button>

        <form onSubmit={sendReceipt} noValidate className="mt-6 border-t border-subtle pt-5">
          <label htmlFor="receipt-email" className="block text-sm font-bold text-ink">
            {t.receiptEmail}
          </label>
          <div className="mt-1.5 flex gap-2">
            <input
              id="receipt-email"
              type="email"
              required
              value={email}
              onChange={(e) => {
                setEmail(stripControlChars(e.target.value, 254));
                if (emailError) setEmailError(null);
              }}
              autoComplete="email"
              aria-invalid={emailError ? true : undefined}
              aria-describedby={emailError ? "receipt-email-hint receipt-email-error" : "receipt-email-hint"}
              className="input-field min-w-0 flex-1"
            />
            <button type="submit" disabled={receipt === "sending"} className="btn-secondary min-h-[44px] shrink-0 rounded-xl px-4 font-semibold">
              {receipt === "sending" ? t.sending : t.send}
            </button>
          </div>
          <p id="receipt-email-hint" className="mt-1.5 text-xs text-subink">
            {t.receiptEmailHint}{" "}
            <a href="/legal" target="_blank" rel="noopener" className="underline">{t.legal}</a>
          </p>
          {emailError ? (
            <p id="receipt-email-error" role="alert" className="mt-1.5 text-xs font-semibold text-[var(--state-err-fg)]">
              {emailError}
            </p>
          ) : null}
          {receipt === "sent" ? <p role="status" className="mt-2 text-sm">{t.receiptSent}</p> : null}
          {receipt === "failed" ? <p role="alert" className="mt-2 text-sm font-semibold">{t.receiptFailed}</p> : null}
        </form>

        <button type="button" onClick={onOrderAgain} className="btn-primary mt-6 min-h-[56px] w-full rounded-xl text-base font-bold">
          {t.orderAgain}
        </button>
      </div>
    </section>
  );
}
