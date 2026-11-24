import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { api, E2E_TABLE, openStoreForGuests, staffLogin } from "./LiveShift";

// PWA-02 (PrdOpsAndPwaClose.md section 12, S4-Q7): the guest's cart is priced by the server. When the manager
// changes the price or marks the item sold out after the guest saw the total, the real API answers
// QUOTE_CHANGED or UNAVAILABLE, the PWA says so, and a re-priced retry pays the new server total.

async function kopiVariant(request: APIRequestContext): Promise<string> {
  const catalog = await (await request.get(`${api}/public/stores/e2e-kasir/catalog`)).json();
  const kopi = (
    catalog.products as {
      name: string;
      variants: { id: string; is_default: boolean }[];
    }[]
  ).find((p) => p.name === "Kopi Uji")!;
  return kopi.variants.find((v) => v.is_default)!.id;
}

async function openCart(page: Page): Promise<number> {
  await page.goto(`/order/${E2E_TABLE}`);
  await page.getByRole("button", { name: "Tambah Kopi Uji" }).click();
  await page.getByRole("button", { name: /Lihat keranjang/ }).click();
  await page.getByLabel("Nama pemesan").fill("Tamu Uji");
  return Number(((await page.getByRole("button", { name: /^Bayar Rp / }).textContent()) ?? "").replace(/\D/g, ""));
}

for (const [label, change, message] of [
  ["price change answers QUOTE_CHANGED and the retry pays the new total", { price: 27_000 }, "Harga berubah"],
  ["sold-out item answers UNAVAILABLE and keeps the cart", { is_available: false }, "baru saja habis"]
] as const) {
  test(label, async ({ page, request }) => {
    const store = await openStoreForGuests(request);
    const manager = await staffLogin(request, "manager.e2e@cassy.local", "739164");
    const variant = await kopiVariant(request);
    const patch = (data: object) =>
      request.patch(`${api}/catalog/variants/${variant}`, {
        headers: { Authorization: `Bearer ${manager}` },
        data
      });
    try {
      const shown = await openCart(page);
      const changed = await patch(change);
      expect(changed.ok(), await changed.text()).toBe(true);

      await page.getByRole("button", { name: /^Bayar Rp / }).click();
      await expect(page.getByRole("dialog").getByRole("alert")).toContainText(message);
      await expect(page.getByRole("dialog")).toContainText("Kopi Uji");

      if ("price" in change) {
        const retry = page.getByRole("button", { name: /^Bayar Rp / });
        await expect.poll(async () => Number(((await retry.textContent()) ?? "").replace(/\D/g, ""))).toBeGreaterThan(shown);
        const created = page.waitForResponse((r) => /\/public\/guest\/orders$/.test(r.url()) && r.request().method() === "POST");
        await retry.click();
        expect((await created).status()).toBe(201);
      }
    } finally {
      await patch({ price: 25_000, is_available: true });
      await store.close();
    }
  });
}
