"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Check, CircleCheck, Clock, Copy } from "lucide-react";
import { useLocale } from "@/components/Providers";
import { QrCodeSvg } from "@/components/QrCodeSvg";
import { DataStateBox } from "@/components/Shell";
import { BankPicker, MidtransTrustFooter, PaymentChannelLogo } from "@/features/order/PaymentMethodPicker";
import type { BankCode } from "@/features/order/types";
import { ApiError, apiFetch, isMaintenance } from "@/lib/api";
import { formatIdr } from "@/lib/format";

interface Share {
  store_name: string;
  amount: number;
  status: string;
  hide_powered_by_badge?: boolean;
  multi_payment_enabled?: boolean;
  cashier_requested_at?: string | null;
}

interface ShareCharge {
  method?: string;
  qr_string?: string;
  va_number?: string;
  bank?: string;
  expires_at?: string;
  amount: number;
}

const POLL_MS = 5_000;

/** One person's share of a split bill, opened from a link the orderer sent (W13). QRIS only. */
export function SharePayView({ token }: { token: string }) {
  const { t, locale, setHidePoweredBy } = useLocale();
  const path = `/api/v1/public/split-shares/${encodeURIComponent(token)}`;
  const [share, setShare] = useState<Share | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "maintenance" | "failed">("loading");
  const [charge, setCharge] = useState<ShareCharge | null>(null);
  const [expired, setExpired] = useState(false);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [copiedVa, setCopiedVa] = useState(false);
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedMethod, setSelectedMethod] = useState<"qris" | "va" | "cashier">("qris");
  const [bank, setBank] = useState<BankCode>("bca");
  const [cashierRequested, setCashierRequested] = useState(false);
  // One key per QR: a double tap replays the same charge, a new QR after expiry gets a new key.
  const payKey = useRef(crypto.randomUUID());

  const load = useCallback(async () => {
    try {
      setShare(await apiFetch<Share>(path));
      setState("ready");
    } catch (e) {
      setState(e instanceof ApiError && e.status === 404 ? "missing" : isMaintenance(e) ? "maintenance" : "failed");
    }
  }, [path]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setHidePoweredBy(Boolean(share?.hide_powered_by_badge));
    return () => setHidePoweredBy(false);
  }, [share?.hide_powered_by_badge, setHidePoweredBy]);

  const paid = share?.status === "paid";
  useEffect(() => {
    if (!charge || paid) return;
    const id = setInterval(() => {
      if (!document.hidden) void load();
    }, POLL_MS);
    return () => clearInterval(id);
  }, [charge, paid, load]);

  useEffect(() => {
    setExpired(false);
    if (!charge?.expires_at) return;
    const deadlineMs = new Date(charge.expires_at).getTime();
    setNowMs(Date.now());
    if (deadlineMs <= Date.now()) {
      setExpired(true);
      return;
    }
    const id = setInterval(() => {
      const now = Date.now();
      setNowMs(now);
      if (now >= deadlineMs) {
        setExpired(true);
        clearInterval(id);
      }
    }, 1000);
    return () => clearInterval(id);
  }, [charge]);

  const handleCopyVa = async (vaNumber: string) => {
    try {
      await navigator.clipboard.writeText(vaNumber);
      setCopiedVa(true);
      setTimeout(() => setCopiedVa(false), 2000);
    } catch {
      // Ignore clipboard permission failure
    }
  };

  const pay = async (fresh: boolean, method: "qris" | "va" | "cashier" = selectedMethod) => {
    if (fresh) payKey.current = crypto.randomUUID();
    setPaying(true);
    setError(null);
    try {
      const body: Record<string, string> = { idempotency_key: payKey.current, method };
      if (method === "va") body.bank = bank;
      const res = await apiFetch<ShareCharge>(`${path}/payments`, { method: "POST", body });
      if (method === "cashier") {
        setCashierRequested(true);
      } else {
        setCharge(res);
      }
    } catch (e) {
      setError(isMaintenance(e) ? t.maintenanceShort : t.checkoutFailed);
    } finally {
      setPaying(false);
    }
  };

  if (state === "loading") {
    return (
      <div className="mx-auto max-w-md px-5 py-10 md:max-w-lg">
        <DataStateBox state="loading" title={t.loadingTitle} description={t.sharePageTitle} />
      </div>
    );
  }
  if (state !== "ready" || !share) {
    const [title, body, illustration] =
      state === "missing"
        ? [t.shareMissingTitle, t.shareMissingBody, "notFound" as const]
        : state === "maintenance"
          ? [t.maintenanceTitle, t.maintenanceBody, "maintenance" as const]
          : [t.loadFailedTitle, t.loadFailedBody, "offline" as const];
    return (
      <div className="mx-auto max-w-md px-5 py-10 md:max-w-lg">
        <DataStateBox
          state="error"
          illustration={illustration}
          title={title}
          description={body}
          onRetry={state !== "missing" ? () => void load() : undefined}
        />
      </div>
    );
  }

  if (paid) {
    return (
      <section className="mx-auto max-w-md px-5 py-10 md:max-w-lg">
        <div className="flex flex-col items-center rounded-3xl border border-subtle bg-surface p-6 text-center shadow-sm md:p-8">
          <span className="inline-block text-[var(--state-ok-fg)] motion-safe:animate-pop">
            <CircleCheck size={56} />
          </span>
          <h1 className="mt-3 text-2xl font-bold text-ink">{t.sharePaidTitle}</h1>
          <p className="mt-2 text-base text-subink">{t.sharePaidBody}</p>
        </div>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-md px-5 py-8 md:max-w-lg">
      <div className="flex flex-col items-center rounded-3xl border border-subtle bg-surface p-6 text-center shadow-sm md:p-8">
        <p className="text-xs font-semibold uppercase tracking-wider text-subink">{share.store_name}</p>
        <h1 className="mt-1 text-2xl font-extrabold tracking-tight text-ink">{t.sharePageTitle}</h1>
        <p className="mt-4 text-3xl font-extrabold tabular-nums tracking-tight text-ink">{formatIdr(share.amount)}</p>

        {cashierRequested || share.cashier_requested_at ? (
          <div role="status" className="mt-6 w-full rounded-2xl border border-subtle bg-canvas p-4 text-center">
            <p className="text-base font-bold text-ink">{t.shareCashierRequested}</p>
            <p className="mt-1 text-sm text-subink">{t.shareCashierRequestedBody}</p>
          </div>
        ) : !charge ? (
          <div className="mt-6 w-full space-y-3">
            {share.multi_payment_enabled !== false ? (
              <div className="grid grid-cols-3 gap-2">
                {(["qris", "va", "cashier"] as const).map((m) => (
                  <button
                    key={m}
                    type="button"
                    onClick={() => setSelectedMethod(m)}
                    className={`min-h-[44px] cursor-pointer rounded-xl border px-2 text-xs font-bold transition ${
                      selectedMethod === m
                        ? "border-brand bg-brand text-brand-fg"
                        : "border-subtle bg-canvas text-ink hover:border-brand"
                    }`}
                  >
                    {m === "qris" ? "QRIS" : m === "va" ? t.sharePayVa : t.sharePayCashier}
                  </button>
                ))}
              </div>
            ) : null}
            {selectedMethod === "va" && share.multi_payment_enabled !== false ? (
              <BankPicker selected={bank} disabled={paying} label={t.selectBank} onSelect={setBank} />
            ) : null}
            <button type="button" disabled={paying} onClick={() => void pay(false)} className="btn-primary inline-flex min-h-[56px] w-full cursor-pointer items-center justify-center gap-2 rounded-xl text-base font-bold motion-safe:transition-transform motion-safe:active:scale-[0.98]">
              {selectedMethod === "qris" ? <PaymentChannelLogo method="qris" className="h-4 w-auto" /> : null}
              <span>
                {paying
                  ? t.placing
                  : selectedMethod === "va"
                    ? t.sharePayVa
                    : selectedMethod === "cashier"
                      ? t.sharePayCashier
                      : t.sharePayCta}
              </span>
            </button>
          </div>
        ) : expired ? (
          <div role="alert" className="mt-6 flex flex-col items-center">
            <p className="font-semibold">{t.expired}</p>
            <button type="button" disabled={paying} onClick={() => void pay(true)} className="btn-primary mt-3 min-h-[56px] cursor-pointer rounded-xl px-6 font-bold motion-safe:transition-transform motion-safe:active:scale-[0.98]">
              {t.newQr}
            </button>
          </div>
        ) : charge.va_number ? (
          <div className="mt-4 w-full rounded-2xl border border-subtle bg-canvas p-4 text-left">
            <div className="flex items-center justify-between gap-2">
              <p className="text-xs font-semibold uppercase text-subink">{t.vaNumberLabel} ({charge.bank?.toUpperCase() ?? bank.toUpperCase()})</p>
              <button
                type="button"
                onClick={() => void handleCopyVa(charge.va_number!)}
                className="btn-secondary inline-flex min-h-[44px] shrink-0 cursor-pointer items-center gap-1.5 rounded-lg px-3 text-xs font-semibold"
              >
                {copiedVa ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}
                <span>{copiedVa ? t.copiedToast : t.copyVa}</span>
              </button>
            </div>
            <p className="mt-1.5 font-mono text-xl font-bold tabular-nums text-ink">{charge.va_number}</p>
            {charge.expires_at ? (
              <p className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold tabular-nums text-subink">
                <Clock size={13} aria-hidden="true" />
                <span>
                  {t.payBeforeCountdown}:{" "}
                  {(() => {
                    const sec = Math.max(0, Math.floor((new Date(charge.expires_at).getTime() - nowMs) / 1000));
                    return `${String(Math.floor(sec / 60)).padStart(2, "0")}:${String(sec % 60).padStart(2, "0")}`;
                  })()}
                </span>
              </p>
            ) : null}
            <p className="mt-2 text-xs text-subink">{t.vaHint}</p>
          </div>
        ) : (
          <>
            <p className="mt-2 text-sm text-subink">{t.scanHint}</p>
            <div className="mt-4 rounded-xl border border-subtle bg-white p-4">
              {charge.qr_string ? <QrCodeSvg value={charge.qr_string} size={240} /> : null}
            </div>
            {charge.expires_at ? (
              <p className="mt-4 inline-flex items-center gap-1.5 text-sm tabular-nums">
                <Clock size={14} aria-hidden="true" className="text-subink" />
                <span>
                  {t.payBefore}{" "}
                  <strong>{new Date(charge.expires_at).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" })}</strong>
                  {" ("}
                  {(() => {
                    const sec = Math.max(0, Math.floor((new Date(charge.expires_at).getTime() - nowMs) / 1000));
                    return `${String(Math.floor(sec / 60)).padStart(2, "0")}:${String(sec % 60).padStart(2, "0")}`;
                  })()}
                  {")"}
                </span>
              </p>
            ) : null}
            <p aria-live="polite" className="mt-2 text-sm text-subink">{t.waiting}</p>
          </>
        )}
        {error ? <p role="alert" className="mt-4 text-sm font-semibold text-[var(--state-err-fg)]">{error}</p> : null}
        <MidtransTrustFooter />
      </div>
    </section>
  );
}
