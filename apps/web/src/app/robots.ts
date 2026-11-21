import type { MetadataRoute } from "next";
import { siteUrl } from "@/lib/site";

export default function robots(): MetadataRoute.Robots {
  const disallow = ["/order/", "/receipts/", "/r/", "/bagian/", "/antrean", "/api/"];
  return {
    rules: [
      { userAgent: "*", allow: "/", disallow },
      { userAgent: "GPTBot", allow: ["/", "/legal", "/legal/mitra", "/llms.txt", "/llms-full.txt"], disallow },
      { userAgent: "ClaudeBot", allow: ["/", "/legal", "/legal/mitra", "/llms.txt", "/llms-full.txt"], disallow },
      { userAgent: "PerplexityBot", allow: ["/", "/legal", "/legal/mitra", "/llms.txt", "/llms-full.txt"], disallow },
      { userAgent: "Google-Extended", allow: ["/", "/legal", "/legal/mitra", "/llms.txt", "/llms-full.txt"], disallow }
    ],
    sitemap: new URL("/sitemap.xml", siteUrl).toString()
  };
}
