"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { CircleCheck } from "lucide-react";
import { useLocale } from "@/components/Providers";
import { QrCodeSvg } from "@/components/QrCodeSvg";
import { DataStateBox } from "@/components/Shell";
import { MidtransTrustFooter, PaymentChannelLogo } from "@/features/order/PaymentMethodPicker";
import { ApiError, apiFetch, isMaintenance } from "@/lib/api";
import { formatIdr } from "@/lib/format";

interface Share {
  store_name: string;
  amount: number;
  status: string;
}

interface ShareCharge {
  qr_string: string;
  expires_at?: string;
  amount: number;
}

const POLL_MS = 5_000;

/** One person's share of a split bill, opened from a link the orderer sent (W13). QRIS only. */
export function SharePayView({ token }: { token: string }) {
  const { t, locale } = useLocale();
  const path = `/api/v1/public/split-shares/${encodeURIComponent(token)}`;
  const [share, setShare] = useState<Share | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "maintenance" | "failed">("loading");
  const [charge, setCharge] = useState<ShareCharge | null>(null);
  const [expired, setExpired] = useState(false);
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
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
    const timer = setTimeout(() => setExpired(true), Math.max(new Date(charge.expires_at).getTime() - Date.now(), 0));
    return () => clearTimeout(timer);
  }, [charge]);

  const pay = async (fresh: boolean) => {
    if (fresh) payKey.current = crypto.randomUUID();
    setPaying(true);
    setError(null);
    try {
      setCharge(await apiFetch<ShareCharge>(`${path}/payments`, { method: "POST", body: { idempotency_key: payKey.current } }));
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

        {!charge ? (
          <button type="button" disabled={paying} onClick={() => void pay(false)} className="btn-primary mt-6 inline-flex min-h-[56px] w-full cursor-pointer items-center justify-center gap-2 rounded-xl text-base font-bold motion-safe:transition-transform motion-safe:active:scale-[0.98]">
            <PaymentChannelLogo method="qris" className="h-4 w-auto" />
            <span>{paying ? t.placing : t.sharePayCta}</span>
          </button>
        ) : expired ? (
          <div role="alert" className="mt-6 flex flex-col items-center">
            <p className="font-semibold">{t.expired}</p>
            <button type="button" disabled={paying} onClick={() => void pay(true)} className="btn-primary mt-3 min-h-[56px] cursor-pointer rounded-xl px-6 font-bold motion-safe:transition-transform motion-safe:active:scale-[0.98]">
              {t.newQr}
            </button>
          </div>
        ) : (
          <>
            <p className="mt-2 text-sm text-subink">{t.scanHint}</p>
            <div className="mt-4 rounded-xl border border-subtle bg-white p-4">
              <QrCodeSvg value={charge.qr_string} size={240} />
            </div>
            {charge.expires_at ? (
              <p className="mt-4 text-sm">
                {t.payBefore}{" "}
                <strong>{new Date(charge.expires_at).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" })}</strong>
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
