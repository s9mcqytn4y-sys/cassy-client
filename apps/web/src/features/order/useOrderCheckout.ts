"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, apiFetch } from "@/lib/api";
import type { Messages } from "@/lib/messages";
import { normalizeSubmissionText } from "@/lib/sanitize";
import { toOrderItems, type CartAction, type CartLine } from "./cart";
import { checkoutMessageKey, type ActiveOrder } from "./status";
import type { BankCode, PaymentInfo, PayMethod, Quote, SplitShareLink } from "./types";

interface UseOrderCheckoutParams {
  resume: ActiveOrder | null | undefined;
  lines: CartLine[];
  attemptKey: React.MutableRefObject<string | null>;
  quote: Quote | null;
  methods: PayMethod[];
  guestName: string;
  splitCount: number | null;
  t: Messages;
  dispatch: React.Dispatch<CartAction>;
  setCartOpen: (open: boolean) => void;
  setSplitCount: (count: number | null) => void;
  onSessionExpired: () => void;
  onQuoteStale: () => void;
}

export function useOrderCheckout({
  resume,
  lines,
  attemptKey,
  quote,
  methods,
  guestName,
  splitCount,
  t,
  dispatch,
  setCartOpen,
  setSplitCount,
  onSessionExpired,
  onQuoteStale
}: UseOrderCheckoutParams) {
  const [placing, setPlacing] = useState(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [order, setOrder] = useState<ActiveOrder["order"] | null>(null);
  const [payment, setPayment] = useState<PaymentInfo | null>(null);
  const [viewingPayment, setViewingPayment] = useState(false);
  const [shares, setShares] = useState<SplitShareLink[] | null>(null);
  const [picked, setPicked] = useState<PayMethod | null>(null);
  const [bank, setBank] = useState<BankCode>("bca");

  const method = picked && methods.includes(picked) ? picked : methods[0];
  const pendingSplitCount = useRef<number | null>(null);
  const resumed = useRef(false);

  // One key per method: retrying the same method replays, switching makes a new attempt.
  const initiatePayment = useCallback(
    async (orderId: string, key: string, via: PayMethod, viaBank: BankCode = bank) => {
      const { payment: created } = await apiFetch<{ payment: PaymentInfo }>(
        `/api/v1/public/guest/orders/${encodeURIComponent(orderId)}/payments`,
        {
          method: "POST",
          body: {
            idempotency_key: via === "bank_transfer" ? `${key}_${via}_${viaBank}` : `${key}_${via}`,
            method: via,
            ...(via === "bank_transfer" ? { bank: viaBank } : {})
          }
        }
      );
      setPayment({
        ...created,
        method: created.method ?? via,
        bank: created.bank ?? (via === "bank_transfer" ? viaBank : undefined)
      });
      setViewingPayment(true);
    },
    [bank]
  );

  useEffect(() => {
    if (resumed.current || !resume) return;
    resumed.current = true;
    const { order: saved, payment: savedPayment, shares: savedShares } = resume;
    setOrder(saved);
    if (savedShares?.length) {
      setShares(savedShares);
      setViewingPayment(true);
    } else if (savedPayment) {
      setPayment(savedPayment);
      setViewingPayment(true);
    } else {
      initiatePayment(saved.id, saved.payKey, method).catch(() => setCheckoutError(t.checkoutFailed));
    }
    // initiatePayment only depends on bank/setters; the ref limits resume to one run.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resume]);

  const checkout = useCallback(async () => {
    if (!quote) return;
    setPlacing(true);
    setCheckoutError(null);
    // One key per cart state, so a retry after a dropped connection cannot create a second order.
    const key = (attemptKey.current ??= crypto.randomUUID());
    try {
      const created = await apiFetch<{ order: { id: string; order_number?: string }; tracking_token?: string }>(
        "/api/v1/public/guest/orders",
        {
          method: "POST",
          body: {
            idempotency_key: key,
            customer_name: normalizeSubmissionText(guestName, 60) || t.guestDefault,
            expected_quote_fingerprint: quote.quote_fingerprint,
            items: toOrderItems(lines)
          }
        }
      );
      const payKey = `pay_${key}`;
      setOrder({ id: created.order.id, number: created.order.order_number, tracking: created.tracking_token ?? null, payKey });
      pendingSplitCount.current = splitCount;
      dispatch({ type: "clear" });
      setCartOpen(false);
      if (splitCount !== null) {
        // Splitting only works before any QR exists, so it replaces the first payment (W13).
        const { links } = await apiFetch<{ links: SplitShareLink[] }>(
          `/api/v1/public/guest/orders/${encodeURIComponent(created.order.id)}/splits`,
          { method: "POST", body: { strategy: "equal", split_count: splitCount } }
        );
        pendingSplitCount.current = null;
        setShares(links.map(({ split_number, amount, token: shareToken }) => ({ split_number, amount, token: shareToken })));
        setViewingPayment(true);
      } else {
        await initiatePayment(created.order.id, payKey, method);
      }
    } catch (err) {
      const code = err instanceof ApiError ? (err.details as { code?: unknown } | undefined)?.code : undefined;
      setCheckoutError(t[checkoutMessageKey(code)]);
      if (code === "GUEST_SESSION_EXPIRED") onSessionExpired();
      else if (code === "QUOTE_CHANGED" || code === "UNAVAILABLE") onQuoteStale();
    } finally {
      setPlacing(false);
    }
  }, [
    attemptKey,
    dispatch,
    guestName,
    initiatePayment,
    lines,
    method,
    onQuoteStale,
    onSessionExpired,
    quote,
    setCartOpen,
    splitCount,
    t
  ]);

  // A fresh attempt: a new QR after expiry, or another method; the server cancels the open one.
  const newAttempt = useCallback(
    async (via: PayMethod, viaBank: BankCode = bank) => {
      if (!order) return;
      setPicked(via);
      setPlacing(true);
      setCheckoutError(null);
      try {
        await initiatePayment(order.id, `pay_${crypto.randomUUID()}`, via, viaBank);
      } catch (err) {
        const code = err instanceof ApiError ? (err.details as { code?: unknown } | undefined)?.code : undefined;
        setCheckoutError(t[checkoutMessageKey(code)]);
      } finally {
        setPlacing(false);
      }
    },
    [bank, initiatePayment, order, t]
  );

  const resumePayment = useCallback(async () => {
    if (!order) return;
    setCheckoutError(null);
    if (payment || shares) {
      setViewingPayment(true);
      return;
    }
    setPlacing(true);
    try {
      if (pendingSplitCount.current !== null) {
        const { links } = await apiFetch<{ links: SplitShareLink[] }>(
          `/api/v1/public/guest/orders/${encodeURIComponent(order.id)}/splits`,
          { method: "POST", body: { strategy: "equal", split_count: pendingSplitCount.current } }
        );
        pendingSplitCount.current = null;
        setShares(links.map(({ split_number, amount, token: shareToken }) => ({ split_number, amount, token: shareToken })));
        setViewingPayment(true);
      } else {
        await initiatePayment(order.id, order.payKey, method);
      }
    } catch {
      setCheckoutError(t.checkoutFailed);
    } finally {
      setPlacing(false);
    }
  }, [initiatePayment, method, order, payment, shares, t]);

  const startOver = useCallback(() => {
    dispatch({ type: "clear" });
    setOrder(null);
    setPayment(null);
    setShares(null);
    setSplitCount(null);
    setViewingPayment(false);
    setCheckoutError(null);
  }, [dispatch, setSplitCount]);

  return {
    placing,
    checkoutError,
    order,
    payment,
    viewingPayment,
    setViewingPayment,
    shares,
    method,
    setPicked,
    bank,
    setBank,
    checkout,
    newAttempt,
    resumePayment,
    startOver
  };
}
