"use client";

import { CassySpinner } from "./CassySpinner";
import { useLocale } from "./Providers";
import { StateIllustration, type StateIllustrationKind } from "./StateIllustration";

export function DataStateBox({
  state,
  title,
  description,
  illustration,
  onRetry,
  retryLabel
}: {
  state: "loading" | "error" | "empty";
  title: string;
  description: string;
  illustration?: StateIllustrationKind;
  onRetry?: () => void;
  retryLabel?: string;
}) {
  const { t } = useLocale();
  const art: StateIllustrationKind | undefined =
    illustration ?? (state === "error" ? "paymentFailed" : state === "empty" ? "empty" : undefined);
  return (
    <div
      data-state-box={state}
      role={state === "error" ? "alert" : "status"}
      aria-live={state === "error" ? "assertive" : "polite"}
      className="mx-auto max-w-md rounded-2xl border border-subtle bg-surface p-6 text-center"
    >
      {art ? <StateIllustration kind={art} width={152} height={114} className="mb-4" /> : null}
      <div className="flex items-center justify-center gap-2">
        {state === "loading" ? <CassySpinner /> : null}
        <h2 className="font-display text-lg font-bold tracking-tight text-ink">{title}</h2>
      </div>
      <p className="mt-2 text-sm leading-relaxed text-subink">{description}</p>
      {onRetry && state !== "loading" ? (
        <button type="button" onClick={onRetry} className="btn-primary mt-5 min-h-[48px] px-5 py-2 text-sm font-semibold">
          {retryLabel ?? t.retry}
        </button>
      ) : null}
    </div>
  );
}
