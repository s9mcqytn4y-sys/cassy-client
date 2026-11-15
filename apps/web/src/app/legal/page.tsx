import { siteUrl } from "@/lib/site";
import type { Metadata } from "next";
import { Suspense } from "react";
import { LegalView } from "./LegalView";

export const metadata: Metadata = {
  title: "Ketentuan dan Privasi Tamu",
  description:
    "Pemberitahuan privasi UU PDP No. 27/2022 dan ketentuan pemesanan bagi tamu yang memesan dari meja lewat kode QR Cassy oleh AzureDevID.",
  alternates: { canonical: "/legal" },
  robots: { index: true, follow: true },
  openGraph: {
    title: "Ketentuan dan Privasi Tamu | Cassy oleh AzureDevID",
    description:
      "Pemberitahuan privasi UU PDP No. 27/2022 dan ketentuan pemesanan bagi tamu yang memesan dari meja lewat kode QR Cassy.",
    url: `${siteUrl}/legal`,
    siteName: "Cassy oleh AzureDevID",
    locale: "id_ID",
    type: "website",
    images: [{ url: "/AndroidChrome512.png", width: 512, height: 512, alt: "Cassy oleh AzureDevID" }]
  },
  twitter: {
    card: "summary",
    title: "Ketentuan dan Privasi Tamu | Cassy",
    description:
      "Pemberitahuan privasi dan ketentuan pemesanan bagi tamu yang memesan dari meja lewat kode QR Cassy.",
    images: ["/AndroidChrome512.png"]
  }
};

const legalGuestJsonLd = {
  "@context": "https://schema.org",
  "@type": "WebPage",
  name: "Ketentuan dan Privasi Tamu | Cassy",
  description:
    "Pemberitahuan privasi UU PDP No. 27/2022 dan ketentuan pemesanan bagi tamu yang memesan dari meja lewat kode QR Cassy.",
  url: `${siteUrl}/legal`,
  inLanguage: "id-ID",
  publisher: {
    "@type": "Organization",
    name: "AzureDevID",
    url: siteUrl
  }
};

export default function LegalPage() {
  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(legalGuestJsonLd) }}
      />
      <Suspense>
        <LegalView audience="guest" />
      </Suspense>
    </>
  );
}
