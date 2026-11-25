import type { Metadata } from "next";
import { MESSAGES } from "@/lib/messages";
import Link from "next/link";
import { CassyWordmark } from "@/components/CassyLogo";
import { siteUrl } from "@/lib/site";

export const metadata: Metadata = {
  title: { absolute: "Cassy: pesan dan bayar dari meja" },
  description:
    "Platform kasir (POS) tablet Android dan pemesanan QR meja untuk kafe, restoran, kedai kopi, bakery, food court, dan cloud kitchen di Indonesia.",
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    title: "Cassy oleh AzureDevID: Kasir POS dan Pemesanan QR Meja F&B",
    description:
      "Sistem kasir F&B Indonesia dengan pemesanan QR tamu tanpa unduh aplikasi, QRIS Dinamis, cetak dapur ESC/POS, dan struk digital.",
    siteName: "Cassy oleh AzureDevID",
    images: [{ url: "/AndroidChrome512.png", width: 512, height: 512, alt: "Cassy oleh AzureDevID" }]
  },
  twitter: {
    card: "summary",
    title: "Cassy: Kasir POS dan Pemesanan QR Meja",
    description: "Platform kasir F&B dan pemesanan QR meja buatan AzureDevID, Indonesia."
  },
  robots: { index: true, follow: true }
};

const structuredData = [
  {
    "@context": "https://schema.org",
    "@type": "Organization",
    name: "AzureDevID",
    brand: "Cassy",
    url: siteUrl,
    logo: new URL("/AndroidChrome512.png", siteUrl).toString(),
    address: {
      "@type": "PostalAddress",
      addressLocality: "Jakarta",
      addressCountry: "ID"
    }
  },
  {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: "Cassy POS & Guest QR Ordering",
    applicationCategory: "BusinessApplication",
    operatingSystem: "Android, iOS, iPadOS, Web",
    inLanguage: ["id", "en"],
    offers: {
      "@type": "Offer",
      priceCurrency: "IDR",
      price: "0",
      description: "Aplikasi tamu QR web gratis untuk pelanggan; paket langganan SaaS tersedia untuk mitra gerai F&B."
    },
    description:
      "Aplikasi kasir POS tablet Android dan halaman pemesanan QR meja untuk bisnis kuliner Indonesia, dengan kalkulasi harga terpusat di server dan kepatuhan UU PDP No. 27/2022."
  },
  {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: [
      {
        "@type": "Question",
        name: "Apa itu Cassy?",
        acceptedAnswer: {
          "@type": "Answer",
          text: "Cassy oleh AzureDevID adalah platform kasir POS tablet Android dan pemesanan QR meja berbasis web untuk bisnis kuliner di Indonesia seperti kafe, roastery, restoran, dan bakery."
        }
      },
      {
        "@type": "Question",
        name: "Bagaimana cara tamu memesan dan membayar di meja?",
        acceptedAnswer: {
          "@type": "Answer",
          text: "Tamu cukup memindai kode QR di meja dengan kamera ponsel, memilih menu, lalu membayar dengan QRIS Dinamis, Virtual Account, atau di kasir tanpa perlu menginstal aplikasi."
        }
      },
      {
        "@type": "Question",
        name: "Bagaimana Cassy menjaga keamanan transaksi dan privasi data?",
        acceptedAnswer: {
          "@type": "Answer",
          text: "Semua harga, pajak, dan total dihitung di server PostgreSQL 18 dengan isolasi Row-Level Security antar gerai serta pemrosesan data pribadi sesuai UU PDP No. 27 Tahun 2022."
        }
      }
    ]
  }
];

export default function HomePage() {
  // Server component without the locale provider: the landing page renders in Indonesian.
  const t = MESSAGES["id-ID"];
  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-4 px-5 py-5 text-ink">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData) }}
      />

      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-subtle pb-4">
        <CassyWordmark height={24} />
        <nav aria-label={t.homeNavLabel} className="flex flex-wrap items-center gap-2 text-sm">
          <Link
            href="/fitur"
            className="inline-flex min-h-[48px] items-center rounded-xl border border-subtle bg-surface px-3.5 font-semibold text-ink transition hover:border-brand"
          >
            {t.mkt.navFeatures}
          </Link>
          <Link
            href="/harga"
            className="inline-flex min-h-[48px] items-center rounded-xl border border-subtle bg-surface px-3.5 font-semibold text-ink transition hover:border-brand"
          >
            {t.mkt.navPricing}
          </Link>
          <Link
            href="/legal"
            className="inline-flex min-h-[48px] items-center rounded-xl border border-subtle bg-surface px-3.5 font-semibold text-ink transition hover:border-brand"
          >
            {t.homeGuestPrivacy}
          </Link>
          <Link
            href="/legal/mitra"
            className="inline-flex min-h-[48px] items-center rounded-xl bg-brand px-3.5 font-semibold text-brand-fg transition hover:opacity-95"
          >
            {t.homePartners}
          </Link>
        </nav>
      </header>

      <section className="space-y-3">
        <div className="flex flex-wrap gap-2 text-xs font-semibold text-subink">
          <span className="rounded-full border border-subtle bg-surface px-3 py-1 text-ink">
            {t.homeBadgeServerPrice}
          </span>
          <span className="rounded-full border border-subtle bg-surface px-3 py-1 text-ink">
            {t.homeBadgePdp}
          </span>
        </div>
        <h1 className="text-2xl font-bold tracking-tight text-ink sm:text-3xl">
          {t.homeHeadline}
        </h1>
        <p className="text-sm leading-relaxed text-subink sm:text-base">
          <strong>Cassy</strong> {t.homeIntro}
        </p>
      </section>

      <section aria-label={t.homeHowLabel} className="grid gap-3 sm:grid-cols-3">
        <article className="rounded-xl border border-subtle bg-surface p-3.5">
          <h2 className="text-sm font-semibold text-ink">{t.homeStep1Title}</h2>
          <p className="mt-1 text-xs leading-relaxed text-subink">
            {t.homeStep1Body}
          </p>
        </article>
        <article className="rounded-xl border border-subtle bg-surface p-3.5">
          <h2 className="text-sm font-semibold text-ink">{t.homeStep2Title}</h2>
          <p className="mt-1 text-xs leading-relaxed text-subink">
            {t.homeStep2Body}
          </p>
        </article>
        <article className="rounded-xl border border-subtle bg-surface p-3.5">
          <h2 className="text-sm font-semibold text-ink">{t.homeStep3Title}</h2>
          <p className="mt-1 text-xs leading-relaxed text-subink">
            {t.homeStep3Body}
          </p>
        </article>
      </section>
    </div>
  );
}
