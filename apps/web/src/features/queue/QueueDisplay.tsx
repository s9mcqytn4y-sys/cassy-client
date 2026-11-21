"use client";

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Bell, Clock, Wifi, WifiOff } from "lucide-react";
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
  store_timezone?: string;
  orders: QueueEntry[];
}

function resolveTimezoneLabel(tz: string): string {
  switch (tz) {
    case "Asia/Makassar":
    case "WITA":
      return "WITA";
    case "Asia/Jayapura":
    case "WIT":
      return "WIT";
    case "Asia/Jakarta":
    case "Asia/Pontianak":
    case "WIB":
      return "WIB";
    default:
      return tz;
  }
}

function resolveIanaTimezone(tz?: string): string {
  if (tz && tz.includes("/")) return tz;
  if (tz === "WITA") return "Asia/Makassar";
  if (tz === "WIT") return "Asia/Jayapura";
  if (tz === "WIB") return "Asia/Jakarta";
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Jakarta";
  } catch {
    return "Asia/Jakarta";
  }
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
  const [wsLive, setWsLive] = useState(false);
  const [clockLabel, setClockLabel] = useState("");
  const [draft, setDraft] = useState("");
  const pollRef = useRef<(() => Promise<void>) | null>(null);

  useEffect(() => {
    const tz = resolveIanaTimezone(view?.store_timezone);
    const tzLabel = resolveTimezoneLabel(view?.store_timezone || tz);
    const formatClock = () =>
      new Intl.DateTimeFormat("id-ID", {
        hour: "2-digit",
        minute: "2-digit",
        timeZone: tz
      }).format(new Date()) + ` ${tzLabel}`;
    setClockLabel(formatClock());
    const id = setInterval(() => setClockLabel(formatClock()), 30_000);
    return () => clearInterval(id);
  }, [view?.store_timezone]);

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
          setWsLive(true);
        };
        socket.onmessage = () => void poll();
        socket.onclose = () => {
          setWsLive(false);
          reconnectLater();
        };
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
  const spotlight = readyOrders[0] ?? null;
  const remainingReady = readyOrders.slice(1);

  return (
    <div className="flex min-h-dvh flex-col gap-6 bg-canvas p-4 ipad:p-8">
      <header className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-subtle bg-surface px-5 py-3.5">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-2xl font-bold tracking-tight text-ink ipad:text-4xl">{view.store_name}</h1>
          <span className="hidden rounded-full bg-muted px-3 py-1 text-xs font-semibold text-subink sm:inline-flex">
            {view.orders.length} antrean aktif
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          {clockLabel ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-muted px-3 py-1 text-sm font-semibold tabular-nums text-ink">
              <Clock className="h-4 w-4 text-subink" aria-hidden="true" />
              {clockLabel}
            </span>
          ) : null}
          {error ? (
            <p role="status" className="inline-flex items-center gap-1.5 rounded-full bg-[var(--state-warn-bg)] px-3 py-1 text-sm font-semibold text-[var(--state-warn-fg)]">
              <WifiOff className="h-4 w-4" aria-hidden="true" />
              {error}
            </p>
          ) : (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-[var(--state-ok-bg)] px-3 py-1 text-sm font-semibold text-[var(--state-ok-fg)]">
              <Wifi className="h-4 w-4" aria-hidden="true" />
              {wsLive ? "Live Terhubung" : "Sinkronisasi"}
            </span>
          )}
        </div>
      </header>
      <div className="grid flex-1 gap-4 md:grid-cols-12 ipad:gap-8">
        <div className="md:col-span-5">
          <QueueColumn title={t.queuePreparing} numbers={preparing} emptyText={t.queueNonePreparing} tone="muted" />
        </div>
        <div className="flex flex-col gap-4 md:col-span-7">
          {spotlight ? (
            <section
              aria-label="Baru dipanggil"
              aria-live="assertive"
              className="flex flex-col items-center justify-center gap-2 rounded-3xl border-2 border-[var(--brand-accent)] bg-[var(--brand-primary)] px-6 py-8 text-center text-[var(--brand-on-primary)]"
            >
              <span className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3.5 py-1 text-xs font-bold uppercase tracking-wider">
                <Bell className="h-4 w-4 text-[var(--brand-accent)]" aria-hidden="true" />
                Siap Diambil Sekarang
              </span>
              <p className="font-display text-6xl font-extrabold tracking-tight tabular-nums ipad:text-8xl">
                {spotlight.queue_number}
              </p>
              <p className="text-sm opacity-90 ipad:text-base">Silakan ambil pesanan Anda di meja pengambilan</p>
            </section>
          ) : null}
          <QueueColumn
            title={t.queueReady}
            numbers={readyOrders}
            emptyText={t.queueNoneReady}
            tone="ok"
          />
        </div>
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
