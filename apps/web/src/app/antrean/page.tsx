import type { Metadata } from "next";
import { QueueDisplay } from "@/features/queue/QueueDisplay";

export const metadata: Metadata = {
  title: "Layar antrean",
  robots: { index: false, follow: false },
  referrer: "no-referrer"
};

export default function QueuePage() {
  return <QueueDisplay />;
}
