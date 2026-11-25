/**
 * Subscription plans for the marketing pages (FL-Q6). The backend `GET /api/v1/public/plans` is the only
 * source of prices; this module never computes a price. A wrong price must fail the build or the daily
 * revalidation (Next keeps the last good page) instead of being shown.
 */

/** One row of `GET /api/v1/public/plans`. Prices are integer rupiah; every `max_*` uses 0 for unlimited. */
export interface Plan {
  code: string;
  name_id: string;
  name_en: string;
  monthly_price_idr: number;
  annual_price_idr: number;
  commission_bps: number;
  max_tables: number;
  max_printers: number;
  /** Quotas below arrive with backend migration 079; an older backend omits them and the pages hide them. */
  max_products?: number;
  max_staff?: number;
  max_devices?: number;
  max_kds_stations?: number;
  supports_byok_midtrans: boolean;
  supports_kds_queue_tv: boolean;
  supports_whitelabel: boolean;
  supports_byo_notifications: boolean;
}

/** Plans the pages show, in display order. A missing one fails the build. */
export const PLAN_CODES = ["trial_14d", "starter_umkm", "pro_cafe", "enterprise_whitelabel"] as const;
export type PlanCode = (typeof PLAN_CODES)[number];

/**
 * Snapshot of the approved price list (FL-Q3..Q5, backend migration 079). Used only when the build has no
 * backend and `ALLOW_PLAN_FALLBACK=1` is set (CI jobs without the API). Production builds never set it.
 */
export const FALLBACK_PLANS: Record<PlanCode, Plan> = {
  trial_14d: plan("trial_14d", "Uji Coba Gratis 14 Hari", "14-Day Free Trial", 0, 0, 15, 1, 100, 5, 2, 1, true, false, false),
  starter_umkm: plan("starter_umkm", "Starter UMKM", "Starter SME", 149000, 1490000, 15, 1, 100, 5, 2, 1, true, false, false),
  pro_cafe: plan("pro_cafe", "Pro Cafe & Resto", "Pro Cafe & Resto", 299000, 2990000, 0, 0, 0, 0, 0, 0, true, false, true),
  enterprise_whitelabel: plan("enterprise_whitelabel", "Enterprise White-Label", "Enterprise White-Label", 599000, 5990000, 0, 0, 0, 0, 0, 0, true, true, true)
};

function plan(
  code: PlanCode, nameId: string, nameEn: string, monthly: number, annual: number,
  tables: number, printers: number, products: number, staff: number, devices: number, kds: number,
  kdsTv: boolean, whitelabel: boolean, byoNotifications: boolean
): Plan {
  return {
    code, name_id: nameId, name_en: nameEn, monthly_price_idr: monthly, annual_price_idr: annual, commission_bps: 0,
    max_tables: tables, max_printers: printers, max_products: products, max_staff: staff, max_devices: devices,
    max_kds_stations: kds, supports_byok_midtrans: true, supports_kds_queue_tv: kdsTv,
    supports_whitelabel: whitelabel, supports_byo_notifications: byoNotifications
  };
}

const INT_FIELDS = ["monthly_price_idr", "annual_price_idr", "commission_bps", "max_tables", "max_printers"] as const;
const OPTIONAL_INT_FIELDS = ["max_products", "max_staff", "max_devices", "max_kds_stations"] as const;
const BOOL_FIELDS = ["supports_byok_midtrans", "supports_kds_queue_tv", "supports_whitelabel", "supports_byo_notifications"] as const;

/**
 * Checks the API payload and returns the plans in display order. Throws on anything that could put a wrong
 * price on screen: a missing plan, a non-integer or negative number, a commission, or an annual price above
 * twelve months.
 */
export function parsePlans(payload: unknown): Plan[] {
  const rows = (payload as { plans?: unknown } | null)?.plans;
  if (!Array.isArray(rows)) throw new Error("plans payload has no plans array");
  const byCode = new Map<string, Plan>();
  for (const row of rows as Record<string, unknown>[]) {
    if (typeof row?.code !== "string") continue;
    for (const f of INT_FIELDS) {
      if (!Number.isSafeInteger(row[f]) || (row[f] as number) < 0) throw new Error(`plan ${row.code}: ${f} is not a non-negative integer`);
    }
    for (const f of OPTIONAL_INT_FIELDS) {
      if (row[f] !== undefined && (!Number.isSafeInteger(row[f]) || (row[f] as number) < 0)) throw new Error(`plan ${row.code}: ${f} is not a non-negative integer`);
    }
    for (const f of BOOL_FIELDS) {
      if (typeof row[f] !== "boolean") throw new Error(`plan ${row.code}: ${f} is not a boolean`);
    }
    if (typeof row.name_id !== "string" || typeof row.name_en !== "string") throw new Error(`plan ${row.code}: missing names`);
    if (row.commission_bps !== 0) throw new Error(`plan ${row.code}: commission must be 0`);
    if ((row.annual_price_idr as number) > (row.monthly_price_idr as number) * 12) throw new Error(`plan ${row.code}: annual above 12 months`);
    byCode.set(row.code, row as unknown as Plan);
  }
  return PLAN_CODES.map((code) => {
    const p = byCode.get(code);
    if (!p) throw new Error(`plan ${code} missing from the API`);
    return p;
  });
}

/** Base URL of the backend the build reads prices from. */
function plansApiBase(): string {
  return (process.env.PLANS_API_URL || process.env.BACKEND_INTERNAL_URL || "http://127.0.0.1:8080").replace(/\/+$/, "");
}

/**
 * Loads the plans for a static page. Next caches the response for a day (the pages revalidate daily). An
 * unreachable API fails the build unless `ALLOW_PLAN_FALLBACK=1`; an invalid payload always fails.
 */
export async function loadPlans(): Promise<Plan[]> {
  let payload: unknown;
  try {
    const res = await fetch(`${plansApiBase()}/api/v1/public/plans`, { next: { revalidate: 86400 } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    payload = await res.json();
  } catch (err) {
    if (process.env.ALLOW_PLAN_FALLBACK === "1") return PLAN_CODES.map((code) => FALLBACK_PLANS[code]);
    throw new Error(`cannot read plans from ${plansApiBase()}: ${String(err)}. Set PLANS_API_URL, or ALLOW_PLAN_FALLBACK=1 for builds without a backend.`);
  }
  return parsePlans(payload);
}

/** True when the annual price equals ten monthly payments ("bayar 10 bulan, pakai 12"). */
export function isTenMonthAnnual(p: Plan): boolean {
  return p.monthly_price_idr > 0 && p.annual_price_idr === p.monthly_price_idr * 10;
}
