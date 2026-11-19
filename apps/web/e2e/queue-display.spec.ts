import fs from "node:fs/promises";
import path from "node:path";
import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { json } from "./mocks";

const TOKEN = "11111111-1111-4111-8111-111111111111.22222222-2222-4222-8222-222222222222.secret";

async function saveShot(page: Page, testInfo: TestInfo, state: string) {
  if (process.env.PLAYWRIGHT_CAPTURE_EVIDENCE !== "1") return;
  const dir = path.resolve(process.cwd(), "screenshots", "ui-v2.5.0", "QueueDisplay");
  await fs.mkdir(dir, { recursive: true });
  const suffix = testInfo.project.name === "mobile-safari" ? "Mobile" : "Desktop";
  await page.screenshot({ path: path.join(dir, `${state}${suffix}.png`), fullPage: true });
}

test("layar antrean memakai token dari tautan dan memisahkan nomor siap", async ({ page }, testInfo) => {
  let auth = "";
  await page.route("**/api/v1/public/queue", (r) => {
    auth = r.request().headers()["authorization"] ?? "";
    return r.fulfill(
      json({
        store_name: "Kopi Senja",
        orders: [
          { queue_number: "A012", status: "queued", updated_at: "2026-11-19T08:00:00Z" },
          { queue_number: "A013", status: "preparing", updated_at: "2026-11-19T08:01:00Z" },
          { queue_number: "A010", status: "ready", updated_at: "2026-11-19T07:58:00Z" }
        ]
      })
    );
  });
  await page.route("**/api/v1/public/queue/ws-ticket", (r) => r.fulfill(json({ error: "offline" }, 503)));
  if (testInfo.project.name !== "mobile-safari") await page.setViewportSize({ width: 1440, height: 900 });

  await page.goto(`/antrean#t=${TOKEN}`);
  await expect(page.getByRole("heading", { level: 1, name: "Kopi Senja" })).toBeVisible();
  expect(auth).toBe(`Display ${TOKEN}`);
  // The token leaves the address bar once it is stored.
  expect(page.url()).not.toContain("#t=");
  await expect(page.getByRole("region", { name: "Siap diambil" }).getByText("A010")).toBeVisible();
  await expect(page.getByRole("region", { name: "Sedang disiapkan" }).getByText("A013")).toBeVisible();
  const { violations } = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
  expect(violations.filter((v) => v.impact === "serious" || v.impact === "critical").map((v) => v.id)).toEqual([]);
  await saveShot(page, testInfo, "Board");
});

test("token dicabut kembali ke form kode layar dengan pesan server", async ({ page }, testInfo) => {
  await page.route("**/api/v1/public/queue", (r) =>
    r.fulfill(json({ code: "DISPLAY_TOKEN_INVALID", error: "Layar ini belum terdaftar atau sudah dicabut. Minta kode baru ke owner." }, 401))
  );
  await page.route("**/api/v1/public/queue/ws-ticket", (r) => r.fulfill(json({ error: "x" }, 401)));
  await page.goto(`/antrean#t=${TOKEN}`);
  await expect(page.getByText("Layar ini belum terdaftar atau sudah dicabut")).toBeVisible();
  await expect(page.getByLabel("Kode layar")).toBeVisible();
  await saveShot(page, testInfo, "TokenRevoked");
});
