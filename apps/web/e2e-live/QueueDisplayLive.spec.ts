import { expect, test, type APIRequestContext } from "@playwright/test";
import { randomUUID } from "node:crypto";

// S3 OPS-07 (PrdOpsAndPwaClose.md section 11, S3-Q4): the queue TV against the real stack.
// Staff calls go through the Toxiproxy port tests never cut; the page itself uses the Next.js rewrite.
const api = process.env.DIRECT_API_URL || "http://127.0.0.1:18081/api/v1";
const STORE = "e2e-kasir";

async function call(request: APIRequestContext, method: "GET" | "POST", path: string, token?: string, data?: unknown) {
  const res = await request.fetch(`${api}${path}`, {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false
  });
  const body = await res.text();
  return { status: res.status(), json: body ? JSON.parse(body) : {} };
}

async function ok(request: APIRequestContext, method: "GET" | "POST", path: string, token?: string, data?: unknown) {
  const res = await call(request, method, path, token, data);
  expect(res.status, `${method} ${path}: ${JSON.stringify(res.json)}`).toBeLessThan(300);
  return res.json;
}

async function login(request: APIRequestContext, email: string, pin: string): Promise<string> {
  return (await ok(request, "POST", "/auth/staff/login", undefined, { store_slug: STORE, email, pin })).access_token;
}

test("queue number moves from preparing to ready, leaves when served, and a revoked screen locks", async ({ page, request }) => {
  const owner = await login(request, "owner.e2e@cassy.local", "482913");
  const manager = await login(request, "manager.e2e@cassy.local", "739164");
  const cashier = await login(request, "cashier.e2e@cassy.local", "528307");
  const kitchen = await login(request, "kitchen.e2e@cassy.local", "615842");

  // A paid pay-first order needs the cashier's shift; a shift left by an earlier attempt is force-closed.
  const drawer = (await ok(request, "GET", "/pos/drawers", cashier)).drawers.find((d: { name: string }) => d.name === "Laci Uji 2");
  const leftover = await call(request, "GET", `/pos/shifts/current?drawer_id=${drawer.id}`, cashier);
  if (leftover.status === 200 && leftover.json.id) {
    await ok(request, "POST", `/pos/shifts/${leftover.json.id}/force-close`, manager, { reason: "Sisa percobaan uji e2e sebelumnya" });
  }
  const shift = (await ok(request, "POST", "/pos/shifts", cashier, { drawer_id: drawer.id, opening_float: 100_000 })).shift;

  const display = await ok(request, "POST", "/store/display-tokens", owner, { name: "TV Uji" });
  try {
    const products = (await ok(request, "GET", "/pos/catalog", cashier)).products as { id: string; name: string }[];
    const nasi = products.find((p) => p.name === "Nasi Uji")!.id;
    const order = (
      await ok(request, "POST", "/pos/orders", cashier, {
        idempotency_key: randomUUID(),
        customer_name: "Antrean Uji",
        order_type: "dine_in",
        billing_mode: "pay_first",
        items: [{ product_id: nasi, quantity: 1 }]
      })
    ).order;
    await ok(request, "POST", `/pos/orders/${order.id}/payments`, cashier, {
      idempotency_key: randomUUID(),
      method: "edc",
      reconciliation_ref: "APPR-E2E-Q",
      drawer_id: drawer.id
    });
    const number: string = order.queue_number;

    await page.goto(`/antrean#t=${display.token}`);
    const preparing = page.getByRole("region", { name: /Sedang disiapkan|Being prepared/ });
    await expect(preparing.getByText(number, { exact: true })).toBeVisible();

    const ticket = (await ok(request, "GET", "/kds/tickets?station=kitchen", kitchen)).tickets.find(
      (t: { order_id: string }) => t.order_id === order.id
    );
    const bump = (to: string, expected: string) =>
      ok(request, "POST", `/kds/tickets/${ticket.id}/transition`, kitchen, { to, expected_status: expected });
    await bump("preparing", "queued");
    await bump("ready", "preparing");

    // Ready within 3 s of the bump (S3-Q4), out of the preparing column.
    await expect(preparing.getByText(number, { exact: true })).toHaveCount(0, { timeout: 3_000 });
    await expect(page.getByText(number, { exact: true }).first()).toBeVisible({ timeout: 3_000 });

    await bump("served", "ready");
    await expect(page.getByText(number, { exact: true })).toHaveCount(0);

    await ok(request, "POST", `/store/display-tokens/${display.id}/revoke`, owner);
    await expect(page.getByLabel(/Kode layar|Screen code/)).toBeVisible();
  } finally {
    await call(request, "POST", `/pos/shifts/${shift.id}/force-close`, manager, { reason: "Akhir uji antrean e2e" });
  }
});
