"use client";

import React, { useState } from "react";
import { Sheet } from "@/components/Sheet";
import { useLocale } from "@/components/Providers";
import { BankPicker, PaymentMethodPicker } from "./PaymentMethodPicker";
import type { BankCode, PayMethod } from "./types";

/**
 * Systematic modal pop-up for switching payment method (and bank for VA) in a single API attempt.
 */
export function PaymentSwitcherSheet({
  methods,
  currentMethod,
  currentBank,
  retrying,
  onConfirm,
  onClose
}: {
  methods: PayMethod[];
  currentMethod: PayMethod;
  currentBank: BankCode;
  retrying: boolean;
  onConfirm: (method: PayMethod, bank?: BankCode) => void;
  onClose: () => void;
}) {
  const { t } = useLocale();
  const [draftMethod, setDraftMethod] = useState<PayMethod>(currentMethod);
  const [draftBank, setDraftBank] = useState<BankCode>(currentBank);

  return (
    <Sheet title={t.switchPaymentTitle} onClose={onClose}>
      <p className="text-sm text-subink">{t.switchPaymentHint}</p>

      <PaymentMethodPicker
        methods={methods}
        selected={draftMethod}
        disabled={retrying}
        onSelect={setDraftMethod}
      />

      {draftMethod === "bank_transfer" ? (
        <div className="mt-4">
          <BankPicker
            selected={draftBank}
            disabled={retrying}
            label={t.selectBank}
            onSelect={setDraftBank}
          />
        </div>
      ) : null}

      <button
        type="button"
        disabled={retrying}
        onClick={() => {
          onConfirm(draftMethod, draftMethod === "bank_transfer" ? draftBank : undefined);
          onClose();
        }}
        className="btn-primary mt-5 min-h-[52px] w-full px-5 text-base font-bold motion-safe:transition-transform motion-safe:active:scale-[0.98]"
      >
        {t.confirmPaymentMethod}
      </button>
    </Sheet>
  );
}
