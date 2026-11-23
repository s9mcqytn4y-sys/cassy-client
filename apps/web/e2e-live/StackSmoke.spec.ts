import { expect, test } from "@playwright/test";

const printerApi = process.env.ESCPOS_SINK_URL || "http://127.0.0.1:9101";
const toxiproxyApi = process.env.TOXIPROXY_URL || "http://127.0.0.1:8474";

// S0 exit check: the PWA, the real API (through Toxiproxy) and the virtual printer are wired up.
test("PWA reaches the real API through the Next.js rewrite", async ({ request }) => {
  const health = await request.get("/api/v1/health");
  expect(health.status()).toBe(200);
  expect(await health.json()).toMatchObject({ status: "healthy", database: "connected" });

  const catalog = await request.get("/api/v1/public/stores/central-cafe/catalog");
  expect(catalog.status()).toBe(200);
});

test("API outage injected by Toxiproxy is visible and recoverable", async ({ request }) => {
  // Toxiproxy rejects browser user agents (CSRF guard), so the toggle must not look like one.
  const toggle = async (enabled: boolean) => {
    const res = await request.post(`${toxiproxyApi}/proxies/cassy_api`, {
      data: { enabled },
      headers: { "User-Agent": "cassy-e2e" }
    });
    expect(res.ok()).toBe(true);
  };
  try {
    await toggle(false);
    const down = await request.get("/api/v1/health", { failOnStatusCode: false });
    expect(down.status()).toBeGreaterThanOrEqual(500);
  } finally {
    await toggle(true);
  }
  expect((await request.get("/api/v1/health")).status()).toBe(200);
});

test("virtual ESC/POS printer is reachable", async ({ request }) => {
  const health = await request.get(`${printerApi}/health`);
  expect(health.status()).toBe(200);
  expect(Array.isArray(await (await request.get(`${printerApi}/jobs`)).json())).toBe(true);
});
