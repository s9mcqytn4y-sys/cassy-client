"use client";

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useLocale } from "@/components/Providers";
import { DataStateBox } from "@/components/Shell";
import { ApiError, apiFetch } from "@/lib/api";
import { reconnectDelay } from "@/features/order/status";

interface QueueEntry {
  queue_number: string;
  status: string;
}

interface QueueView {
  store_name: string;
  orders: QueueEntry[];
}

const TOKEN_KEY = "cassy.displayToken";
const POLL_MS = 5_000;

/** The owner shares "/antrean#t=<token>"; the fragment never reaches the server or its logs. */
function takeTokenFromHash(): string | null {
  const match = /(?:^#|&)t=([^&]+)/.exec(window.location.hash);
  if (!match) return null;
  history.replaceState(null, "", window.location.pathname);
  return decodeURIComponent(match[1]);
}

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function storeToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Private mode: the token lives for this page only.
  }
}

/**
 * Queue TV screen: queue numbers being prepared and ready, from a display token the owner created.
 * Polls every few seconds; a socket event on channel "queue" refreshes sooner. Keeps the screen awake.
 */
export function QueueDisplay() {
  const { t } = useLocale();
  const [token, setToken] = useState<string | null>(null);
  const [ready, setReady] = useState(false);
  const [view, setView] = useState<QueueView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const pollRef = useRef<(() => Promise<void>) | null>(null);

  useEffect(() => {
    const fromHash = takeTokenFromHash();
    if (fromHash) storeToken(fromHash);
    setToken(fromHash ?? readStoredToken());
    setReady(true);
  }, []);

  useEffect(() => {
    if (!token) return;
    const auth = { Authorization: `Display ${token}` };
    let stopped = false;
    let attempt = 0;
    let socket: WebSocket | null = null;
    let retry: ReturnType<typeof setTimeout> | undefined;

    const poll = async () => {
      try {
        const result = await apiFetch<QueueView>("/api/v1/public/queue", { headers: auth });
        if (stopped) return;
        setView(result);
        setError(null);
      } catch (err) {
        if (stopped) return;
        if (err instanceof ApiError && err.status === 401) {
          stop();
          storeToken(null);
          setToken(null);
          setView(null);
          setError(err.message);
          return;
        }
        // Keep the last board on screen; the next poll retries.
        setError(t.queueOffline);
      }
    };
    const reconnectLater = () => {
      if (!stopped) retry = setTimeout(() => void connect(), reconnectDelay(attempt++));
    };
    const connect = async () => {
      try {
        const { ticket } = await apiFetch<{ ticket: string }>("/api/v1/public/queue/ws-ticket", { method: "POST", body: {}, headers: auth });
        if (stopped) return;
        const scheme = window.location.protocol === "https:" ? "wss:" : "ws:";
        socket = new WebSocket(`${scheme}//${window.location.host}/ws?ticket=${encodeURIComponent(ticket)}`);
        socket.onopen = () => {
          attempt = 0;
        };
        socket.onmessage = () => void poll();
        socket.onclose = reconnectLater;
      } catch {
        reconnectLater();
      }
    };
    const timer = setInterval(() => void poll(), POLL_MS);
    function stop() {
      stopped = true;
      pollRef.current = null;
      clearInterval(timer);
      clearTimeout(retry);
      socket?.close();
    }
    pollRef.current = poll;
    void poll();
    void connect();
    return stop;
  }, [token, t.queueOffline]);

  // A TV must not dim or sleep; the lock drops when the tab hides, so take it again on return.
  useEffect(() => {
    if (!token || !("wakeLock" in navigator)) return;
    let lock: WakeLockSentinel | null = null;
    const acquire = async () => {
      try {
        lock = await navigator.wakeLock.request("screen");
      } catch {
        // Not allowed here (battery saver, no user gesture): the screen may sleep.
      }
    };
    const onVisible = () => {
      if (!document.hidden) void acquire();
    };
    void acquire();
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      document.removeEventListener("visibilitychange", onVisible);
      void lock?.release();
    };
  }, [token]);

  const submit = useCallback(
    (e: FormEvent) => {
      e.preventDefault();
      const value = draft.trim();
      if (!value) return;
      storeToken(value);
      setError(null);
      setDraft("");
      setToken(value);
    },
    [draft]
  );

  if (!ready) return null;

  if (!token) {
    return (
      <div className="mx-auto flex min-h-dvh max-w-lg flex-col justify-center gap-6 px-4 py-10">
        <div>
          <h1 className="font-display text-2xl font-bold tracking-tight text-ink">{t.queueSetupTitle}</h1>
          <p className="mt-2 text-sm leading-relaxed text-subink">{t.queueSetupBody}</p>
        </div>
        {error ? (
          <p role="alert" className="rounded-xl bg-[var(--state-err-bg)] px-4 py-3 text-sm text-[var(--state-err-fg)]">
            {error}
          </p>
        ) : null}
        <form onSubmit={submit} className="flex flex-col gap-3">
          <label htmlFor="display-token" className="text-sm font-semibold text-ink">
            {t.queueTokenLabel}
          </label>
          <input
            id="display-token"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            autoComplete="off"
            spellCheck={false}
            className="min-h-[48px] rounded-xl border border-strong bg-surface px-4 text-base text-ink"
          />
          <button type="submit" disabled={!draft.trim()} className="btn-primary min-h-[48px] px-5 text-sm font-semibold">
            {t.queueTokenSubmit}
          </button>
        </form>
      </div>
    );
  }

  if (!view) {
    return (
      <div className="flex min-h-dvh items-center justify-center px-4">
        <DataStateBox
          state={error ? "error" : "loading"}
          title={error ? t.queueOfflineTitle : t.queueLoading}
          description={error ?? t.queueLoadingBody}
          onRetry={error ? () => void pollRef.current?.() : undefined}
        />
      </div>
    );
  }

  const preparing = view.orders.filter((o) => o.status !== "ready");
  const readyOrders = view.orders.filter((o) => o.status === "ready");

  return (
    <div className="flex min-h-dvh flex-col gap-6 bg-canvas p-4 ipad:p-8">
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <h1 className="font-display text-2xl font-bold tracking-tight text-ink ipad:text-4xl">{view.store_name}</h1>
        {error ? (
          <p role="status" className="rounded-full bg-[var(--state-warn-bg)] px-3 py-1 text-sm font-semibold text-[var(--state-warn-fg)]">
            {error}
          </p>
        ) : null}
      </header>
      <div className="grid flex-1 gap-4 md:grid-cols-2 ipad:gap-8">
        <QueueColumn title={t.queuePreparing} numbers={preparing} emptyText={t.queueNonePreparing} tone="muted" />
        <QueueColumn title={t.queueReady} numbers={readyOrders} emptyText={t.queueNoneReady} tone="ok" />
      </div>
    </div>
  );
}

function QueueColumn({ title, numbers, emptyText, tone }: { title: string; numbers: QueueEntry[]; emptyText: string; tone: "muted" | "ok" }) {
  const cell =
    tone === "ok"
      ? "bg-[var(--state-ok-bg)] text-[var(--state-ok-fg)] border-[var(--state-ok-fg)]"
      : "bg-surface text-ink border-subtle";
  return (
    <section aria-label={title} className="flex flex-col gap-4 rounded-3xl border border-subtle bg-surface p-4 ipad:p-6">
      <h2 className="font-display text-xl font-bold text-ink ipad:text-3xl">{title}</h2>
      {numbers.length === 0 ? (
        <p className="text-base text-subink ipad:text-xl">{emptyText}</p>
      ) : (
        <ul aria-live="polite" className="grid grid-cols-2 gap-3 sm:grid-cols-3 ipad:gap-4">
          {numbers.map((o) => (
            <li
              key={o.queue_number}
              className={`rounded-2xl border-2 px-2 py-4 text-center font-display text-3xl font-bold tabular-nums ipad:text-5xl ${cell}`}
            >
              {o.queue_number}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
