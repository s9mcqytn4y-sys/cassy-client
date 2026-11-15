import type { Metadata } from "next";
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
    operatingSystem: "Android, Web",
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
  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-5 px-5 py-6 text-ink">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData) }}
      />

      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-subtle pb-4">
        <CassyWordmark height={24} />
        <nav aria-label="Tautan informasi" className="flex flex-wrap items-center gap-4 text-sm text-subink">
          <Link href="/legal" className="inline-flex min-h-[44px] items-center underline underline-offset-4 hover:text-ink">
            Privasi Tamu
          </Link>
          <Link href="/legal/mitra" className="inline-flex min-h-[44px] items-center underline underline-offset-4 hover:text-ink">
            Dokumen Mitra
          </Link>
          <a href="/llms.txt" className="inline-flex min-h-[44px] items-center underline underline-offset-4 hover:text-ink">
            llms.txt
          </a>
        </nav>
      </header>

      <section className="space-y-2.5">
        <h1 className="text-2xl font-bold tracking-tight text-ink sm:text-3xl">
          Pesan dan bayar dari meja, terhubung ke kasir gerai.
        </h1>
        <p className="text-sm leading-relaxed text-subink">
          <strong>Cassy</strong> oleh AzureDevID adalah platform kasir POS tablet Android dan pemesanan QR meja untuk
          kafe, restoran, dan bakery di Indonesia. Semua total dihitung server dan struk digital dikirim aman.
        </p>
      </section>

      <section aria-label="Cara kerja Cassy" className="grid gap-3 sm:grid-cols-3">
        <article className="rounded-xl border border-subtle bg-surface p-3.5">
          <h2 className="text-sm font-semibold text-ink">1. Pindai QR Meja</h2>
          <p className="mt-1 text-xs leading-relaxed text-subink">
            Buka menu langsung di peramban tanpa instal aplikasi.
          </p>
        </article>
        <article className="rounded-xl border border-subtle bg-surface p-3.5">
          <h2 className="text-sm font-semibold text-ink">2. Harga Resmi Server</h2>
          <p className="mt-1 text-xs leading-relaxed text-subink">
            Pajak, layanan, dan status bayar divalidasi penuh oleh server gerai.
          </p>
        </article>
        <article className="rounded-xl border border-subtle bg-surface p-3.5">
          <h2 className="text-sm font-semibold text-ink">3. Struk Digital &amp; PDF</h2>
          <p className="mt-1 text-xs leading-relaxed text-subink">
            Tersedia sebagai halaman ringkas dan berkas PDF lewat tautan aman.
          </p>
        </article>
      </section>
    </div>
  );
}
