// Run with: node --experimental-strip-types src/lib/plans.test.mjs
import assert from "node:assert/strict";
import { FALLBACK_PLANS, PLAN_CODES, isTenMonthAnnual, parsePlans } from "./plans.ts";

const good = { plans: PLAN_CODES.map((c) => ({ ...FALLBACK_PLANS[c], is_active: true })).reverse() };
assert.deepEqual(parsePlans(good).map((p) => p.code), [...PLAN_CODES], "display order is fixed");
assert.ok(parsePlans(good).filter((p) => p.monthly_price_idr > 0).every(isTenMonthAnnual), "snapshot is 10 x monthly");

const broken = (patch) => ({ plans: good.plans.map((p) => (p.code === "pro_cafe" ? { ...p, ...patch } : p)) });
assert.throws(() => parsePlans({ plans: good.plans.filter((p) => p.code !== "pro_cafe") }), /pro_cafe missing/);
assert.throws(() => parsePlans(broken({ monthly_price_idr: 299000.5 })), /not a non-negative integer/);
assert.throws(() => parsePlans(broken({ monthly_price_idr: "299000" })), /not a non-negative integer/);
assert.throws(() => parsePlans(broken({ commission_bps: 50 })), /commission/);
assert.throws(() => parsePlans(broken({ annual_price_idr: 299000 * 13 })), /above 12 months/);
assert.throws(() => parsePlans(broken({ supports_kds_queue_tv: "yes" })), /boolean/);
assert.throws(() => parsePlans({}), /no plans array/);
// The 9.6 x list still on the backend before migration 079 is valid data, just not a 10-month annual.
assert.equal(isTenMonthAnnual({ ...FALLBACK_PLANS.starter_umkm, annual_price_idr: 1430400 }), false);
console.log("plans.test ok");
{
  // A backend before migration 079 omits the new quotas: accepted, and they stay unknown.
  const old = { plans: good.plans.map(({ max_products, max_staff, max_devices, max_kds_stations, ...rest }) => rest) };
  assert.equal(parsePlans(old)[1].max_kds_stations, undefined);
  assert.throws(() => parsePlans(broken({ max_kds_stations: -1 })), /max_kds_stations/);
}
console.log("plans.test optional quotas ok");
