import { expect, type APIRequestContext } from "@playwright/test";

/** Direct API port (never cut by Toxiproxy) and the seeded e2e store (backend db/seeds/E2eLifecycle.sql). */
export const api = process.env.DIRECT_API_URL || "http://127.0.0.1:18081/api/v1";
export const E2E_TABLE = "e2e-kasir-meja-u1-qrcode";

export async function staffLogin(request: APIRequestContext, email: string, pin: string): Promise<string> {
  const res = await request.post(`${api}/auth/staff/login`, {
    data: { store_slug: "e2e-kasir", email, pin }
  });
  expect(res.ok(), await res.text()).toBe(true);
  return (await res.json()).access_token;
}

/**
 * Guest QR ordering is closed while the store has no open shift (`QRClosedNoShift`). Opens one on
 * "Laci Uji 2" (closing a leftover from an earlier run) and returns a function that force-closes it.
 */
export async function openStoreForGuests(request: APIRequestContext): Promise<{ cashier: string; close: () => Promise<void> }> {
  const cashier = await staffLogin(request, "cashier.e2e@cassy.local", "528307");
  const manager = await staffLogin(request, "manager.e2e@cassy.local", "739164");
  const auth = (token: string) => ({ Authorization: `Bearer ${token}` });
  const drawers = (await (await request.get(`${api}/pos/drawers`, { headers: auth(cashier) })).json()).drawers as {
    id: string;
    name: string;
  }[];
  const drawer = drawers.find((d) => d.name === "Laci Uji 2")!;
  const forceClose = (id: string, reason: string) =>
    request.post(`${api}/pos/shifts/${id}/force-close`, {
      headers: auth(manager),
      data: { reason },
      failOnStatusCode: false
    });

  const leftover = await request.get(`${api}/pos/shifts/current?drawer_id=${drawer.id}`, {
    headers: auth(cashier),
    failOnStatusCode: false
  });
  if (leftover.ok()) {
    const shift = await leftover.json();
    if (shift.id) await forceClose(shift.id, "Sisa percobaan uji e2e sebelumnya");
  }
  const opened = await request.post(`${api}/pos/shifts`, {
    headers: auth(cashier),
    data: { drawer_id: drawer.id, opening_float: 100_000 }
  });
  expect(opened.ok(), await opened.text()).toBe(true);
  const shiftId = (await opened.json()).shift.id as string;
  return {
    cashier,
    close: async () => void (await forceClose(shiftId, "Akhir uji tamu e2e"))
  };
}
