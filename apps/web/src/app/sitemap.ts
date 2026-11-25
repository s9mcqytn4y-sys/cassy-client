import type { MetadataRoute } from "next";
import { MARKETING_UPDATED } from "@/components/Marketing";
import { siteUrl } from "@/lib/site";

// Only public pages; table QR links stay private. /hapus-akun is the Play Store account deletion page (FL-Q9).
const PAGES = [
  "/",
  "/fitur",
  "/harga",
  "/faq",
  "/panduan/memilih-kasir-kafe",
  "/banding/kasir-langganan-vs-cassy",
  "/legal",
  "/legal/mitra",
  "/hapus-akun"
];
const MARKETING = new Set(["/fitur", "/harga", "/faq", "/panduan/memilih-kasir-kafe", "/banding/kasir-langganan-vs-cassy"]);

export default function sitemap(): MetadataRoute.Sitemap {
  return PAGES.map((path) => ({
    url: new URL(path, siteUrl).toString(),
    ...(MARKETING.has(path) ? { lastModified: MARKETING_UPDATED } : {})
  }));
}
