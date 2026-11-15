import type { Metadata } from "next";
import { ReceiptView } from "@/features/receipt/ReceiptView";

export const metadata: Metadata = {
  title: "Struk Digital | Cassy",
  robots: { index: false, follow: false },
  referrer: "no-referrer"
};

export default async function ShortReceiptPage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  return <ReceiptView token={code} mode="short" />;
}
