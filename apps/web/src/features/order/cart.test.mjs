// Run with: node --experimental-strip-types src/features/order/cart.test.mjs
import assert from "node:assert/strict";
import { cartReducer, optionNames, toOrderItems } from "./cart.ts";
import { checkoutMessageKey, isFinal, moneyTaken, orderPhase, progressStep, readActiveOrder, reconnectDelay, safeDeeplink, writeActiveOrder } from "./status.ts";
import { menuSections, pickSmartPairings } from "./menu.ts";

const latte = { productId: "p1", variantId: "v1", name: "Latte", options: [], quantity: 1, notes: "" };
const oat = { ...latte, options: [{ optionId: "oat", subOptionIds: ["b", "a"] }] };
const oatReordered = { ...latte, options: [{ optionId: "oat", subOptionIds: ["a", "b"] }] };

let cart = cartReducer([], { type: "add", line: latte });
cart = cartReducer(cart, { type: "add", line: latte });
assert.equal(cart.length, 1);
assert.equal(cart[0].quantity, 2);

cart = cartReducer(cart, { type: "add", line: oat });
cart = cartReducer(cart, { type: "add", line: oatReordered });
assert.equal(cart.length, 2, "same selection in another order is one line");
assert.equal(cart[1].quantity, 2);

cart = cartReducer(cart, { type: "setQuantity", key: cart[0].key, quantity: 0 });
assert.equal(cart.length, 1, "quantity zero removes the line");

assert.deepEqual(toOrderItems(cart)[0].modifiers, [
  { option_id: "oat", quantity: 1, sub_addons: [{ option_id: "b", quantity: 1 }, { option_id: "a", quantity: 1 }] }
]);
assert.deepEqual(optionNames("[]"), [], "an empty snapshot shows nothing");
assert.deepEqual(optionNames(undefined), []);
assert.deepEqual(optionNames("not json"), []);
assert.deepEqual(
  optionNames('[{"option_name":"Oat","sub_addons":[{"option_name":"Extra"}]},{"option_name":"Less sugar"}]'),
  ["Oat", "Extra", "Less sugar"]
);
assert.equal(orderPhase(null), "awaiting");
assert.equal(orderPhase({ status: "pending_payment", payment_status: "failed" }), "awaiting", "a failed QR can be retried");
assert.equal(orderPhase({ status: "pending_payment", payment_status: "partially_paid" }), "awaiting");
assert.equal(orderPhase({ status: "queued", payment_status: "paid" }), "paid");
assert.equal(orderPhase({ status: "served", payment_status: "partially_refunded" }), "paid");
assert.equal(orderPhase({ status: "cancelled", payment_status: "expired" }), "cancelled");
assert.equal(orderPhase({ status: "cancelled", payment_status: "paid" }), "cancelled", "cancellation beats payment");
assert.equal(orderPhase({ status: "served", payment_status: "refunded" }), "refunded");
assert.equal(orderPhase({ status: "served", payment_status: "chargeback" }), "refunded");
assert.equal(moneyTaken({ status: "cancelled", payment_status: "paid" }), true);
assert.equal(moneyTaken({ status: "cancelled", payment_status: "expired" }), false);

assert.equal(checkoutMessageKey("QUOTE_CHANGED"), "priceChanged");
assert.equal(checkoutMessageKey("UNAVAILABLE"), "itemUnavailable");
assert.equal(checkoutMessageKey("GUEST_SESSION_EXPIRED"), "sessionExpired");
assert.equal(checkoutMessageKey("SOMETHING_NEW"), "checkoutFailed");
assert.equal(checkoutMessageKey(undefined), "checkoutFailed");

assert.equal(isFinal({ status: "served", payment_status: "paid" }), true);
assert.equal(isFinal({ status: "cancelled", payment_status: "expired" }), true);
assert.equal(isFinal({ status: "queued", payment_status: "refunded" }), true);
assert.equal(isFinal({ status: "preparing", payment_status: "paid" }), false, "the kitchen is still working");
assert.equal(isFinal(null), false);
assert.deepEqual([0, 1, 2, 3, 4, 5, 9].map(reconnectDelay), [1000, 2000, 4000, 8000, 16000, 30000, 30000]);

const store = new Map();
globalThis.localStorage = {
  getItem: (k) => store.get(k) ?? null,
  setItem: (k, v) => store.set(k, v),
  removeItem: (k) => store.delete(k)
};
const active = { order: { id: "o1", tracking: "trk", payKey: "pay_1" }, payment: null };
writeActiveOrder("T1", active, 0);
assert.deepEqual(readActiveOrder("T1", 3 * 60 * 60 * 1000), active, "still valid at exactly three hours");
assert.equal(readActiveOrder("T1", 3 * 60 * 60 * 1000 + 1), null, "older than the guest session is dropped");
assert.equal(store.size, 0, "an expired record is removed");
store.set("cassy_active_order_T2", JSON.stringify(active));
assert.equal(readActiveOrder("T2"), null, "a record without savedAt is ignored");
assert.deepEqual(["queued", "preparing", "ready", "served", "completed", undefined].map(progressStep), [0, 1, 2, 3, 3, 0]);

const product = (id, category_id) => ({ id, category_id });
const sections = menuSections(
  [{ id: "food", name: "Makanan" }, { id: "empty", name: "Kosong" }, { id: "drink", name: "Minuman" }],
  [product("tea", "drink"), product("toast", "food"), product("mystery", null), product("old", "archived")],
  "Lainnya"
);
assert.deepEqual(
  sections.map((s) => [s.name, s.products.map((p) => p.id)]),
  [["Makanan", ["toast"]], ["Minuman", ["tea"]], ["Lainnya", ["mystery", "old"]]],
  "category order kept, empty skipped, unknown last"
);
assert.equal(safeDeeplink("gojek://gopay/merchanttransfer?tref=1"), "gojek://gopay/merchanttransfer?tref=1");
assert.equal(safeDeeplink("https://simulator.sandbox.midtrans.com/pay/1"), "https://simulator.sandbox.midtrans.com/pay/1");
assert.equal(safeDeeplink("javascript:alert(1)"), null);
assert.equal(safeDeeplink(" JavaScript:alert(1)"), null, "case and leading space do not slip through");
assert.equal(safeDeeplink("data:text/html,x"), null);
assert.equal(safeDeeplink("not a url"), null);
assert.equal(safeDeeplink(undefined), null);
assert.equal(safeDeeplink("shopeeid://main?x=1"), "shopeeid://main?x=1");
assert.equal(safeDeeplink("http://evil.example/pay"), null, "plain http is not a wallet redirect");
assert.equal(safeDeeplink("intent://x#Intent;scheme=evil;end"), null, "unknown schemes are dropped");
assert.equal(safeDeeplink("file:///etc/passwd"), null);
assert.equal(checkoutMessageKey("INVALID_PAYMENT"), "methodUnavailable");
console.log("cart ok");

// Bell: distinct harmonic chimes for paid, ready, delivered, and expiringSoon.
const { bellNotes } = await import("./bell.ts");
const ready = bellNotes("ready");
assert.equal(ready.length, 2);
assert.ok(ready[1][0] > ready[0][0] && ready[1][1] > ready[0][1]);
assert.equal(bellNotes("delivered").length, 2);
assert.equal(bellNotes("paid").length, 3);
assert.equal(bellNotes("expiringSoon").length, 2);

// Maintenance: only nginx's 503 with code MAINTENANCE counts, not every 503.
const { ApiError, isMaintenance } = await import("../../lib/api.ts");
assert.equal(isMaintenance(new ApiError(503, "x", { code: "MAINTENANCE" })), true);
assert.equal(isMaintenance(new ApiError(503, "x", { code: "SERVICE_TEMPORARILY_UNAVAILABLE" })), false);
assert.equal(isMaintenance(new ApiError(503, "x", null)), false);
assert.equal(checkoutMessageKey("MAINTENANCE"), "maintenanceShort");

// Bundle lines: same slot choices in another order merge, different choices stay separate, and toOrderItems maps bundle_items.
const bundleA = {
  productId: "bundle-1",
  variantId: "v-b1",
  name: "Paket Kopi & Roti",
  options: [],
  bundleItems: [
    { slotId: "slot-drink", productId: "latte", variantId: "v-l", options: [{ optionId: "oat", subOptionIds: ["b", "a"] }] },
    { slotId: "slot-food", productId: "croissant", variantId: "v-c", options: [] }
  ],
  quantity: 1,
  notes: ""
};
const bundleAReordered = {
  ...bundleA,
  bundleItems: [
    { slotId: "slot-food", productId: "croissant", variantId: "v-c", options: [] },
    { slotId: "slot-drink", productId: "latte", variantId: "v-l", options: [{ optionId: "oat", subOptionIds: ["a", "b"] }] }
  ]
};
const bundleB = {
  ...bundleA,
  bundleItems: [
    { slotId: "slot-drink", productId: "americano", variantId: "v-a", options: [] },
    { slotId: "slot-food", productId: "croissant", variantId: "v-c", options: [] }
  ]
};
let bundleCart = cartReducer([], { type: "add", line: bundleA });
bundleCart = cartReducer(bundleCart, { type: "add", line: bundleAReordered });
assert.equal(bundleCart.length, 1, "identical bundle choices in another slot/sub-addon order merge");
assert.equal(bundleCart[0].quantity, 2);
bundleCart = cartReducer(bundleCart, { type: "add", line: bundleB });
assert.equal(bundleCart.length, 2, "different bundle component stays a separate line");
const mappedBundle = toOrderItems(bundleCart);
assert.deepEqual(mappedBundle[0].bundle_items, [
  {
    slot_id: "slot-drink",
    product_id: "latte",
    variant_id: "v-l",
    modifiers: [{ option_id: "oat", quantity: 1, sub_addons: [{ option_id: "b", quantity: 1 }, { option_id: "a", quantity: 1 }] }]
  },
  {
    slot_id: "slot-food",
    product_id: "croissant",
    variant_id: "v-c",
    modifiers: []
  }
]);
assert.equal("bundle_items" in toOrderItems(cart)[0], false, "non-bundle line omits bundle_items");

// Smart pairing: prefers complementary available simple products not yet in cart, capped at max.
const simpleVariant = { id: "v", name: "Regular", price: 25000, is_default: true, available: true };
const catalog = [
  { id: "c1", category_id: "coffee", name: "Latte", available: true, variants: [simpleVariant], modifier_groups: [] },
  { id: "c2", category_id: "coffee", name: "Americano", available: true, variants: [simpleVariant], modifier_groups: [] },
  { id: "p1", category_id: "pastry", name: "Croissant", available: true, variants: [simpleVariant], modifier_groups: [] },
  { id: "p2", category_id: "pastry", name: "Pain au Chocolat", available: true, variants: [simpleVariant], modifier_groups: [{ id: "g", name: "Warm", required: false, min: 0, max: 1, options: [] }] },
  { id: "p3", category_id: "pastry", name: "Sold Out Tart", available: false, variants: [simpleVariant], modifier_groups: [] },
  { id: "p4", category_id: "snack", name: "Cookies", available: true, variants: [simpleVariant], modifier_groups: [] }
];
assert.deepEqual(
  pickSmartPairings(catalog, [{ key: "k", productId: "c1", name: "Latte", options: [], quantity: 1, notes: "" }], 2).map((p) => p.id),
  ["p1", "p4"],
  "skips in-cart, modifiers, and sold-out; picks complementary categories first"
);
const catalogWithServerRecs = catalog.map((p) =>
  p.id === "c1" ? { ...p, recommended_product_ids: ["p4", "p3", "c2"] } : p
);
assert.deepEqual(
  pickSmartPairings(catalogWithServerRecs, [{ key: "k", productId: "c1", name: "Latte", options: [], quantity: 1, notes: "" }], 2).map((p) => p.id),
  ["p4", "c2"],
  "prefers server recommended_product_ids in order and skips unavailable recommended items"
);
assert.deepEqual(pickSmartPairings(catalog, [], 2), [], "empty cart returns no pairings");
