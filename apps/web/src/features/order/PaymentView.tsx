"use client";

import React, { useEffect, useRef, useState } from "react";
import { Check, CircleCheck, Clock, Copy, RefreshCw } from "lucide-react";
import { useLocale } from "@/components/Providers";
import { QrCodeSvg } from "@/components/QrCodeSvg";
import { StateIllustration } from "@/components/StateIllustration";
import { apiFetch } from "@/lib/api";
import { formatIdr } from "@/lib/format";
import type { Messages } from "@/lib/messages";
import { isValidEmail, normalizeSubmissionText } from "@/lib/sanitize";
import { BankPicker, bankMeta, METHOD_LABEL, MidtransTrustFooter, PaymentChannelLogo } from "./PaymentMethodPicker";
import { progressStep, safeDeeplink, type OrderPhase } from "./status";
import type { BankCode, PaymentInfo, PayMethod, ServiceMode } from "./types";

// Status text per progressStep (0 received, 1 preparing, 2 ready, 3 served) and the steps drawn on the line.
// Pickup ends when the order is ready at the bar; table service goes on until staff mark it delivered.
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
  // Times are only for steps this page saw happen; a step already reached on load has no honest time.
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
      <ol aria-label={t.progressLabel} className="mt-4 flex gap-2">
        {labels.slice(0, copy.line).map((label, i) => (
          <li
            key={label}
            aria-current={i === step ? "step" : undefined}
            className={`flex-1 border-t-4 pt-2 text-xs font-semibold motion-safe:transition-colors motion-safe:duration-150 ${
              i <= step ? "border-brand text-ink" : "border-subtle text-subink"
            }`}
          >
            {label}
            {reachedAt[i] ? <span className="block font-normal tabular-nums text-subink">{reachedAt[i]}</span> : null}
          </li>
        ))}
      </ol>
    </>
  );
}

export function PaymentView({
  payment,
  phase,
  moneyTaken,
  orderId,
  orderNumber,
  storeName,
  status,
  retrying,
  error,
  methods,
  bank,
  serviceMode,
  muted,
  onToggleSound,
  onNewQr,
  onSwitch,
  onSelectBank,
  onNotify,
  onCheckStatus,
  onBack,
  onOrderAgain
}: {
  payment: PaymentInfo;
  phase: OrderPhase;
  moneyTaken: boolean;
  orderId: string;
  orderNumber?: string;
  storeName?: string;
  status?: string;
  retrying: boolean;
  error: string | null;
  methods: PayMethod[];
  bank: BankCode;
  serviceMode: ServiceMode;
  muted: boolean;
  onToggleSound: () => void;
  onNewQr: () => void;
  onSwitch: (method: PayMethod, bank?: BankCode) => void;
  onSelectBank: (bank: BankCode) => void;
  onNotify?: (message: string) => void;
  onCheckStatus?: () => void;
  onBack: () => void;
  onOrderAgain: () => void;
}) {
  const { t, locale } = useLocale();
  const [expired, setExpired] = useState(false);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [email, setEmail] = useState("");
  const [emailError, setEmailError] = useState<string | null>(null);
  const [copiedField, setCopiedField] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<"idle" | "sending" | "sent" | "failed">("idle");

  useEffect(() => {
    setExpired(false);
    if (!payment.expires_at || phase !== "awaiting") return;
    const deadlineTime = new Date(payment.expires_at).getTime();
    setNowMs(Date.now());
    const remaining = deadlineTime - Date.now();
    if (remaining <= 0) {
      setExpired(true);
      return;
    }
    const tick = setInterval(() => {
      const current = Date.now();
      setNowMs(current);
      if (deadlineTime - current <= 0) setExpired(true);
    }, 1000);
    return () => clearInterval(tick);
  }, [payment.expires_at, phase]);

  const copyValue = async (field: string, text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedField(field);
      onNotify?.(t.copiedToast);
      setTimeout(() => setCopiedField((prev) => (prev === field ? null : prev)), 2500);
    } catch {
      // Clipboard may be restricted in non-HTTPS contexts.
    }
  };

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

  if (phase === "cancelled" || phase === "refunded") {
    const title = phase === "refunded" ? t.refundedTitle : t.cancelledTitle;
    const body = phase === "refunded" ? t.refundedBody : moneyTaken ? t.cancelledPaidBody : t.cancelledBody;
    return (
      <section className="mx-auto max-w-md px-5 py-10 text-center">
        <StateIllustration kind="paymentFailed" width={152} height={114} className="mb-4" />
        <h1 className="text-2xl font-bold text-ink">{title}</h1>
        {orderNumber ? (
          <p className="mt-5 text-sm text-subink">
            {t.orderNumber}
            <span className="block text-4xl font-bold tabular-nums text-ink">{orderNumber}</span>
          </p>
        ) : null}
        <p role="status" className="mt-4 text-base text-ink">{body}</p>
        <button type="button" onClick={onOrderAgain} className="btn-primary mt-8 min-h-[56px] w-full text-base font-bold">
          {t.orderAgain}
        </button>
      </section>
    );
  }

  if (phase === "paid") {
    return (
      <section className="mx-auto max-w-md px-5 py-10">
        <span className="inline-block text-[var(--state-ok-fg)] motion-safe:animate-pop">
          <CircleCheck size={56} />
        </span>
        <h1 className="mt-3 text-2xl font-bold">{t.paidTitle}</h1>
        {orderNumber ? (
          <p className="mt-6 text-sm text-subink">
            {t.orderNumber}
            <span className="block text-4xl font-bold text-ink">{orderNumber}</span>
          </p>
        ) : null}
        <ProgressSteps status={status} mode={serviceMode} />
        <button type="button" onClick={onToggleSound} className="btn-secondary mt-4 min-h-[44px] px-4 text-sm font-semibold">
          {muted ? t.unmuteSound : t.muteSound}
        </button>

        <form onSubmit={sendReceipt} noValidate className="mt-8">
          <label htmlFor="receipt-email" className="block text-sm font-bold">
            {t.receiptEmail}
          </label>
          <div className="mt-1 flex gap-2">
            <input
              id="receipt-email"
              type="email"
              required
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (emailError) setEmailError(null);
              }}
              autoComplete="email"
              aria-invalid={emailError ? true : undefined}
              aria-describedby={emailError ? "receipt-email-hint receipt-email-error" : "receipt-email-hint"}
              className="input-field min-w-0 flex-1"
            />
            <button type="submit" disabled={receipt === "sending"} className="btn-secondary min-h-[44px] px-4 font-semibold">
              {receipt === "sending" ? t.sending : t.send}
            </button>
          </div>
          <p id="receipt-email-hint" className="mt-1 text-xs text-subink">
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

        <button type="button" onClick={onOrderAgain} className="btn-primary mt-8 min-h-[56px] w-full text-base font-bold">
          {t.orderAgain}
        </button>
      </section>
    );
  }

  const via = payment.method ?? "qris";
  const isVa = via === "bank_transfer";
  const wallet = via !== "qris" && !isVa;
  const deeplink = wallet ? safeDeeplink(payment.deeplink_url) : null;
  const others = methods.filter((m) => m !== via);
  const selectedBankInfo = bankMeta(payment.bank ?? bank);
  const deadline = payment.expires_at
    ? new Date(payment.expires_at).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" })
    : null;
  const remainingSec = payment.expires_at ? Math.max(0, Math.floor((new Date(payment.expires_at).getTime() - nowMs) / 1000)) : null;
  const countdownText =
    remainingSec !== null
      ? `${String(Math.floor(remainingSec / 60)).padStart(2, "0")}:${String(remainingSec % 60).padStart(2, "0")}`
      : null;
  const expiringSoon = remainingSec !== null && remainingSec > 0 && remainingSec <= 60;

  return (
    <section className="mx-auto flex max-w-md flex-col items-center py-5 pl-[max(1.25rem,env(safe-area-inset-left))] pr-[max(1.25rem,env(safe-area-inset-right))] pt-[max(1.25rem,env(safe-area-inset-top))] text-center">
      {via === "qris" ? (
        <span className="mb-2 inline-flex items-center rounded-full bg-[var(--state-ok-bg)] px-3 py-1 text-xs font-bold text-[var(--state-ok-fg)]">
          QRIS | {t.qrisRecommended}
        </span>
      ) : null}
      <h1 className="text-2xl font-bold">{isVa ? t.bankTransferLabel : wallet ? METHOD_LABEL[via] : t.scanToPay}</h1>
      <p className="mt-1.5 text-sm text-subink">{isVa ? t.vaHint : wallet ? t.walletHint : t.scanHint}</p>
      <p className="mt-3 text-3xl font-bold tabular-nums">{formatIdr(payment.amount)}</p>
      {payment.expires_at && deadline && !expired ? (
        <p className={`mt-2 inline-flex items-center gap-1.5 rounded-full px-3.5 py-1 text-sm tabular-nums ${expiringSoon ? "bg-[var(--state-warn-bg)] font-bold text-[var(--state-warn-fg)]" : "bg-muted text-ink"}`}>
          <Clock size={14} aria-hidden="true" />
          <span>{t.payBefore} <strong>{deadline}</strong></span>
          {countdownText ? <span className="opacity-80">({countdownText})</span> : null}
        </p>
      ) : null}

      {expired ? (
        <div role="alert" className="mt-6 w-full rounded-2xl border border-subtle bg-surface p-6 text-center">
          <StateIllustration kind="sessionExpired" width={144} height={108} className="mb-3" />
          <p className="font-semibold text-ink">{t.expired}</p>
          <button type="button" disabled={retrying} onClick={onNewQr} className="btn-primary mt-4 min-h-[56px] w-full px-6 font-bold">
            <RefreshCw size={17} aria-hidden="true" />
            <span>{t.newQr}</span>
          </button>
        </div>
      ) : (
        <>
          {isVa ? (
            <div className="mt-6 w-full space-y-3 rounded-2xl border border-subtle bg-surface p-5 text-left">
              {selectedBankInfo ? (
                <div className="flex items-center gap-2.5 border-b border-subtle pb-3">
                  <span className="inline-flex h-8 items-center justify-center rounded bg-white px-2 ring-1 ring-black/10">
                    <img src={selectedBankInfo.logo} alt="" aria-hidden="true" className="h-4 w-auto object-contain" />
                  </span>
                  <span className="text-base font-bold text-ink">{selectedBankInfo.label} Virtual Account</span>
                </div>
              ) : null}

              {payment.biller_code ? (
                <div>
                  <p className="text-xs font-semibold text-subink">{t.billerCodeLabel}</p>
                  <p className="mt-1 font-mono text-xl font-bold tracking-wider tabular-nums text-ink">{payment.biller_code}</p>
                </div>
              ) : null}

              {payment.bill_key ? (
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-xs font-semibold text-subink">{t.billKeyLabel}</p>
                    <p className="mt-1 break-all font-mono text-xl font-bold tracking-wider tabular-nums text-ink">{payment.bill_key}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => void copyValue("bill_key", payment.bill_key!)}
                    className="btn-secondary inline-flex min-h-[48px] shrink-0 items-center gap-1.5 px-3.5 text-sm font-semibold"
                  >
                    {copiedField === "bill_key" ? <Check size={16} aria-hidden="true" /> : <Copy size={16} aria-hidden="true" />}
                    <span>{t.copyBillKey}</span>
                  </button>
                </div>
              ) : null}

              {payment.va_number ? (
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-xs font-semibold text-subink">{t.vaNumberLabel}</p>
                    <p data-testid="va-number" className="mt-1 break-all font-mono text-xl font-bold tracking-wider tabular-nums text-ink">
                      {payment.va_number}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => void copyValue("va", payment.va_number!)}
                    className="btn-secondary inline-flex min-h-[48px] shrink-0 items-center gap-1.5 px-3.5 text-sm font-semibold"
                  >
                    {copiedField === "va" ? <Check size={16} aria-hidden="true" /> : <Copy size={16} aria-hidden="true" />}
                    <span>{t.copyVa}</span>
                  </button>
                </div>
              ) : !payment.bill_key ? (
                <p role="alert" className="text-sm font-semibold text-[var(--state-err-fg)]">{t.methodUnavailable}</p>
              ) : null}

              {payment.va_number || payment.bill_key ? (
                <details className="group rounded-xl bg-muted px-4 py-3" open>
                  <summary className="cursor-pointer list-none text-sm font-bold text-ink marker:hidden">{t.vaHowTitle}</summary>
                  <ol className="mt-2 list-decimal space-y-1.5 pl-5 text-sm leading-6 text-ink">
                    <li>{t.vaStepOpen}</li>
                    <li>{payment.bill_key ? t.vaStepMultipayment : t.vaStepTransfer}</li>
                    <li>{t.vaStepConfirm}</li>
                    <li>{t.vaStepDone}</li>
                  </ol>
                </details>
              ) : null}

              <BankPicker
                selected={bank}
                disabled={retrying}
                label={t.selectBank}
                onSelect={(nextBank) => {
                  onSelectBank(nextBank);
                  onSwitch("bank_transfer", nextBank);
                }}
              />
            </div>
          ) : wallet ? (
            deeplink ? (
              <a href={deeplink} className="btn-primary mt-6 inline-flex min-h-[56px] w-full items-center justify-center px-6 text-base font-bold">
                {t.openWallet}
              </a>
            ) : (
              <p role="alert" className="mt-6 text-sm font-semibold">{t.methodUnavailable}</p>
            )
          ) : (
            <div className="mt-5 w-full space-y-3 rounded-2xl border border-subtle bg-white p-5">
              {payment.qr_string ? (
                <QrCodeSvg
                  value={payment.qr_string}
                  size={240}
                  downloadMeta={{
                    storeName,
                    orderLabel: orderNumber,
                    amountLabel: formatIdr(payment.amount),
                    deadlineLabel: deadline ? `${t.payBefore} ${deadline}` : undefined,
                    filename: `cassy-qris-${orderNumber ?? orderId}`,
                    pngLabel: t.downloadQrPng,
                    onDownloaded: () => onNotify?.(t.qrDownloaded)
                  }}
                />
              ) : null}
              <details className="rounded-xl bg-[#F7F5F0] px-4 py-3 text-left text-[#17211E]">
                <summary className="cursor-pointer list-none text-sm font-bold marker:hidden">{t.qrisHowTitle}</summary>
                <ol className="mt-2 list-decimal space-y-1 pl-5 text-xs leading-relaxed text-[#4B5854]">
                  <li>{t.qrisStep1}</li>
                  <li>{t.qrisStep2}</li>
                  <li>{t.qrisStep3}</li>
                </ol>
              </details>
            </div>
          )}
          <p aria-live="polite" className="mt-3 text-sm text-subink">{t.waiting}</p>
        </>
      )}

      {error ? <p role="alert" className="mt-4 text-sm font-semibold text-[var(--state-err-fg)]">{error}</p> : null}

      {others.length > 0 ? (
        <div className="mt-6 w-full rounded-2xl border border-subtle bg-surface p-4">
          <p className="text-xs font-bold uppercase tracking-wide text-subink">{t.payOther}</p>
          <div className="mt-2.5 flex flex-wrap justify-center gap-2">
            {others.map((m) => (
              <button
                key={m}
                type="button"
                disabled={retrying}
                onClick={() => onSwitch(m)}
                className="btn-secondary inline-flex min-h-[48px] items-center gap-2 px-4 text-sm font-semibold motion-safe:transition-transform motion-safe:active:scale-[0.98]"
              >
                <PaymentChannelLogo method={m} className="h-4 w-auto" />
                <span>{m === "bank_transfer" ? t.bankTransferLabel : METHOD_LABEL[m]}</span>
              </button>
            ))}
          </div>
        </div>
      ) : null}

      <MidtransTrustFooter />

      <div className="mt-6 flex flex-wrap justify-center gap-3">
        {onCheckStatus ? (
          <button
            type="button"
            disabled={retrying}
            onClick={onCheckStatus}
            className="btn-secondary inline-flex min-h-[44px] items-center gap-1.5 px-4 text-sm font-semibold motion-safe:transition-transform motion-safe:active:scale-[0.98]"
          >
            <RefreshCw size={14} aria-hidden="true" />
            <span>{t.checkStatus}</span>
          </button>
        ) : null}
        <button type="button" onClick={onBack} className="btn-secondary min-h-[44px] px-5 font-semibold motion-safe:transition-transform motion-safe:active:scale-[0.98]">
          {t.back}
        </button>
      </div>
    </section>
  );
}
