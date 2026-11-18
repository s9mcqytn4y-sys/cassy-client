"use client";

import { BellRing, Check } from "lucide-react";
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
  label: string;
  status: "open" | "acknowledged" | "done" | "cancelled";
  created_at: string;
}

const NOTE_MAX = 80;

/**
 * "Panggil Pelayan" (feature flag call_waiter). One tap on a template sends it; the backend returns the
 * live request when the same one is already open at this table and rate-limits each guest session.
 */
export function CallWaiter({ onNotify }: { onNotify: (message: string) => void }) {
  const { t } = useLocale();
  const [open, setOpen] = useState(false);
  const [templates, setTemplates] = useState<Template[] | null>(null);
  const [mine, setMine] = useState<ServiceRequest[]>([]);
  const [note, setNote] = useState("");
  const [sendingId, setSendingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [tpl, req] = await Promise.all([
        apiFetch<{ templates?: Template[] }>("/api/v1/public/guest/service-request-templates"),
        apiFetch<{ service_requests?: ServiceRequest[] }>("/api/v1/public/guest/service-requests")
      ]);
      setTemplates(tpl.templates ?? []);
      setMine(req.service_requests ?? []);
    } catch (err) {
      setTemplates([]);
      setError(err instanceof ApiError && err.status !== 0 ? err.message : t.callWaiterFailed);
    }
  }, [t.callWaiterFailed]);

  useEffect(() => {
    if (open) void load();
  }, [open, load]);

  const send = async (tpl: Template) => {
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
      setOpen(false);
      onNotify(t.callWaiterSent);
    } catch (err) {
      setError(err instanceof ApiError && err.status !== 0 ? err.message : t.callWaiterFailed);
    } finally {
      setSendingId(null);
    }
  };

  const statusLabel = (s: ServiceRequest["status"]) =>
    s === "acknowledged" ? t.callWaiterOnTheWay : s === "done" ? t.callWaiterDone : s === "open" ? t.callWaiterWaiting : t.callWaiterCancelled;

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="btn-secondary inline-flex min-h-[44px] items-center gap-2 rounded-full px-4 text-sm font-semibold motion-safe:active:scale-[0.97] motion-safe:transition-transform"
      >
        <BellRing size={18} aria-hidden="true" />
        {t.callWaiter}
      </button>
      {open ? (
        <Sheet title={t.callWaiterTitle} onClose={() => setOpen(false)}>
          <p className="text-sm text-subink">{t.callWaiterHint}</p>
          <label className="mt-4 block text-sm font-semibold" htmlFor="call-waiter-note">
            {t.callWaiterNote}
          </label>
          <input
            id="call-waiter-note"
            value={note}
            maxLength={NOTE_MAX}
            onChange={(e) => setNote(e.target.value)}
            className="mt-1.5 min-h-[48px] w-full rounded-xl border border-subtle bg-surface px-3.5 text-base"
          />
          {templates === null ? (
            <p className="mt-4 text-sm text-subink" aria-live="polite">{t.loadingTitle}</p>
          ) : templates.length === 0 && !error ? (
            <p className="mt-4 text-sm text-subink">{t.callWaiterEmpty}</p>
          ) : (
            <div className="mt-4 grid grid-cols-2 gap-2">
              {templates.map((tpl) => (
                <button
                  key={tpl.id}
                  type="button"
                  disabled={sendingId !== null}
                  onClick={() => void send(tpl)}
                  className="min-h-[56px] rounded-2xl border border-subtle bg-surface px-3 text-left text-sm font-semibold text-ink motion-safe:active:scale-[0.98] motion-safe:transition-transform disabled:opacity-60"
                >
                  {sendingId === tpl.id ? t.callWaiterSending : tpl.label}
                </button>
              ))}
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
