"use client";

import { BellRing, Check, Clock } from "lucide-react";
import React, { useCallback, useEffect, useState } from "react";
import { Sheet } from "@/components/Sheet";
import { useLocale } from "@/components/Providers";
import { ApiError, apiFetch } from "@/lib/api";
import { stripControlChars } from "@/lib/sanitize";

interface Template {
  id: string;
  label: string;
}

interface ServiceRequest {
  id: string;
  template_id?: string;
  label: string;
  status: "open" | "acknowledged" | "done" | "cancelled";
  created_at: string;
}

const NOTE_MAX = 80;
const DEFAULT_COOLDOWN_SEC = 60;
const COOLDOWN_STORAGE_KEY = "cassy.callWaiter.cooldownUntilMs";

function formatMmSs(totalSec: number): string {
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

/**
 * "Panggil Pelayan" (feature flag call_waiter). One tap on a template sends it; the backend enforces
 * a 5-minute per-table cooldown and allows POS staff to mute spam tables for 5 minutes.
 */
export function CallWaiter({ onNotify }: { onNotify: (message: string) => void }) {
  const { t } = useLocale();
  const [open, setOpen] = useState(false);
  const [templates, setTemplates] = useState<Template[] | null>(null);
  const [mine, setMine] = useState<ServiceRequest[]>([]);
  const [note, setNote] = useState("");
  const [sendingId, setSendingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cooldownUntilMs, setCooldownUntilMs] = useState<number>(0);
  const [nowMs, setNowMs] = useState<number>(() => Date.now());

  const applyCooldownUntil = useCallback((untilMs: number) => {
    setCooldownUntilMs((prev) => {
      const next = Math.max(prev, untilMs);
      try {
        if (next > Date.now()) {
          window.sessionStorage.setItem(COOLDOWN_STORAGE_KEY, String(next));
        }
      } catch {
        // Ignore sessionStorage restrictions in private contexts.
      }
      return next;
    });
  }, []);

  useEffect(() => {
    try {
      const saved = Number(window.sessionStorage.getItem(COOLDOWN_STORAGE_KEY) ?? "0");
      if (saved > Date.now()) {
        setCooldownUntilMs(saved);
      }
    } catch {
      // Ignore storage error.
    }
  }, []);

  const remainingSec = Math.max(0, Math.ceil((cooldownUntilMs - nowMs) / 1000));
  const isCoolingDown = remainingSec > 0;

  useEffect(() => {
    if (!isCoolingDown) return;
    const id = window.setInterval(() => setNowMs(Date.now()), 1_000);
    return () => window.clearInterval(id);
  }, [isCoolingDown]);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [tpl, req] = await Promise.all([
        apiFetch<{ templates?: Template[] }>("/api/v1/public/guest/service-request-templates"),
        apiFetch<{ service_requests?: ServiceRequest[]; cooldown_seconds?: number }>("/api/v1/public/guest/service-requests")
      ]);
      setTemplates(tpl.templates ?? []);
      const list = req.service_requests ?? [];
      setMine(list);
      const cooldownSec = req.cooldown_seconds ?? DEFAULT_COOLDOWN_SEC;
      const latest = list[0];
      if (latest?.created_at) {
        const createdMs = Date.parse(latest.created_at);
        if (!Number.isNaN(createdMs) && createdMs <= Date.now() + 5_000) {
          const untilMs = createdMs + cooldownSec * 1000;
          if (untilMs > Date.now()) {
            setNowMs(Date.now());
            applyCooldownUntil(untilMs);
          }
        }
      }
    } catch (err) {
      setTemplates([]);
      setError(err instanceof ApiError && err.status !== 0 ? err.message : t.callWaiterFailed);
    }
  }, [applyCooldownUntil, t.callWaiterFailed]);

  useEffect(() => {
    if (open) void load();
  }, [open, load]);

  const send = async (tpl: Template) => {
    if (isCoolingDown) return;
    setSendingId(tpl.id);
    setError(null);
    try {
      const cleaned = stripControlChars(note, NOTE_MAX).trim();
      const created = await apiFetch<ServiceRequest>("/api/v1/public/guest/service-requests", {
        method: "POST",
        body: cleaned ? { template_id: tpl.id, note: cleaned } : { template_id: tpl.id }
      });
      setMine((prev) => [created, ...prev.filter((r) => r.id !== created.id)]);
      setNote("");
      const now = Date.now();
      setNowMs(now);
      applyCooldownUntil(now + DEFAULT_COOLDOWN_SEC * 1000);
      setOpen(false);
      onNotify(t.callWaiterSent);
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) {
        const details = err.details as { retry_after_seconds?: unknown } | null;
        const retrySec =
          typeof details?.retry_after_seconds === "number" && details.retry_after_seconds > 0
            ? details.retry_after_seconds
            : DEFAULT_COOLDOWN_SEC;
        const now = Date.now();
        setNowMs(now);
        applyCooldownUntil(now + retrySec * 1000);
      }
      setError(err instanceof ApiError && err.status !== 0 ? err.message : t.callWaiterFailed);
    } finally {
      setSendingId(null);
    }
  };

  const statusLabel = (s: ServiceRequest["status"]) =>
    s === "acknowledged" ? t.callWaiterOnTheWay : s === "done" ? t.callWaiterDone : s === "open" ? t.callWaiterWaiting : t.callWaiterCancelled;

  const mmss = formatMmSs(remainingSec);

  return (
    <>
      <button
        type="button"
        aria-label={t.callWaiter}
        onClick={() => {
          setNowMs(Date.now());
          setOpen(true);
        }}
        className="btn-secondary inline-flex min-h-[44px] items-center gap-2 rounded-full px-4 text-sm font-semibold motion-safe:active:scale-[0.97] motion-safe:transition-transform"
      >
        <BellRing size={18} aria-hidden="true" />
        <span>{t.callWaiter}</span>
        {isCoolingDown ? (
          <span
            data-testid="call-waiter-timer"
            className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-xs font-bold tabular-nums text-subink"
          >
            <Clock size={12} aria-hidden="true" />
            {mmss}
          </span>
        ) : null}
      </button>
      {open ? (
        <Sheet title={t.callWaiterTitle} onClose={() => setOpen(false)}>
          <p className="text-sm text-subink">{t.callWaiterHint}</p>
          {isCoolingDown ? (
            <div
              role="status"
              data-testid="call-waiter-cooldown"
              className="mt-3 flex items-center justify-between gap-2 rounded-xl border border-subtle bg-muted px-3.5 py-2.5 text-xs font-semibold text-ink"
            >
              <span>{t.callWaiterCooldownBanner}</span>
              <span className="shrink-0 rounded-full bg-surface px-2.5 py-1 font-mono text-xs font-bold tabular-nums text-ink">
                {mmss}
              </span>
            </div>
          ) : null}
          <label className="mt-4 block text-sm font-semibold" htmlFor="call-waiter-note">
            {t.callWaiterNote}
          </label>
          <input
            id="call-waiter-note"
            value={note}
            maxLength={NOTE_MAX}
            disabled={isCoolingDown}
            onChange={(e) => setNote(stripControlChars(e.target.value, NOTE_MAX))}
            className="mt-1.5 min-h-[48px] w-full rounded-xl border border-subtle bg-surface px-3.5 text-base disabled:opacity-60"
          />
          {templates === null ? (
            <p className="mt-4 text-sm text-subink" aria-live="polite">{t.loadingTitle}</p>
          ) : templates.length === 0 && !error ? (
            <p className="mt-4 text-sm text-subink">{t.callWaiterEmpty}</p>
          ) : (
            <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
              {templates.map((tpl) => {
                const activeReq = mine.find(
                  (r) => (r.template_id === tpl.id || r.label === tpl.label) && (r.status === "open" || r.status === "acknowledged")
                );
                const isDisabled = sendingId !== null || isCoolingDown || Boolean(activeReq);
                return (
                  <button
                    key={tpl.id}
                    type="button"
                    disabled={isDisabled}
                    onClick={() => void send(tpl)}
                    className="flex min-h-[56px] items-center justify-between gap-2 rounded-2xl border border-subtle bg-surface px-3 text-left text-sm font-semibold text-ink motion-safe:active:scale-[0.98] motion-safe:transition-transform disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <span className="min-w-0 truncate">{sendingId === tpl.id ? t.callWaiterSending : tpl.label}</span>
                    {activeReq ? (
                      <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-xs font-semibold text-subink">
                        {statusLabel(activeReq.status)}
                      </span>
                    ) : null}
                  </button>
                );
              })}
            </div>
          )}
          {error ? (
            <p role="alert" className="mt-3 text-sm font-semibold text-[var(--state-err-fg)]">{error}</p>
          ) : null}
          {mine.length > 0 ? (
            <section aria-label={t.callWaiterMine} className="mt-5">
              <h3 className="text-sm font-bold">{t.callWaiterMine}</h3>
              <ul className="mt-2 divide-y divide-[var(--border-subtle)]">
                {mine.slice(0, 5).map((r) => (
                  <li key={r.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                    <span className="min-w-0 truncate">{r.label}</span>
                    <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-muted px-2.5 py-1 text-xs font-semibold">
                      {r.status === "done" ? <Check size={14} aria-hidden="true" /> : null}
                      {statusLabel(r.status)}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </Sheet>
      ) : null}
    </>
  );
}
