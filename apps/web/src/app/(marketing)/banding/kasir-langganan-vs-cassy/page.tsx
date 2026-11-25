import type { Metadata } from "next";
import { AnswerBlock, Breadcrumbs, MARKETING_UPDATED, JsonLd, MarketingHeader, MarketingPage, TrialCta, fill, marketingMetadata, paidPrices } from "@/components/Marketing";
import { MESSAGES } from "@/lib/messages";
import { loadPlans } from "@/lib/plans";

export const revalidate = 86400;

const t = MESSAGES["id-ID"].mkt;
const PATH = "/banding/kasir-langganan-vs-cassy";

export const metadata: Metadata = marketingMetadata(PATH, t.compareTitle, t.compareDescription);

// FL-Q11: compared by category only. No competitor names, logos or prices.
export default async function ComparePage() {
  const prices = paidPrices(await loadPlans());
  return (
    <MarketingPage>
      <MarketingHeader />
      <Breadcrumbs trail={[{ name: t.compareHeadline, path: PATH }]} />
      <section className="space-y-4">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{t.compareHeadline}</h1>
        <AnswerBlock text={fill(t.compareAnswer, { price: prices.starter })} />
      </section>
      <section className="space-y-3">
        <div tabIndex={0} role="region" aria-label={t.compareTitle} className="overflow-x-auto rounded-xl border border-subtle bg-surface">
          <table className="w-full min-w-[640px] border-collapse text-left text-sm">
            <caption className="sr-only">{t.compareTitle}</caption>
            <thead>
              <tr className="border-b border-strong">
                <th scope="col" className="px-3 py-3 text-xs font-semibold uppercase tracking-wide text-subink">{t.compareColumnAspect}</th>
                <th scope="col" className="px-3 py-3 font-bold text-ink">{t.compareColumnCommission}</th>
                <th scope="col" className="px-3 py-3 font-bold text-ink">{t.compareColumnSubscription}</th>
                <th scope="col" className="px-3 py-3 font-bold text-ink">{t.compareColumnCassy}</th>
              </tr>
            </thead>
            <tbody>
              {t.compareRows.map((r) => (
                <tr key={r.aspect} className="border-b border-subtle last:border-b-0">
                  <th scope="row" className="px-3 py-2.5 font-medium text-subink">{r.aspect}</th>
                  <td className="px-3 py-2.5 text-ink">{r.commission}</td>
                  <td className="px-3 py-2.5 text-ink">{r.subscription}</td>
                  <td className="px-3 py-2.5 font-semibold text-ink">{r.cassy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="text-sm leading-relaxed text-subink">{t.compareNote}</p>
      </section>
      <TrialCta />
      <JsonLd data={{ "@context": "https://schema.org", "@type": "WebPage", name: t.compareTitle, description: t.compareDescription, dateModified: MARKETING_UPDATED, inLanguage: "id-ID" }} />
    </MarketingPage>
  );
}
