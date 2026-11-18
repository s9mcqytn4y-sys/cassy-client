"use client";

export type StateIllustrationKind =
  | "notFound"
  | "offline"
  | "maintenance"
  | "paymentFailed"
  | "sessionExpired"
  | "empty"
  | "error";

const ILLUSTRATION_PATHS: Record<StateIllustrationKind, string> = {
  notFound: "/brand/illustrations/state-404.svg",
  offline: "/brand/illustrations/state-offline.svg",
  maintenance: "/brand/illustrations/state-maintenance.svg",
  paymentFailed: "/brand/illustrations/state-payment-failed.svg",
  sessionExpired: "/brand/illustrations/state-session-expired.svg",
  empty: "/brand/illustrations/state-empty-menu.svg",
  error: "/brand/illustrations/state-payment-failed.svg"
};

/** Brand SVG illustration for empty, offline, maintenance, expired, or failure states. */
export function StateIllustration({
  kind,
  width = 144,
  height = 108,
  className = ""
}: {
  kind: StateIllustrationKind;
  width?: number;
  height?: number;
  className?: string;
}) {
  return (
    <img
      src={ILLUSTRATION_PATHS[kind]}
      alt=""
      width={width}
      height={height}
      aria-hidden="true"
      className={`mx-auto h-auto select-none object-contain ${className}`.trim()}
    />
  );
}
