import { formatIdr } from "./format";
import { MESSAGES } from "./messages";
import { isTenMonthAnnual, type Plan } from "./plans";

const t = MESSAGES["id-ID"].mkt;

function limit(label: string, value: number | undefined): string[] {
  return value === undefined ? [] : [`${label} ${value === 0 ? t.unlimited : value}`];
}

/** Plain-text plan list for /llms.txt and /llms-full.txt, every number from the API. */
export function plansText(plans: Plan[], updated: string): string {
  const lines = plans.map((p) => {
    const price = p.monthly_price_idr === 0 ? t.free : `${formatIdr(p.monthly_price_idr)}${t.perMonth}`;
    const annual = p.annual_price_idr === 0 ? "" : `, ${formatIdr(p.annual_price_idr)}${t.perYear}`;
    const limits = [
      ...limit(t.rowTables, p.max_tables),
      ...limit(t.rowProducts, p.max_products),
      ...limit(t.rowStaff, p.max_staff),
      ...limit(t.rowDevices, p.max_devices),
      ...limit(t.rowPrinters, p.max_printers),
      ...(p.supports_kds_queue_tv ? (p.max_kds_stations === undefined ? [`${t.rowKds} ${t.included}`] : limit(t.rowKds, p.max_kds_stations)) : [`${t.rowKds} ${t.notIncluded}`])
    ];
    return `- ${p.name_id} (${p.code}): ${price}${annual}. ${limits.join(", ")}.`;
  });
  const notes = [
    "- Komisi transaksi 0%: pembayaran tamu masuk ke akun Midtrans milik gerai (BYOK).",
    "- Uji coba 14 hari tanpa kartu kredit; setelah berakhir akun menjadi baca-saja, data tidak dihapus.",
    ...(plans.some(isTenMonthAnnual) ? [`- ${t.tenMonthNote}`] : [])
  ];
  return [`## Harga Paket (diperbarui ${updated}, sumber: /harga)`, ...lines, ...notes].join("\n");
}
