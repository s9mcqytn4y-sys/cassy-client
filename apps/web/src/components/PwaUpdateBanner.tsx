"use client";

import { useLocale } from "@/components/Providers";
import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";

/**
 * Registers `/sw.js` and surfaces a non-blocking update banner whenever a new
 * Service Worker waits to activate. Reloading preserves guest cart state in localStorage.
 */
export function PwaUpdateBanner() {
  const { t } = useLocale();
  const [waitingWorker, setWaitingWorker] = useState<ServiceWorker | null>(null);

  useEffect(() => {
    if (typeof window === "undefined" || !("serviceWorker" in navigator)) return;
    let hadInitialController = Boolean(navigator.serviceWorker.controller);
    let refreshing = false;

    const onControllerChange = () => {
      if (!hadInitialController) {
        hadInitialController = true;
        return;
      }
      if (refreshing) return;
      refreshing = true;
      window.location.reload();
    };
    navigator.serviceWorker.addEventListener("controllerchange", onControllerChange);

    navigator.serviceWorker
      .register("/sw.js")
      .then((reg) => {
        if (reg.waiting) {
          setWaitingWorker(reg.waiting);
        }
        reg.addEventListener("updatefound", () => {
          const installing = reg.installing;
          if (!installing) return;
          installing.addEventListener("statechange", () => {
            if (installing.state === "installed" && navigator.serviceWorker.controller) {
              setWaitingWorker(installing);
            }
          });
        });
      })
      .catch(() => {
        // Ignore SW registration failure in private/unsupported browsers.
      });

    return () => {
      navigator.serviceWorker.removeEventListener("controllerchange", onControllerChange);
    };
  }, []);

  if (!waitingWorker) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed bottom-4 left-1/2 z-50 flex w-[calc(100%-2rem)] max-w-md -translate-x-1/2 items-center justify-between gap-3 rounded-2xl border border-strong bg-[var(--brand-primary)] px-4 py-3 text-sm text-[var(--brand-on-primary)] shadow-lg"
    >
      <span className="font-medium">{t.pwaUpdateAvailable}</span>
      <button
        type="button"
        onClick={() => waitingWorker.postMessage({ type: "SKIP_WAITING" })}
        className="inline-flex min-h-[44px] shrink-0 items-center gap-1.5 rounded-xl bg-[var(--brand-accent)] px-3.5 py-2 text-xs font-bold text-[var(--brand-on-accent)]"
      >
        <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
        {t.pwaUpdateAction}
      </button>
    </div>
  );
}
