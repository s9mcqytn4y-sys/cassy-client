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
    <main className="mx-auto flex min-h-screen w-full max-w-3xl flex-col px-5 py-12 text-ink">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData) }}
      />

      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-subtle pb-6">
        <CassyWordmark height={24} />
        <nav aria-label="Tautan informasi" className="flex flex-wrap items-center gap-4 text-sm text-subink">
          <Link href="/legal" className="inline-flex min-h-[44px] items-center underline underline-offset-4 hover:text-ink">
            Privasi Tamu
          </Link>
          <Link href="/legal/mitra" className="inline-flex min-h-[44px] items-center underline underline-offset-4 hover:text-ink">
            Dokumen Mitra
          </Link>
        </nav>
      </header>

      <section className="mt-8 space-y-4">
        <p className="inline-flex rounded-full bg-muted px-3 py-1 text-xs font-semibold tracking-wide text-subink">
          POS Tablet Android &amp; QR Meja F&amp;B Indonesia
        </p>
        <h1 className="text-3xl font-bold tracking-tight text-ink sm:text-4xl">
          Pesan dan bayar dari meja, terhubung langsung ke kasir gerai.
        </h1>
        <p className="text-base leading-relaxed text-subink">
          <strong>Cassy</strong> oleh AzureDevID membantu kafe, roastery, restoran, bakery, dan gerai kuliner menerima
          pesanan QR meja tanpa aplikasi tambahan, mencetak tiket dapur ESC/POS secara otomatis, dan mengirim struk
          digital resmi berbasis tautan aman.
        </p>
      </section>

      <section aria-label="Cara kerja Cassy" className="mt-8 grid gap-4 sm:grid-cols-3">
        <article className="rounded-2xl border border-subtle bg-surface p-5">
          <h2 className="text-sm font-semibold text-ink">1. Pindai QR Meja</h2>
          <p className="mt-2 text-sm leading-relaxed text-subink">
            Tamu memindai kode QR di meja gerai untuk membuka daftar menu resmi langsung di peramban.
          </p>
        </article>
        <article className="rounded-2xl border border-subtle bg-surface p-5">
          <h2 className="text-sm font-semibold text-ink">2. Harga Resmi Server</h2>
          <p className="mt-2 text-sm leading-relaxed text-subink">
            Sub-total, pajak, biaya layanan, dan status pembayaran divalidasi penuh oleh server gerai.
          </p>
        </article>
        <article className="rounded-2xl border border-subtle bg-surface p-5">
          <h2 className="text-sm font-semibold text-ink">3. Struk Digital &amp; PDF</h2>
          <p className="mt-2 text-sm leading-relaxed text-subink">
            Bukti bayar tersedia sebagai halaman web ringkas maupun berkas PDF melalui tautan terverifikasi.
          </p>
        </article>
      </section>

      <section aria-label="Pertanyaan umum" className="mt-8 rounded-2xl border border-subtle bg-surface p-6">
        <h2 className="text-base font-semibold text-ink">Ringkasan Pertanyaan Umum</h2>
        <dl className="mt-4 space-y-4 text-sm">
          <div>
            <dt className="font-semibold text-ink">Apakah tamu harus menginstal aplikasi?</dt>
            <dd className="mt-1 text-subink">
              Tidak. Halaman pemesanan tamu terbuka langsung sebagai Progressive Web App di peramban ponsel.
            </dd>
          </div>
          <div>
            <dt className="font-semibold text-ink">Bagaimana jika kasir ingin memakai jaringan lokal gerai?</dt>
            <dd className="mt-1 text-subink">
              Tablet Android Cassy POS mendukung endpoint Cloud HTTPS maupun server lokal via mDNS di Wi-Fi toko.
            </dd>
          </div>
        </dl>
      </section>

      <footer className="mt-auto pt-10 text-xs text-subink">
        <div className="flex flex-wrap items-center justify-between gap-4 border-t border-subtle pt-6">
          <p>Cassy oleh AzureDevID, Jakarta.</p>
          <div className="flex items-center gap-4">
            <Link href="/legal" className="inline-flex min-h-[44px] items-center underline underline-offset-2">
              Ketentuan &amp; Privasi
            </Link>
            <Link href="/legal/mitra" className="inline-flex min-h-[44px] items-center underline underline-offset-2">
              Mitra Gerai
            </Link>
            <a href="/llms.txt" className="inline-flex min-h-[44px] items-center underline underline-offset-2">
              llms.txt
            </a>
          </div>
        </div>
      </footer>
    </main>
  );
}
