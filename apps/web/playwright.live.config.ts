import { defineConfig, devices } from "@playwright/test";

/**
 * Live e2e against the real Docker test stack (cassy-docker Core/DockerComposeTest.yml).
 * Unlike playwright.config.ts, nothing is mocked: the app must be built with
 * BACKEND_INTERNAL_URL pointing at the Toxiproxy port (default http://127.0.0.1:18080),
 * because Next.js bakes rewrites in at build time.
 */
export default defineConfig({
  testDir: "./e2e-live",
  timeout: 60_000,
  expect: { timeout: 15_000 },
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: "list",
  use: {
    baseURL: process.env.PLAYWRIGHT_TEST_BASE_URL || "http://127.0.0.1:3000",
    trace: "retain-on-failure",
    serviceWorkers: "block"
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "pnpm start",
    url: "http://127.0.0.1:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  }
});
