import type { MetadataRoute } from "next";
import { siteUrl } from "@/lib/site";

export default function robots(): MetadataRoute.Robots {
  const disallow = ["/order/", "/receipts/", "/r/", "/bagian/", "/antrean", "/api/"];
  const aiAllow = ["/", "/fitur", "/harga", "/faq", "/panduan/", "/banding/", "/legal", "/legal/mitra", "/llms.txt", "/llms-full.txt"];
  return {
    rules: [
      { userAgent: "*", allow: "/", disallow },
      { userAgent: "GPTBot", allow: aiAllow, disallow },
      { userAgent: "ClaudeBot", allow: aiAllow, disallow },
      { userAgent: "PerplexityBot", allow: aiAllow, disallow },
      { userAgent: "Google-Extended", allow: aiAllow, disallow }
    ],
    sitemap: new URL("/sitemap.xml", siteUrl).toString()
  };
}
