import { expect, test } from "@playwright/test";
import { E2E_TABLE, openStoreForGuests } from "./LiveShift";

// PWA-01 (PrdOpsAndPwaClose.md section 12, S4-Q7): scanning a real table QR loads the live catalog, a drink
// with a required option cannot enter the cart until it is chosen, and the cart survives a reload.
// Runs on a phone and a desktop viewport.
for (const [device, viewport] of [
  ["mobile", { width: 390, height: 844 }],
  ["desktop", { width: 1280, height: 800 }]
] as const) {
  test(`${device}: table QR, required option, cart kept after reload`, async ({ page, request }) => {
    const store = await openStoreForGuests(request);
    try {
      await page.setViewportSize(viewport);
      await page.goto(`/order/${E2E_TABLE}`);
      await expect(page.getByRole("heading", { level: 1, name: "Kafe Uji E2E" })).toBeVisible();

      await page.getByRole("button", { name: "Pilih Latte Uji" }).click();
      await page.getByRole("button", { name: "Masukkan keranjang" }).click();
      await expect(page.getByText("Lengkapi pilihan")).toBeVisible();

      await page.getByLabel(/Susu Oat/).check();
      await page.getByRole("button", { name: "Masukkan keranjang" }).click();
      const cart = page.getByRole("button", {
        name: /Lihat keranjang \(1 item\)/
      });
      await expect(cart).toBeVisible();

      await page.reload();
      await expect(cart).toBeVisible();
      await cart.click();
      await expect(page.getByRole("dialog")).toContainText("Susu Oat");
    } finally {
      await store.close();
    }
  });
}
