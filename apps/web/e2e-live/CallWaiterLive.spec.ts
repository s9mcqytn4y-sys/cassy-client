import { expect, test } from "@playwright/test";
import { api, openStoreForGuests } from "./LiveShift";

// PWA-05 (PrdOpsAndPwaClose.md section 12, S4-Q7): a guest calls the waiter from a real table, the POS gets
// the WebSocket event, a second call is held by the cooldown, and once staff cancel the request the table is
// muted. Table U2 is used only here: the mute lasts 15 minutes on that table session.
const TABLE = "e2e-kasir-meja-u2-qrcode";
const wsBase = (process.env.DIRECT_API_URL || "http://127.0.0.1:18081/api/v1").replace(/^http/, "ws").replace(/\/api\/v1$/, "");

test("call waiter reaches the POS over WebSocket, then cooldown and staff mute hold the table", async ({ page, request }) => {
  const store = await openStoreForGuests(request);
  const auth = { Authorization: `Bearer ${store.cashier}` };
  const ticket = (await (await request.post(`${api}/pos/ws-ticket`, { headers: auth })).json()).ticket as string;
  const events: string[] = [];
  const socket = new WebSocket(`${wsBase}/ws?ticket=${encodeURIComponent(ticket)}`);
  socket.addEventListener("message", (e) => events.push(String(e.data)));
  try {
    await expect.poll(() => socket.readyState, { message: "POS WebSocket connects" }).toBe(WebSocket.OPEN);

    await page.goto(`/order/${TABLE}`);
    await page.getByRole("button", { name: "Panggil pelayan" }).click();
    const sheet = page.getByRole("dialog", { name: "Butuh bantuan?" });
    await sheet.getByRole("button", { name: "Minta Tisu" }).click();
    await expect(page.getByText("Permintaan terkirim. Staf segera datang.")).toBeVisible();

    await expect
      .poll(() => events.some((e) => e.includes("service_request.created") && e.includes("Minta Tisu")), {
        message: "the POS receives service_request.created"
      })
      .toBe(true);

    await page.getByRole("button", { name: "Panggil pelayan" }).click();
    await expect(page.getByTestId("call-waiter-cooldown")).toBeVisible();
    await expect(page.getByRole("dialog", { name: "Butuh bantuan?" }).getByRole("button", { name: "Minta Tisu" })).toBeDisabled();

    const open = (await (await request.get(`${api}/store/service-requests`, { headers: auth })).json()) as {
      service_requests: {
        id: string;
        label: string;
        table_number: string;
        status: string;
      }[];
    };
    const call = open.service_requests.find((r) => r.label === "Minta Tisu" && r.table_number === "U2" && r.status === "open")!;
    expect(
      (
        await request.post(`${api}/store/service-requests/${call.id}/cancel`, {
          headers: auth
        })
      ).ok()
    ).toBe(true);

    // Same guest session (page cookies): any further call from this table is muted by staff.
    const templates = (await (await page.request.get("/api/v1/public/guest/service-request-templates")).json()).templates as {
      id: string;
      label: string;
    }[];
    const muted = await page.request.post("/api/v1/public/guest/service-requests", {
      headers: { "X-CSRF-Protection": "1" },
      data: {
        template_id: templates.find((t) => t.label === "Minta Air Putih")!.id
      },
      failOnStatusCode: false
    });
    expect(muted.status()).toBe(429);
    expect((await muted.json()).error).toContain("diredam");
  } finally {
    socket.close();
    await store.close();
  }
});
