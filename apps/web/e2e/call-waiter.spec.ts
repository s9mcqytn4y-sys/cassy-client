import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { json, mockStore } from "./mocks";

const TEMPLATES = [
  { id: "t-tisu", label: "Minta tisu", sort_order: 0, is_active: true },
  { id: "t-air", label: "Minta air putih", sort_order: 1, is_active: true }
];

async function mockRequests(page: Page, onPost: (body: Record<string, unknown>) => { status: number; body: unknown }) {
  await page.route("**/api/v1/public/guest/service-request-templates", (r) => r.fulfill(json({ templates: TEMPLATES })));
  await page.route("**/api/v1/public/guest/service-requests", (r) => {
    if (r.request().method() === "GET") return r.fulfill(json({ service_requests: [] }));
    const res = onPost(r.request().postDataJSON());
    return r.fulfill(json(res.body, res.status));
  });
}

test("panggil pelayan tersembunyi saat fitur mati", async ({ page }) => {
  await mockStore(page);
  await page.goto("/order/T1");
  await expect(page.getByRole("heading", { level: 1, name: "Kopi Senja" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Panggil pelayan" })).toHaveCount(0);
});

test("tamu memanggil pelayan dengan sekali ketuk", async ({ page }) => {
  await mockStore(page, { call_waiter_enabled: true });
  let sent: Record<string, unknown> | null = null;
  await mockRequests(page, (body) => {
    sent = body;
    return {
      status: 201,
      body: { id: "sr1", label: "Minta tisu", status: "open", created_at: "2026-11-17T10:00:00Z", table_id: "tb", table_number: "A1", template_id: "t-tisu" }
    };
  });
  await page.goto("/order/T1");
  await page.getByRole("button", { name: "Panggil pelayan" }).click();
  const sheet = page.getByRole("dialog", { name: "Butuh bantuan?" });
  await expect(sheet.getByRole("button", { name: "Minta tisu" })).toBeVisible();
  const { violations } = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
  expect(violations.filter((v) => v.impact === "serious" || v.impact === "critical").map((v) => v.id)).toEqual([]);

  await sheet.getByLabel("Catatan untuk staf (opsional)").fill("dua lembar");
  await sheet.getByRole("button", { name: "Minta tisu" }).click();
  await expect(page.getByText("Permintaan terkirim. Staf segera datang.")).toBeVisible();
  expect(sent).toEqual({ template_id: "t-tisu", note: "dua lembar" });
});

test("batas permintaan menampilkan pesan server", async ({ page }) => {
  await mockStore(page, { call_waiter_enabled: true });
  await mockRequests(page, () => ({
    status: 429,
    body: { code: "TOO_MANY_REQUESTS", error: "Permintaan terlalu sering. Tunggu sebentar, staf sedang menuju meja Anda." }
  }));
  await page.goto("/order/T1");
  await page.getByRole("button", { name: "Panggil pelayan" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Minta air putih" }).click();
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("Permintaan terlalu sering");
});
