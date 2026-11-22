"use client";

import React from "react";
import { Landmark } from "lucide-react";
import { useLocale } from "@/components/Providers";
import type { BankCode, PayMethod } from "./types";

export const METHOD_LABEL: Record<PayMethod, string> = {
  qris: "QRIS",
  gopay: "GoPay",
  shopeepay: "ShopeePay",
  bank_transfer: "Transfer Bank (VA)"
};

const METHOD_LOGO: Record<Exclude<PayMethod, "bank_transfer">, string> = {
  qris: "/brand/midtrans/qris.png",
  gopay: "/brand/midtrans/gopay.svg",
  shopeepay: "/brand/midtrans/shopeepay.svg"
};

export const VA_BANKS: { code: BankCode; label: string; logo: string }[] = [
  { code: "bca", label: "BCA", logo: "/brand/midtrans/bca.png" },
  { code: "bni", label: "BNI", logo: "/brand/midtrans/bni.png" },
  { code: "bri", label: "BRI", logo: "/brand/midtrans/bri.png" },
  { code: "mandiri", label: "Mandiri", logo: "/brand/midtrans/mandiri.png" },
  { code: "permata", label: "Permata", logo: "/brand/midtrans/permata_bank.png" }
];

export function bankMeta(code?: string) {
  return VA_BANKS.find((b) => b.code === (code ?? "").toLowerCase());
}

/**
 * Channel logo on a white chip; `bare` drops the chip when the caller already provides one.
 */
export function PaymentChannelLogo({
  method,
  className = "h-5 w-auto",
  bare = false
}: {
  method: PayMethod;
  className?: string;
  bare?: boolean;
}) {
  const mark =
    method === "bank_transfer" ? (
      <Landmark size={18} aria-hidden="true" className="text-[#17211E]" />
    ) : (
      <img src={METHOD_LOGO[method]} alt="" aria-hidden="true" className={`${className} object-contain`} />
    );
  if (bare) return mark;
  return (
    <span className="inline-flex h-7 items-center justify-center rounded bg-white px-1.5 py-0.5 ring-1 ring-black/10">{mark}</span>
  );
}

const METHOD_HINT: Record<PayMethod, "methodHintQris" | "methodHintGopay" | "methodHintShopeepay" | "methodHintBank"> = {
  qris: "methodHintQris",
  gopay: "methodHintGopay",
  shopeepay: "methodHintShopeepay",
  bank_transfer: "methodHintBank"
};

/**
 * Grouped payment selector: QRIS highlighted as primary recommended option, followed by E-Wallets
 * and Virtual Account transfers so choices are scannable at a glance.
 */
export function PaymentMethodPicker({
  methods,
  selected,
  disabled,
  onSelect
}: {
  methods: PayMethod[];
  selected: PayMethod;
  disabled?: boolean;
  onSelect: (method: PayMethod) => void;
}) {
  const { t } = useLocale();
  const qrisFirst = methods.includes("qris") ? (["qris"] as PayMethod[]) : [];
  const wallets = methods.filter((m) => m === "gopay" || m === "shopeepay");
  const bankTransfer = methods.filter((m) => m === "bank_transfer");
  const groups: { title: string; items: PayMethod[]; highlightPrimary?: boolean }[] = [
    ...(qrisFirst.length ? [{ title: t.qrisGroupTitle, items: qrisFirst, highlightPrimary: true }] : []),
    ...(wallets.length ? [{ title: t.walletGroupTitle, items: wallets }] : []),
    ...(bankTransfer.length ? [{ title: t.bankGroupTitle, items: bankTransfer }] : [])
  ];

  return (
    <div className="mt-2 space-y-3">
      {groups.map((group) => (
        <div key={group.title} className="space-y-1.5">
          <p className="text-xs font-bold uppercase tracking-wide text-subink">{group.title}</p>
          <div className="flex flex-col gap-2">
            {group.items.map((m) => {
              const active = selected === m;
              const label = m === "bank_transfer" ? t.bankTransferLabel : METHOD_LABEL[m];
              return (
                <label
                  key={m}
                  className={`flex min-h-[60px] cursor-pointer items-center gap-3 rounded-2xl border bg-surface px-3 py-2.5 motion-safe:transition-[border-color,box-shadow] motion-safe:duration-150 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 ${
                    active ? "border-brand shadow-[0_0_0_1px_var(--color-brand)]" : "border-subtle"
                  } ${disabled ? "pointer-events-none opacity-60" : ""}`}
                >
                  <input
                    type="radio"
                    name="pay-method"
                    value={m}
                    disabled={disabled}
                    checked={active}
                    onChange={() => onSelect(m)}
                    className="sr-only"
                  />
                  <span
                    className={`flex h-9 shrink-0 items-center justify-center rounded-lg bg-white px-2 ring-1 ring-black/10 ${
                      m === "bank_transfer" ? "w-11" : "w-20"
                    }`}
                  >
                    <PaymentChannelLogo
                      method={m}
                      bare
                      className={m === "bank_transfer" ? "h-5 w-5" : "h-5 w-full max-w-[64px]"}
                    />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-1.5">
                      <span className="text-sm font-semibold text-ink">{label}</span>
                      {group.highlightPrimary ? (
                        <span className="rounded-full bg-[var(--state-ok-bg)] px-2 py-0.5 text-[11px] font-bold text-[var(--state-ok-fg)]">
                          {t.qrisRecommended}
                        </span>
                      ) : null}
                    </span>
                    <span className="block text-xs text-subink">{t[METHOD_HINT[m]]}</span>
                  </span>
                  <span
                    aria-hidden="true"
                    className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 ${active ? "border-brand" : "border-strong"}`}
                  >
                    <span className={`h-2.5 w-2.5 rounded-full bg-brand motion-safe:transition-transform ${active ? "scale-100" : "scale-0"}`} />
                  </span>
                </label>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}

export function BankPicker({
  selected,
  disabled,
  label,
  onSelect
}: {
  selected: BankCode;
  disabled?: boolean;
  label: string;
  onSelect: (bank: BankCode) => void;
}) {
  return (
    <fieldset className="mt-3">
      <legend className="text-sm font-bold text-ink">{label}</legend>
      <div className="mt-2 grid grid-cols-3 gap-2 sm:grid-cols-5">
        {VA_BANKS.map((bank) => {
          const active = selected === bank.code;
          return (
            <label
              key={bank.code}
              className={`flex min-h-[56px] cursor-pointer flex-col items-center justify-center gap-1 rounded-xl border bg-surface px-2 py-1.5 text-xs font-bold text-ink motion-safe:transition-[border-color,box-shadow] motion-safe:duration-150 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 ${
                active ? "border-brand shadow-[0_0_0_1px_var(--color-brand)]" : "border-subtle"
              } ${disabled ? "pointer-events-none opacity-60" : ""}`}
            >
              <input
                type="radio"
                name="va-bank"
                value={bank.code}
                disabled={disabled}
                checked={active}
                onChange={() => onSelect(bank.code)}
                className="sr-only"
              />
              <span className="inline-flex h-7 w-full items-center justify-center rounded-md bg-white px-1.5">
                <img src={bank.logo} alt="" aria-hidden="true" className="h-5 w-auto max-w-full object-contain" />
              </span>
              <span>{bank.label}</span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}

export function MidtransTrustFooter() {
  return (
    <div className="mt-6 flex items-center justify-center gap-2 text-xs text-subink">
      <span>Powered by</span>
      <img src="/brand/midtrans/midtrans_logo.svg" alt="Midtrans" className="h-4 w-auto object-contain" />
    </div>
  );
}
