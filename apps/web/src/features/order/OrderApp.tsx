"use client";

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { animate } from "animejs";
import { ShoppingBag } from "lucide-react";
import { MenuSkeleton } from "@/components/MenuSkeleton";
import { Notices } from "@/components/Toast";
import { useLocale } from "@/components/Providers";
import { DataStateBox } from "@/components/Shell";
import { apiFetch } from "@/lib/api";
import { brandVariables } from "@/lib/brand";
import { formatIdr } from "@/lib/format";
import { CartSheet } from "./CartSheet";
import { CategoryNav, sectionId } from "./CategoryNav";
import { GuestOrdersSheet } from "./GuestOrdersSheet";
import { menuSections } from "./menu";
import { PaymentView } from "./PaymentView";
import { defaultVariant, ProductCard, ProductRow } from "./MenuItems";
import { ProductOptionsSheet } from "./ProductOptionsSheet";
import { moneyTaken, orderPhase, writeActiveOrder } from "./status";
import { isFeatureEnabled, type CatalogProduct, type GuestOrderSummary, type PayMethod, type ServiceMode } from "./types";
import { SplitView } from "./SplitView";
import { CallWaiter } from "./CallWaiter";
import { StoreHeader } from "./StoreHeader";
import { StoreClosedBanner } from "./StoreClosedBanner";
import { ActiveOrderMonitorBanner } from "./ActiveOrderMonitorBanner";
import { useCartPersistence, useOnlineStatus, useOrderBell } from "./hooks";
import { useOrderCheckout } from "./useOrderCheckout";
import { useOrderData, useOrderProgress, useQuote } from "./useOrderData";

export function OrderApp({ token }: { token: string }) {
  const { t, locale, setHidePoweredBy } = useLocale();
  const { state, data, reload } = useOrderData(token);
  const { lines, dispatch, attemptKey } = useCartPersistence(token);
  const [optionsFor, setOptionsFor] = useState<CatalogProduct | null>(null);
  const [cartOpen, setCartOpen] = useState(false);
  const [ordersOpen, setOrdersOpen] = useState(false);
  const [guestOrders, setGuestOrders] = useState<GuestOrderSummary[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [cancellingOrder, setCancellingOrder] = useState(false);
  const [guestName, setGuestName] = useState("");
  const [splitCount, setSplitCount] = useState<number | null>(null);

  useEffect(() => {
    if (data?.orders) setGuestOrders(data.orders);
  }, [data?.orders]);

  const refreshGuestOrders = useCallback(async () => {
    setOrdersLoading(true);
    try {
      const res = await apiFetch<{ orders?: GuestOrderSummary[] }>("/api/v1/public/guest/orders");
      setGuestOrders(res.orders ?? []);
    } catch {
      // Keep the last known list when offline or session expired.
    } finally {
      setOrdersLoading(false);
    }
  }, []);

  const openGuestOrders = useCallback(() => {
    setOrdersOpen(true);
    void refreshGuestOrders();
  }, [refreshGuestOrders]);

  const { quote, failed, loading, refresh } = useQuote(data?.table.store_slug, lines);
  const methods: PayMethod[] = data?.table.payment_methods?.length ? data.table.payment_methods : ["qris"];

  const {
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
  } = useOrderCheckout({
    resume: data?.resume,
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
    onSessionExpired: () => void reload(),
    onQuoteStale: () => void refresh()
  });

  const { tracked, refresh: refreshTrack } = useOrderProgress(order?.tracking ?? null);
  const phase = orderPhase(tracked);

  const [actionToast, setActionToast] = useState<string | null>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const notify = useCallback((message: string) => {
    setActionToast(message);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setActionToast(null), 2600);
  }, []);

  useEffect(() => () => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
  }, []);

  useEffect(() => {
    if (!viewingPayment) return;
    setActionToast(null);
    if (typeof window !== "undefined") window.scrollTo(0, 0);
  }, [viewingPayment]);

  useEffect(() => {
    // A served order is done; a cancelled one stays so a guest who paid still sees where to ask for the refund.
    const served = tracked?.status === "served" || tracked?.status === "completed";
    if (state === "ready") writeActiveOrder(token, order && !served ? { order, payment, shares: shares ?? undefined } : null);
  }, [state, token, order, payment, shares, tracked?.status]);

  // The moment the guest waits for after paying: ready at the bar (pickup) or delivered to the table.
  const serviceMode: ServiceMode = data?.table.service_mode ?? "pickup";
  const arrived =
    serviceMode === "table" ? tracked?.status === "served" || tracked?.status === "completed" : tracked?.status === "ready";
  const arrivedText = serviceMode === "table" ? t.toastDelivered : t.toastReady;

  useEffect(() => {
    if (!data) return;
    const base = `${data.table.store_name} | ${data.table.label || `${t.table} ${data.table.table_number}`}`;
    // A guest on another tab sees the news in the tab bar.
    const apply = () => {
      document.title = arrived && document.hidden ? `${arrivedText} | ${base}` : base;
    };
    apply();
    document.addEventListener("visibilitychange", apply);
    return () => document.removeEventListener("visibilitychange", apply);
  }, [data, t.table, arrived, arrivedText]);

  const bellEnabled = isFeatureEnabled(data?.table.features, "guest_bell_sound_enabled");
  const { muted, toggleSound, toast } = useOrderBell(bellEnabled && arrived, arrivedText, serviceMode);
  const online = useOnlineStatus(() => void refresh());
  const notices = <Notices toast={cartOpen || optionsFor || ordersOpen ? null : (actionToast ?? toast)} offline={!online} offlineText={t.offline} />;

  const sections = useMemo(() => menuSections(data?.categories ?? [], data?.products ?? [], t.otherMenu), [data, t.otherMenu]);
  // About the first screen at 360 to 390 px: two rows of the two-column grid.
  const eagerIds = useMemo(() => new Set(sections.flatMap((s) => s.products).slice(0, 4).map((p) => p.id)), [sections]);
  const multiVariant = useMemo(() => new Set((data?.products ?? []).filter((p) => p.variants.length > 1).map((p) => p.id)), [data]);
  const itemCount = lines.reduce((sum, l) => sum + l.quantity, 0);

  const prevItemCount = useRef(0);
  useEffect(() => {
    const increased = itemCount > prevItemCount.current;
    prevItemCount.current = itemCount;
    if (!increased || typeof window === "undefined" || !window.matchMedia("(prefers-reduced-motion: no-preference)").matches) return;
    animate("[data-cart-cta]", {
      scale: [0.97, 1],
      duration: 150, // --motion-feedback (tokens.json motion.feedbackMs)
      ease: "outQuad"
    });
  }, [itemCount]);

  useEffect(() => {
    setHidePoweredBy(Boolean(data?.cms.hide_powered_by_badge));
    return () => setHidePoweredBy(false);
  }, [data?.cms.hide_powered_by_badge, setHidePoweredBy]);

  const handleSelfCancelOrder = useCallback(async () => {
    if (!order || cancellingOrder) return;
    setCancellingOrder(true);
    try {
      await apiFetch(`/api/v1/public/guest/orders/${encodeURIComponent(order.id)}/cancel`, {
        method: "POST",
        body: {}
      });
      startOver();
      notify(t.orderCancelledToast);
      void refreshGuestOrders();
    } catch {
      notify(t.cancelOrderFailed);
    } finally {
      setCancellingOrder(false);
    }
  }, [order, cancellingOrder, startOver, notify, t.orderCancelledToast, t.cancelOrderFailed, refreshGuestOrders]);

  if (state === "loading") {
    return (
      <div className="py-4">
        <p className="sr-only" role="status">{t.loadingTitle}</p>
        <MenuSkeleton />
      </div>
    );
  }
  if (state === "missing") return <div className="p-5"><DataStateBox state="error" illustration="notFound" title={t.tableMissingTitle} description={t.tableMissingBody} /></div>;
  if (state === "maintenance") {
    return <div className="p-5"><DataStateBox state="error" illustration="maintenance" title={t.maintenanceTitle} description={t.maintenanceBody} onRetry={reload} /></div>;
  }
  if (state === "failed" || !data) {
    return <div className="p-5"><DataStateBox state="error" illustration={online ? "paymentFailed" : "offline"} title={t.loadFailedTitle} description={t.loadFailedBody} onRetry={reload} /></div>;
  }

  const { table, cms } = data;
  const announcement = locale === "en-US" ? cms.announcement_banner_en : cms.announcement_banner_id;
  const tagline = locale === "en-US" ? cms.hero_subtitle_en : cms.hero_subtitle_id;
  const brand = brandVariables(cms.primary_hex) as React.CSSProperties;

  if (shares && order && viewingPayment && phase === "awaiting") {
    return (
      <div style={brand}>
        {notices}
        <SplitView orderId={order.id} shares={shares} onBack={() => setViewingPayment(false)} />
      </div>
    );
  }

  if ((payment || shares) && order && viewingPayment) {
    return (
      <div style={brand}>
        {notices}
        <PaymentView
          // A split order reaches here only once paid or cancelled, where PaymentView does not read the payment.
          payment={payment ?? { id: "split", amount: 0 }}
          phase={phase}
          moneyTaken={moneyTaken(tracked)}
          orderId={order.id}
          orderNumber={tracked?.order_number ?? order.number}
          storeName={table.store_name}
          status={tracked?.status}
          estimatedPrepMinutes={tracked?.estimated_prep_minutes}
          retrying={placing}
          error={checkoutError}
          methods={methods}
          bank={bank}
          onNewQr={() => void newAttempt(payment?.method ?? method)}
          onSwitch={(via, viaBank) => void newAttempt(via, viaBank)}
          onSelectBank={setBank}
          onNotify={notify}
          onCheckStatus={refreshTrack}
          serviceMode={serviceMode}
          muted={muted}
          bellEnabled={bellEnabled}
          emailReceiptEnabled={isFeatureEnabled(table.features, "guest_email_receipt_enabled")}
          canSelfCancel={phase === "awaiting" && isFeatureEnabled(table.features, "guest_self_cancel_enabled")}
          cancelling={cancellingOrder}
          onCancelOrder={() => void handleSelfCancelOrder()}
          onToggleSound={toggleSound}
          onBack={() => setViewingPayment(false)}
          onOrderAgain={startOver}
        />
      </div>
    );
  }

  return (
    <div style={brand} className="mx-auto flex min-h-dvh max-w-lg flex-col md:max-w-3xl lg:max-w-4xl">
      {notices}
      {cms.hero_banner_url ? (
        <img
          src={cms.hero_banner_url}
          alt=""
          width={1280}
          height={480}
          fetchPriority="high"
          className="aspect-[8/3] w-full object-cover md:rounded-b-2xl"
        />
      ) : null}
      <StoreHeader
        storeName={table.store_name}
        tableLabel={table.label || `${t.table} ${table.table_number}`}
        tagline={tagline}
        announcement={announcement}
        orderCount={Math.max(guestOrders.length, order ? 1 : 0)}
        onOpenOrders={openGuestOrders}
        actionSlot={table.call_waiter_enabled && table.ordering_open ? <CallWaiter onNotify={notify} /> : null}
      />

      {order && !viewingPayment ? (
        <div className="mt-1">
          <ActiveOrderMonitorBanner
            orderNumber={tracked?.order_number ?? order.number}
            amount={payment?.amount}
            expiresAt={tracked?.expires_at ?? payment?.expires_at}
            status={tracked?.status}
            paymentStatus={tracked?.payment_status}
            busy={placing}
            onOpenPayment={resumePayment}
            onRenewQr={() => void newAttempt(payment?.method ?? method)}
          />
          {checkoutError ? <p role="alert" className="mt-2 px-5 text-sm font-semibold text-[var(--state-err-fg)] md:px-6">{checkoutError}</p> : null}
        </div>
      ) : null}

      {!table.ordering_open ? (
        <div className="mt-4 px-5 md:px-6">
          <StoreClosedBanner closedReason={table.closed_reason} operatingHours={table.operating_hours} />
        </div>
      ) : null}

      {sections.length > 1 ? <CategoryNav sections={sections} /> : null}

      <div className={`px-5 md:px-6 ${itemCount > 0 ? "pb-24" : "pb-8"}`}>
        {sections.length === 0 ? (
          <div className="mt-4"><DataStateBox state="empty" illustration="empty" title={t.emptyTitle} description={t.emptyBody} /></div>
        ) : (
          sections.map((section) => (
            <section key={section.id} aria-labelledby={sectionId(section.id)} className="cassy-menu-section mt-7">
              <h2 id={sectionId(section.id)} className="scroll-mt-20 text-xl font-extrabold tracking-tight">
                {section.name}
              </h2>
              {(() => {
                const withPhotos = section.products.some((p) => p.image_url);
                const Item = withPhotos ? ProductCard : ProductRow;
                return (
                  <ul className={withPhotos ? "mt-3 grid grid-cols-2 gap-3 md:grid-cols-3 md:gap-4" : "mt-1 divide-y divide-[var(--border-subtle)]"}>
                    {section.products.map((product) => (
                      <Item
                        key={product.id}
                        product={product}
                        canOrder={table.ordering_open}
                        eager={eagerIds.has(product.id)}
                        onOpen={() => setOptionsFor(product)}
                        onQuickAdd={() => {
                          dispatch({
                            type: "add",
                            line: { productId: product.id, variantId: defaultVariant(product)?.id, name: product.name, options: [], quantity: 1, notes: "" }
                          });
                          notify(t.addedToCart);
                        }}
                      />
                    ))}
                  </ul>
                );
              })()}
            </section>
          ))
        )}
      </div>

      {itemCount > 0 && !cartOpen && !optionsFor && !ordersOpen ? (
        // Floating cart CTA dock in thumb reach; hidden while a modal sheet is open so it never peeks under the backdrop.
        <div data-cart-bar className="sticky bottom-0 z-20 mt-auto border-t border-subtle bg-surface/95 px-4 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-2.5 backdrop-blur supports-[backdrop-filter]:bg-surface/85 md:px-6">
          <button type="button" data-cart-cta onClick={() => setCartOpen(true)} className="btn-primary mx-auto flex min-h-[56px] w-full cursor-pointer items-center justify-between rounded-xl px-5 text-base font-bold shadow-md md:max-w-md">
            <span className="flex items-center gap-2.5">
              <ShoppingBag size={20} aria-hidden="true" />
              {t.viewCart} ({itemCount} {t.items})
            </span>
            <span className="tabular-nums">{quote ? formatIdr(quote.total_amount) : ""}</span>
          </button>
        </div>
      ) : null}

      {optionsFor ? (
        <ProductOptionsSheet
          product={optionsFor}
          canOrder={table.ordering_open}
          catalogProducts={data.products}
          onClose={() => setOptionsFor(null)}
          onAdd={(line) => {
            dispatch({ type: "add", line });
            setOptionsFor(null);
            notify(t.addedToCart);
          }}
        />
      ) : null}

      {ordersOpen ? (
        <GuestOrdersSheet
          orders={guestOrders}
          activeOrderId={order?.id}
          loading={ordersLoading}
          onRefresh={() => void refreshGuestOrders()}
          onOpenActive={resumePayment}
          onClose={() => setOrdersOpen(false)}
        />
      ) : null}

      {cartOpen ? (
        <CartSheet
          lines={lines}
          multiVariant={multiVariant}
          dispatch={dispatch}
          quote={quote}
          quoteLoading={loading}
          quoteFailed={failed}
          onRetryQuote={refresh}
          guestName={guestName}
          onGuestName={setGuestName}
          placing={placing}
          error={checkoutError}
          canOrder={table.ordering_open}
          methods={methods}
          method={method}
          onMethod={setPicked}
          bank={bank}
          onBank={setBank}
          // Shares are paid by QRIS only, so the outlet must have QRIS on as well.
          splitEnabled={table.split_enabled === true && methods.includes("qris") && isFeatureEnabled(table.features, "guest_split_equal_enabled")}
          splitCount={splitCount}
          onSplitCount={setSplitCount}
          catalogProducts={data.products}
          onQuickAddPairing={
            isFeatureEnabled(table.features, "cart_pairing_enabled")
              ? (product) => {
                  dispatch({
                    type: "add",
                    line: { productId: product.id, variantId: defaultVariant(product)?.id, name: product.name, options: [], quantity: 1, notes: "" }
                  });
                }
              : undefined
          }
          onPay={checkout}
          onClose={() => setCartOpen(false)}
        />
      ) : null}
    </div>
  );
}
