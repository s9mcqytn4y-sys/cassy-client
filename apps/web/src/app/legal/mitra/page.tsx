import { siteUrl } from "@/lib/site";
import type { Metadata } from "next";
import { Suspense } from "react";
import { LegalView } from "../LegalView";

export const metadata: Metadata = {
  title: "Dokumen untuk Mitra Gerai",
  description:
    "Syarat layanan SaaS, kebijakan privasi UU PDP No. 27/2022, pengembalian dana, dan perjanjian pemrosesan data (DPA) bagi pemilik gerai pengguna Cassy.",
  alternates: { canonical: "/legal/mitra" },
  robots: { index: true, follow: true },
  openGraph: {
    title: "Dokumen Hukum Mitra Gerai | Cassy oleh AzureDevID",
    description:
      "Syarat layanan SaaS, kebijakan privasi UU PDP No. 27/2022, dan perjanjian pemrosesan data bagi pemilik gerai pengguna Cassy.",
    url: `${siteUrl}/legal/mitra`,
    siteName: "Cassy oleh AzureDevID",
    locale: "id_ID",
    type: "website",
    images: [{ url: "/AndroidChrome512.png", width: 512, height: 512, alt: "Cassy oleh AzureDevID" }]
  },
  twitter: {
    card: "summary",
    title: "Dokumen Hukum Mitra Gerai | Cassy",
    description:
      "Syarat layanan, kebijakan privasi, dan perjanjian pemrosesan data bagi pemilik gerai pengguna Cassy.",
    images: ["/AndroidChrome512.png"]
  }
};

const legalPartnerJsonLd = {
  "@context": "https://schema.org",
  "@type": "WebPage",
  name: "Dokumen untuk Mitra Gerai | Cassy",
  description:
    "Syarat layanan SaaS, kebijakan privasi UU PDP No. 27/2022, pengembalian dana, dan perjanjian pemrosesan data bagi pemilik gerai pengguna Cassy.",
  url: `${siteUrl}/legal/mitra`,
  inLanguage: "id-ID",
  publisher: {
    "@type": "Organization",
    name: "AzureDevID",
    url: siteUrl
  }
};

export default function LegalPartnerPage() {
  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(legalPartnerJsonLd) }}
      />
      <Suspense>
        <LegalView audience="tenant" />
      </Suspense>
    </>
  );
}
