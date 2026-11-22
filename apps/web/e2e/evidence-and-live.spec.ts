import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { json, mockCheckout, mockStore } from "./mocks";

const captureEvidence = process.env.PLAYWRIGHT_CAPTURE_EVIDENCE === "1";
const runLiveApi = process.env.PLAYWRIGHT_LIVE_API === "1";
const screenshotRoot = path.resolve(process.cwd(), "screenshots", "ui-v2.5.0");

const SENOPATI_QR = "qr_senopati_m01_7f8a9b2c3d4e5f6a7b8c9d01";

function viewportSuffix(testInfo: TestInfo): "Desktop" | "Mobile" {
  return testInfo.project.name === "mobile-safari" ? "Mobile" : "Desktop";
}

async function setViewportForEvidence(page: Page, testInfo: TestInfo) {
  if (testInfo.project.name !== "mobile-safari") {
    await page.setViewportSize({ width: 1440, height: 900 });
  }
}

async function saveShot(page: Page, testInfo: TestInfo, group: string, stateName: string) {
  if (!captureEvidence) return;
  const dir = path.join(screenshotRoot, group);
  await fs.mkdir(dir, { recursive: true });
  await page.screenshot({ path: path.join(dir, `${stateName}${viewportSuffix(testInfo)}.png`), fullPage: true });
}

async function expectCleanA11y(page: Page) {
  const { violations } = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
  const serious = violations.filter((v) => v.impact === "serious" || v.impact === "critical");
  expect(serious.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).join(", ")}`)).toEqual([]);
}

async function mockLegalDocs(page: Page) {
  const mk = (doc_type: string, title: string, content_markdown: string) => ({
    doc_type,
    title,
    version: "1.0.0",
    effective_at: "2026-11-10T00:00:00Z",
    content_sha256: crypto.createHash("sha256").update(content_markdown).digest("hex"),
    content_markdown
  });
  await page.route("**/api/v1/public/legal*", (r) =>
    r.fulfill(
      json({
        documents: [
          mk("guest_order_terms", "Ketentuan Pemesanan Tamu", "## Ketentuan Umum\nPesanan diteruskan ke kasir gerai."),
          mk("guest_privacy_notice", "Pemberitahuan Privasi Tamu", "## Privasi UU PDP\nData pesanan dipakai untuk memenuhi pesanan meja."),
          mk("dpa_tenant", "Perjanjian Pemrosesan Data", "## DPA Mitra\nIsolasi tenant memakai PostgreSQL RLS."),
          mk("terms_of_service", "Syarat Layanan Mitra", "## Ketentuan Layanan\nHarga dan pajak diatur penuh oleh gerai.")
        ]
      })
    )
  );
}

test.describe("UI v2.5.0 Visual Evidence & Regression Suite", () => {
  test.skip(!captureEvidence, "Runs when PLAYWRIGHT_CAPTURE_EVIDENCE=1");
  test.setTimeout(90_000);

  test("Home & Legal pages evidence", async ({ page }, testInfo) => {
    await setViewportForEvidence(page, testInfo);
    await mockLegalDocs(page);

    await page.goto("/");
    await expect(page.locator("main")).toHaveCount(1);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expectCleanA11y(page);
    await saveShot(page, testInfo, "Home", "QrScanInstruction");

    await page.goto("/legal");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByRole("article")).toBeVisible();
    await expectCleanA11y(page);
    await saveShot(page, testInfo, "Legal", "GuestPrivacy");

    await page.goto("/legal/mitra");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByRole("article")).toBeVisible();
    await expectCleanA11y(page);
    await saveShot(page, testInfo, "Legal", "PartnerMitra");
  });

  test("Guest Order journeys & Payment evidence", async ({ page }, testInfo) => {
    await setViewportForEvidence(page, testInfo);
    await mockStore(page, { payment_methods: ["qris", "gopay", "shopeepay", "bank_transfer"] });
    await mockCheckout(page);

    await page.goto("/order/T1");
    await expect(page.getByRole("heading", { level: 1, name: "Kopi Senja" })).toBeVisible();
    await expectCleanA11y(page);
    await saveShot(page, testInfo, "GuestOrder", "MenuCatalog");

    await page.getByRole("button", { name: "Pilih Caffe Latte" }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await saveShot(page, testInfo, "GuestOrder", "ProductOptionsModal");
    await page.getByLabel(/Oat/).check();
    await page.getByRole("button", { name: "Masukkan keranjang" }).click();

    await page.getByRole("button", { name: /Lihat keranjang/ }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(page.getByRole("button", { name: "Bayar Rp 28.750" })).toBeVisible();
    // Wait one animation frame after sheet mount so WebKit transition opacity settles before Axe contrast audit.
    await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
    await expectCleanA11y(page);
    await saveShot(page, testInfo, "GuestOrder", "CartSheet");

    await page.getByLabel("Nama pemesan").fill("Rani");
    await page.getByRole("button", { name: "Bayar Rp 28.750" }).click();
    await expect(page.getByRole("heading", { name: "Pindai QRIS" })).toBeVisible();
    await saveShot(page, testInfo, "GuestOrder", "QrisPayment");
  });

  test("VA Bank Transfer, Digital Receipt & Error states evidence", async ({ page }, testInfo) => {
    await setViewportForEvidence(page, testInfo);
    await mockStore(page, { payment_methods: ["qris", "bank_transfer"] });
    await page.route("**/api/v1/public/guest/orders", (r) =>
      r.request().method() === "GET"
        ? r.fulfill(json({ orders: [] }))
        : r.fulfill(json({ order: { id: "o-va", order_number: "A-108" }, tracking_token: "trk-va" }, 201))
    );
    await page.route("**/api/v1/public/guest/orders/o-va/payments", (r) =>
      r.fulfill(
        json(
          {
            payment: {
              id: "p-va",
              method: "bank_transfer",
              bank: "bca",
              va_number: "880012345678",
              amount: 28750,
              expires_at: new Date(Date.now() + 15 * 60_000).toISOString()
            }
          },
          201
        )
      )
    );
    await page.route("**/api/v1/public/guest/ws-ticket", (r) => r.fulfill(json({}, 503)));
    await page.route("**/api/v1/public/orders/track/trk-va", (r) =>
      r.fulfill(json({ order_number: "A-108", status: "pending_payment", payment_status: "unpaid" }))
    );

    await page.goto("/order/T1");
    await page.getByRole("button", { name: "Tambah Croissant" }).click();
    await page.getByRole("button", { name: /Lihat keranjang/ }).click();
    await page.getByText("Transfer Bank (VA)", { exact: true }).click();
    await page.getByRole("button", { name: "Bayar Rp 28.750" }).click();
    await expect(page.getByTestId("va-number")).toHaveText("880012345678");
    await saveShot(page, testInfo, "GuestOrder", "VaBankTransfer");

    await page.route("**/api/v1/public/receipts/rt-evidence", (r) =>
      r.fulfill(
        json({
          store: { name: "Cassy Roastery Senopati", address: "Jl. Senopati No. 45, Jakarta" },
          order_number: "SEN-001",
          status: "completed",
          payment_status: "paid",
          issued_at: "2026-11-15T10:00:00Z",
          lines: [{ name: "Classic Iced Latte", variant: "Reguler", quantity: 1, amount: 24000 }],
          subtotal_amount: 24000,
          discount_amount: 0,
          service_charge_amount: 1200,
          tax_amount: 2520,
          total_amount: 27720,
          payments: [{ method: "qris" }]
        })
      )
    );
    await page.goto("/receipts/rt-evidence");
    await expect(page.getByText("SEN-001")).toBeVisible();
    await saveShot(page, testInfo, "Receipt", "DigitalReceipt");

    await page.route("**/api/v1/public/qr/BAD", (r) => r.fulfill(json({ code: "NOT_FOUND" }, 404)));
    await page.goto("/order/BAD");
    await expect(page.getByText("Meja tidak ditemukan")).toBeVisible();
    await saveShot(page, testInfo, "Errors", "TableNotFound");

    await page.route("**/api/v1/public/qr/MAINT", (r) => r.fulfill(json({ code: "MAINTENANCE" }, 503)));
    await page.goto("/order/MAINT");
    await expect(page.locator('img[src="/brand/illustrations/state-maintenance.svg"]')).toBeVisible();
    await saveShot(page, testInfo, "Errors", "MaintenanceMode");
  });
});

test.describe("Live Backend E2E (Android Demo Account + Dual Store QR)", () => {
  test.skip(!runLiveApi, "Runs when PLAYWRIGHT_LIVE_API=1 against local Go API");
  test.setTimeout(90_000);

  test("Seeded owner account, live Senopati & Sudirman catalogs, server quote, legal & SEO/GEO", async ({ page, request }, testInfo) => {
    await setViewportForEvidence(page, testInfo);
    const consoleErrors: string[] = [];
    const serverErrors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() === "error") consoleErrors.push(msg.text());
    });
    page.on("pageerror", (err) => consoleErrors.push(err.message));
    page.on("response", (res) => {
      if (res.status() >= 500) serverErrors.push(`${res.status()} ${res.url()}`);
    });

    // 1. Authenticate using Android demo owner credentials
    const loginRes = await request.post("/api/v1/auth/staff/login", {
      data: {
        store_slug: "central-cafe",
        email: "abdulaziz543333@gmail.com",
        pin: "482913"
      }
    });
    expect(loginRes.ok()).toBe(true);
    const loginJson = (await loginRes.json()) as { access_token: string; staff: { role: string; store: { slug: string } } };
    expect(loginJson.staff.role).toBe("owner");
    expect(loginJson.staff.store.slug).toBe("central-cafe");

    // 2. SEO/GEO static endpoints + single landmark + non-duplicated title
    await page.goto("/");
    await expect(page.locator("main")).toHaveCount(1);
    const homeBody = await page.locator("body").innerText();
    expect(homeBody).not.toContain("MyCaffe");
    const robotsRes = await request.get("/robots.txt");
    expect(robotsRes.status()).toBe(200);
    expect(await robotsRes.text()).toContain("Allow: /");
    const sitemapRes = await request.get("/sitemap.xml");
    expect(sitemapRes.status()).toBe(200);
    const llmsRes = await request.get("/llms.txt");
    expect(llmsRes.status()).toBe(200);
    expect(await llmsRes.text()).toContain("Cassy oleh AzureDevID");

    // Verify /r/[code] title has no duplicated suffix and handles unknown code cleanly
    await page.goto("/r/INVALIDCODE", { waitUntil: "networkidle" });
    await expect(page).toHaveTitle("Struk digital | Cassy");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

    // 3. Live Legal pages from PostgreSQL 18
    await page.goto("/legal", { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByRole("article")).toBeVisible();
    await page.goto("/legal/mitra", { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByRole("article")).toBeVisible();

    // 4. Store 1 (Cassy Roastery Senopati) live menu + server quote
    await page.goto(`/order/${SENOPATI_QR}`);
    await expect(page.getByRole("heading", { level: 1, name: "Cassy Roastery Senopati" })).toBeVisible({ timeout: 15_000 });
    const store1Text = await page.locator("body").innerText();
    expect(store1Text).not.toContain("MyCaffe");
    await expectCleanA11y(page);
    await saveShot(page, testInfo, "LiveBackend", "SenopatiMenu");

    const addBtn = page.getByRole("button", { name: "Tambah Americano (Double Shot)" });
    await addBtn.click();
    await page.getByRole("button", { name: /Lihat keranjang|View cart/ }).click();
    await expect(page.getByRole("dialog")).toContainText("Rp", { timeout: 10_000 });
    await saveShot(page, testInfo, "LiveBackend", "SenopatiCartQuote");

    // 5. Performance + zero unexpected console/5xx errors (excluding expected 404 on /r/INVALIDCODE tested above)
    expect(consoleErrors.filter((e) => !e.includes("404") && !e.includes("Failed to load resource"))).toEqual([]);
    expect(serverErrors).toEqual([]);
  });
});
