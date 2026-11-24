import { expect, test, type APIRequestContext } from "@playwright/test";
import { randomUUID } from "node:crypto";

// S4 cup labels (PrdOpsAndPwaClose.md section 12, S4-Q3): the server renders TSPL for the label printer,
// the real print agent in the test stack claims the job and prints it on the virtual printer.
const api = process.env.DIRECT_API_URL || "http://127.0.0.1:18081/api/v1";
const printer = process.env.ESCPOS_SINK_URL || "http://127.0.0.1:9101";
const STORE = "e2e-kasir";

async function ok(request: APIRequestContext, method: "GET" | "POST", path: string, token?: string, data?: unknown) {
  const res = await request.fetch(`${api}${path}`, {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false
  });
  const body = await res.text();
  expect(res.status(), `${method} ${path}: ${body}`).toBeLessThan(300);
  return body ? JSON.parse(body) : {};
}

const login = async (request: APIRequestContext, email: string, pin: string): Promise<string> =>
  (await ok(request, "POST", "/auth/staff/login", undefined, { store_slug: STORE, email, pin })).access_token;

test("three iced drinks print three cup labels with table, notes, name and time", async ({ request }) => {
  const cashier = await login(request, "cashier.e2e@cassy.local", "528307");
  const manager = await login(request, "manager.e2e@cassy.local", "739164");

  const drawer = (await ok(request, "GET", "/pos/drawers", cashier)).drawers.find((d: { name: string }) => d.name === "Laci Uji 2");
  const leftover = await request.fetch(`${api}/pos/shifts/current?drawer_id=${drawer.id}`, {
    headers: { Authorization: `Bearer ${cashier}` },
    failOnStatusCode: false
  });
  if (leftover.ok()) {
    const shift = await leftover.json();
    if (shift.id) await ok(request, "POST", `/pos/shifts/${shift.id}/force-close`, manager, { reason: "Sisa percobaan uji e2e sebelumnya" });
  }
  const shift = (await ok(request, "POST", "/pos/shifts", cashier, { drawer_id: drawer.id, opening_float: 100_000 })).shift;

  try {
    await request.delete(`${printer}/jobs`);
    const products = (await ok(request, "GET", "/pos/catalog", cashier)).products as { id: string; name: string }[];
    const kopi = products.find((p) => p.name === "Kopi Uji")!.id;
    const order = (
      await ok(request, "POST", "/pos/orders", cashier, {
        idempotency_key: randomUUID(),
        customer_name: "Rani",
        order_type: "dine_in",
        table_number: "4",
        billing_mode: "pay_first",
        items: [{ product_id: kopi, quantity: 3, notes: "es sedikit" }]
      })
    ).order;
    // Paying sends the drink to the bar; the label printer follows the bar (W8).
    await ok(request, "POST", `/pos/orders/${order.id}/payments`, cashier, {
      idempotency_key: randomUUID(),
      method: "edc",
      reconciliation_ref: "APPR-E2E-LABEL",
      drawer_id: drawer.id
    });

    let labels = "";
    await expect
      .poll(
        async () => {
          const jobs = (await (await request.get(`${printer}/jobs`)).json()) as { text: string }[];
          labels = jobs.map((j) => j.text).filter((t) => t.includes("SIZE 50 mm,30 mm")).join("\n");
          return (labels.match(/PRINT 1,1/g) ?? []).length;
        },
        { timeout: 60_000, message: "the print agent prints one label per cup" }
      )
      .toBe(3);

    const q = order.queue_number as string;
    for (const n of ["1/3", "2/3", "3/3"]) expect(labels).toContain(`${q}  ${n}  MJ 4`);
    expect(labels).toContain("* es sedikit");
    expect(labels).toMatch(/Rani {2}\d{2}:\d{2}/);
    expect(labels).not.toContain("Rp");
  } finally {
    await request.fetch(`${api}/pos/shifts/${shift.id}/force-close`, {
      method: "POST",
      data: { reason: "Akhir uji label e2e" },
      headers: { Authorization: `Bearer ${manager}` },
      failOnStatusCode: false
    });
  }
});
