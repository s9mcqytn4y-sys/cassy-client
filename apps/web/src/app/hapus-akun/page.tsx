import { siteUrl } from "@/lib/site";
import type { Metadata } from "next";
import { DeleteAccountView } from "./DeleteAccountView";

const description =
  "Pemilik toko dapat meminta penghapusan akun Cassy dengan kode verifikasi email. Masa tenggang 30 hari; catatan transaksi dan pajak disimpan tanpa data pribadi.";

export const metadata: Metadata = {
  title: "Hapus Akun Toko",
  description,
  alternates: { canonical: "/hapus-akun" },
  robots: { index: true, follow: true },
  openGraph: {
    title: "Hapus Akun Toko | Cassy oleh AzureDevID",
    description,
    url: `${siteUrl}/hapus-akun`,
    siteName: "Cassy oleh AzureDevID",
    locale: "id_ID",
    type: "website"
  }
};

export default function DeleteAccountPage() {
  return <DeleteAccountView />;
}
