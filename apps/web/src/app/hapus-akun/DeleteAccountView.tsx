"use client";

import { CassyWordmark } from "@/components/CassyLogo";
import { useLocale } from "@/components/Providers";
import { ApiError, apiFetch } from "@/lib/api";
import type { components } from "@/types/api.generated";
import { ArrowLeft, Globe } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";

type DeletionRequest = components["schemas"]["AccountDeletionRequest"];
type DeletionPending = components["schemas"]["AccountDeletionPendingResponse"];
type Step = "form" | "code" | "done" | "pending" | "cancelled";

/** Owner-facing account deletion (FL-Q9): email code, 30-day grace period, cancel any time before it ends. */
export function DeleteAccountView() {
  const { t, locale, toggleLocale } = useLocale();
  const [step, setStep] = useState<Step>("form");
  const [slug, setSlug] = useState("");
  const [email, setEmail] = useState("");
  const [reason, setReason] = useState("");
  const [code, setCode] = useState("");
  const [request, setRequest] = useState<DeletionRequest | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const formatDate = (iso: string) => new Date(iso).toLocaleDateString(locale, { dateStyle: "long" });

  const errorText = (err: unknown) => {
    if (!(err instanceof ApiError)) return t.delErrGeneric;
    const byStatus: Record<number, string> = { 409: t.delErrBusy, 404: t.delErrNotPending, 422: t.delErrCode, 429: t.delErrRate };
    return byStatus[err.status] ?? t.delErrGeneric;
  };

  const sendCode = async (e?: FormEvent) => {
    e?.preventDefault();
    setBusy(true);
    setError("");
    try {
      await apiFetch("/api/v1/public/account-deletion/otp", { method: "POST", body: { store_slug: slug.trim(), email: email.trim() } });
      setCode("");
      setStep("code");
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const submit = async (action: "confirm" | "cancel") => {
    setBusy(true);
    setError("");
    const path = action === "confirm" ? "/api/v1/public/account-deletion" : "/api/v1/public/account-deletion/cancel";
    try {
      const res = await apiFetch<components["schemas"]["AccountDeletionResponse"]>(path, {
        method: "POST",
        body: { store_slug: slug.trim(), email: email.trim(), otp_code: code, reason: reason.trim() }
      });
      setRequest(res.request);
      setStep(action === "confirm" ? "done" : "cancelled");
    } catch (err) {
      const pending = err instanceof ApiError && err.status === 409 ? (err.details as DeletionPending | null) : null;
      if (pending?.code === "ACCOUNT_DELETION_PENDING" && pending.request) {
        setRequest(pending.request);
        setStep("pending");
      } else {
        setError(errorText(err));
      }
    } finally {
      setBusy(false);
    }
  };

  const linkButton = "min-h-[44px] text-sm font-medium text-brand underline underline-offset-4";

  return (
    <div className="mx-auto max-w-2xl px-5 py-8">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-subtle pb-4">
        <div className="flex items-center gap-3">
          <Link href="/" aria-label={t.delHome} className="inline-flex min-h-[48px] items-center rounded-lg">
            <CassyWordmark height={22} />
          </Link>
          <Link
            href="/"
            className="inline-flex min-h-[48px] items-center gap-1.5 rounded-xl border border-subtle bg-canvas px-3 py-2 text-xs font-semibold text-ink transition hover:border-brand"
          >
            <ArrowLeft className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            <span>{t.delHome}</span>
          </Link>
        </div>
        <button
          type="button"
          onClick={toggleLocale}
          aria-label={t.delSwitchLanguage}
          className="inline-flex min-h-[48px] items-center gap-1.5 rounded-xl border border-subtle bg-canvas px-3.5 py-2 text-xs font-semibold text-ink transition hover:border-brand"
        >
          <Globe className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          <span>{locale === "en-US" ? "EN | ID" : "ID | EN"}</span>
        </button>
      </header>

      <div>
        <h1 className="mt-6 text-2xl font-bold sm:text-3xl">{t.delTitle}</h1>
        <p className="mt-2 text-base leading-relaxed text-subink">{t.delIntro}</p>

        <section aria-labelledby="del-what" className="mt-6 rounded-2xl border border-subtle bg-surface p-5">
          <h2 id="del-what" className="text-lg font-bold">
            {t.delWhatTitle}
          </h2>
          <ul className="mt-3 list-disc space-y-2 pl-5 text-base leading-7 text-ink">
            <li>{t.delGrace}</li>
            <li>{t.delPaymentsOff}</li>
            <li>{t.delRemoved}</li>
            <li>{t.delKept}</li>
          </ul>
          <Link href="/legal/mitra" className="mt-3 inline-flex min-h-[44px] items-center font-medium text-brand underline underline-offset-4">
            {t.delPrivacyLink}
          </Link>
        </section>

        <div className="mt-6 rounded-2xl border border-subtle bg-surface p-5">
          {error ? (
            <p role="alert" className="mb-4 rounded-lg border border-subtle bg-canvas p-3 text-sm font-medium text-ink">
              {error}
            </p>
          ) : null}

          {step === "form" ? (
            <form onSubmit={sendCode} className="space-y-4">
              <div>
                <label htmlFor="del-slug" className="block text-sm font-semibold">
                  {t.delStoreSlug}
                </label>
                <input
                  id="del-slug"
                  className="input-field mt-1"
                  required
                  autoCapitalize="none"
                  aria-describedby="del-slug-hint"
                  value={slug}
                  onChange={(e) => setSlug(e.target.value)}
                />
                <p id="del-slug-hint" className="mt-1 text-xs text-subink">
                  {t.delStoreSlugHint}
                </p>
              </div>
              <div>
                <label htmlFor="del-email" className="block text-sm font-semibold">
                  {t.delEmail}
                </label>
                <input
                  id="del-email"
                  type="email"
                  className="input-field mt-1"
                  required
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
              <div>
                <label htmlFor="del-reason" className="block text-sm font-semibold">
                  {t.delReason}
                </label>
                <textarea
                  id="del-reason"
                  className="input-field mt-1"
                  rows={3}
                  maxLength={500}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </div>
              <button type="submit" disabled={busy} className="btn-primary min-h-[48px] w-full px-5 py-2 text-sm font-semibold">
                {busy ? t.delSending : t.delSendCode}
              </button>
            </form>
          ) : null}

          {step === "code" ? (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void submit("confirm");
              }}
              className="space-y-4"
            >
              <p role="status" className="text-sm leading-relaxed text-subink">
                {t.delCodeSent}
              </p>
              <div>
                <label htmlFor="del-code" className="block text-sm font-semibold">
                  {t.delCode}
                </label>
                <input
                  id="del-code"
                  className="input-field mt-1 font-mono tracking-[0.3em]"
                  required
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                />
              </div>
              <button type="submit" disabled={busy || code.length !== 6} className="btn-primary min-h-[48px] w-full px-5 py-2 text-sm font-semibold">
                {busy ? t.delWorking : t.delConfirm}
              </button>
              <button
                type="button"
                disabled={busy || code.length !== 6}
                onClick={() => void submit("cancel")}
                className="btn-secondary min-h-[48px] w-full px-5 py-2 text-sm font-semibold"
              >
                {t.delCancelExisting}
              </button>
              <div className="flex flex-wrap gap-4">
                <button type="button" disabled={busy} onClick={() => void sendCode()} className={linkButton}>
                  {t.delResend}
                </button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    setStep("form");
                    setError("");
                  }}
                  className={linkButton}
                >
                  {t.delBack}
                </button>
              </div>
            </form>
          ) : null}

          {step === "done" && request ? (
            <div role="status">
              <h2 className="text-lg font-bold">{t.delDoneTitle}</h2>
              <p className="mt-2 text-base leading-7">{t.delDoneBody.replace("{date}", formatDate(request.cooling_off_ends_at))}</p>
              <p className="mt-2 text-sm leading-relaxed text-subink">{t.delKept}</p>
            </div>
          ) : null}

          {step === "pending" && request ? (
            <div role="status" className="space-y-4">
              <h2 className="text-lg font-bold">{t.delPendingTitle}</h2>
              <p className="text-base leading-7">{t.delPendingBody.replace("{date}", formatDate(request.cooling_off_ends_at))}</p>
              <button type="button" disabled={busy} onClick={() => void sendCode()} className="btn-secondary min-h-[48px] w-full px-5 py-2 text-sm font-semibold">
                {t.delResend}
              </button>
            </div>
          ) : null}

          {step === "cancelled" ? (
            <div role="status">
              <h2 className="text-lg font-bold">{t.delCancelledTitle}</h2>
              <p className="mt-2 text-base leading-7">{t.delCancelledBody}</p>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
