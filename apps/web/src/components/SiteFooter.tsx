"use client";

import Link from "next/link";
import { CassyWordmark } from "./CassyLogo";
import { useLocale } from "./Providers";

export function SiteFooter() {
  const { t, toggleLocale, hidePoweredBy } = useLocale();
  return (
    <footer className="mt-8 flex flex-col items-center gap-1 border-t border-subtle px-5 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-5 text-xs text-subink print:hidden">
      {!hidePoweredBy ? (
        <Link
          href="/legal/mitra"
          className="inline-flex min-h-[44px] items-center gap-1.5 underline-offset-2 hover:underline"
        >
          <span>{t.footerPowered}</span>
          <CassyWordmark height={14} />
        </Link>
      ) : null}
      <div className="flex items-center gap-4">
        <Link href="/legal" className="inline-flex min-h-[44px] items-center underline underline-offset-2">
          {t.legal}
        </Link>
        <button type="button" onClick={toggleLocale} className="min-h-[44px] cursor-pointer underline underline-offset-2">
          {t.language}
        </button>
      </div>
    </footer>
  );
}
