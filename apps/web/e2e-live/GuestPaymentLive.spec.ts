import { expect, test } from "@playwright/test";
import { api, E2E_TABLE, openStoreForGuests } from "./LiveShift";

// PWA-03 (PrdOpsAndPwaClose.md section 12, S4-Q8): a guest pays QRIS through the real API, which charges
// the fake Midtrans in the test stack. The test settles the charge there; the stub posts a webhook signed
// like Midtrans (SHA-512 with the charge's server key), the API marks the order paid, the PWA shows it,
// the tracker shows the ETA and follows the bar ticket to ready (PWA-06), and /r/<code> shows the total.
const midtrans = process.env.MIDTRANS_STUB_URL || "http://127.0.0.1:9200";

type Charge = {
  order_id: string;
  gross_amount: string;
  payment_type: string;
  transaction_status: string;
  qr_string?: string;
};

test("guest pays QRIS, the webhook settles it, the tracker follows the bar, and the receipt shows the total", async ({ page, request }) => {
  const store = await openStoreForGuests(request);
  try {
    const before = new Set(((await (await request.get(`${midtrans}/_stub/charges`)).json()) as Charge[]).map((c) => c.order_id));

    await page.goto(`/order/${E2E_TABLE}`);
    await page.getByRole("button", { name: "Tambah Kopi Uji" }).click();
    await page.getByRole("button", { name: /Lihat keranjang/ }).click();
    await page.getByLabel("Nama pemesan").fill("Tamu Uji");
    const pay = page.getByRole("button", { name: /^Bayar Rp / });
    const total = Number(((await pay.textContent()) ?? "").replace(/\D/g, ""));
    expect(total, "the cart shows a server-quoted total").toBeGreaterThan(0);

    const created = page.waitForResponse((r) => /\/public\/guest\/orders$/.test(r.url()) && r.request().method() === "POST");
    await pay.click();
    const orderId = ((await (await created).json()) as { order: { id: string } }).order.id;

    let charge: Charge | undefined;
    await expect
      .poll(
        async () => {
          const charges = (await (await request.get(`${midtrans}/_stub/charges`)).json()) as Charge[];
          charge = charges.find((c) => !before.has(c.order_id));
          return charge?.payment_type;
        },
        { message: "the API charged QRIS at the stub" }
      )
      .toBe("qris");
    expect(charge!.gross_amount).toBe(`${total}.00`);
    expect(charge!.transaction_status).toBe("pending");
    await expect(page.getByRole("img", { name: "Kode QR dapat dipindai" })).toBeVisible();

    const settled = await (
      await request.post(`${midtrans}/_stub/charges/${charge!.order_id}/settle`, { data: { transaction_status: "settlement" } })
    ).json();
    expect(settled.webhook_status, settled.webhook_body).toBe(200);

    await expect(page.getByRole("heading", { name: "Pembayaran diterima" })).toBeVisible({ timeout: 30_000 });

    // PWA-06: the tracker shows the server ETA, then follows the bar ticket to ready.
    await expect(page.getByText(/Estimasi siap sekitar \d+ menit/)).toBeVisible();
    const progress = page.getByRole("list", { name: "Perjalanan pesanan" }).locator('[aria-current="step"]');
    await expect(progress).toContainText("Pesanan diterima");
    const auth = { Authorization: `Bearer ${store.cashier}` };
    const tickets = (await (await request.get(`${api}/kds/tickets?station=bar`, { headers: auth })).json()).tickets as {
      id: string;
      order_id: string;
      status: string;
    }[];
    const ticket = tickets.find((k) => k.order_id === orderId)!;
    for (const [from, to, step] of [
      [ticket.status, "preparing", "Sedang disiapkan"],
      ["preparing", "ready", "Sedang diantar ke meja Anda"]
    ]) {
      const moved = await request.post(`${api}/kds/tickets/${ticket.id}/transition`, {
        headers: auth,
        data: { to, expected_status: from }
      });
      expect(moved.ok(), await moved.text()).toBe(true);
      await expect(progress).toContainText(step, { timeout: 30_000 });
    }

    const link = await request.post(`${api}/pos/orders/${orderId}/receipt-link`, { headers: { Authorization: `Bearer ${store.cashier}` } });
    expect(link.ok(), await link.text()).toBe(true);
    const { short_code: code } = (await link.json()) as { short_code: string };
    expect(code).toBeTruthy();

    await page.goto(`/r/${code}`);
    await expect(page.getByText(`Rp ${total.toLocaleString("id-ID")}`).first()).toBeVisible();
    await expect(page.getByText(/QRIS/i).first()).toBeVisible();
  } finally {
    await store.close();
  }
});
