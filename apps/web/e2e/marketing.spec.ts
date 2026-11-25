import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// FL1 W4 marketing pages: indexable, one h1, no serious axe violation, 320 px reflow, structured data.
const PAGES = ["/fitur", "/harga", "/faq", "/panduan/memilih-kasir-kafe", "/banding/kasir-langganan-vs-cassy"];

for (const path of PAGES) {
  test(`marketing ${path}`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 720 });
    await page.goto(path);
    await expect(page.locator("h1")).toHaveCount(1);
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /index, follow/);
    await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", new RegExp(`${path}$`));
    await expect(page.locator("time[datetime]")).toHaveCount(1);
    const types = await page.locator('script[type="application/ld+json"]').evaluateAll((nodes) =>
      nodes.flatMap((n) => [JSON.parse(n.textContent || "null")].flat().map((d) => d["@type"]))
    );
    expect(types).toContain("BreadcrumbList");
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(0);
    const { violations } = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
    expect(violations.filter((v) => v.impact === "serious" || v.impact === "critical").map((v) => v.id)).toEqual([]);
  });
}

test("harga carries Product offers and FAQPage with integer IDR prices", async ({ page }) => {
  await page.goto("/harga");
  const data = await page.locator('script[type="application/ld+json"]').evaluateAll((nodes) =>
    nodes.flatMap((n) => [JSON.parse(n.textContent || "null")].flat())
  );
  const products = data.filter((d) => d["@type"] === "Product");
  expect(products.length).toBe(4);
  for (const p of products) {
    expect(p.offers.priceCurrency).toBe("IDR");
    expect(p.offers.price).toMatch(/^\d+$/);
  }
  expect(data.some((d) => d["@type"] === "FAQPage")).toBe(true);
  await expect(page.getByRole("table")).toContainText("Starter UMKM");
});

test("sitemap lists the marketing routes and the account deletion page", async ({ request }) => {
  const xml = await (await request.get("/sitemap.xml")).text();
  for (const path of [...PAGES, "/hapus-akun"]) expect(xml).toContain(`${path}</loc>`);
  const llms = await (await request.get("/llms.txt")).text();
  expect(llms).toContain("starter_umkm");
});
