import type { Metadata } from "next";
import { AnswerBlock, Breadcrumbs, FaqList, JsonLd, MarketingHeader, MarketingPage, PlanTable, TrialCta, faqItems, faqJsonLd, fill, marketingMetadata, paidPrices, planProductsJsonLd } from "@/components/Marketing";
import { MESSAGES } from "@/lib/messages";
import { loadPlans } from "@/lib/plans";

export const revalidate = 86400;

const t = MESSAGES["id-ID"].mkt;

export const metadata: Metadata = marketingMetadata("/harga", t.pricingTitle, t.pricingDescription);

export default async function PricingPage() {
  const plans = await loadPlans();
  const faq = faqItems(plans);
  return (
    <MarketingPage>
      <MarketingHeader />
      <Breadcrumbs trail={[{ name: t.navPricing, path: "/harga" }]} />
      <section className="space-y-4">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{t.pricingHeadline}</h1>
        <AnswerBlock text={fill(t.pricingAnswer, paidPrices(plans))} />
        <TrialCta />
      </section>
      <section className="space-y-3">
        <h2 className="text-xl font-bold">{t.plansSummaryTitle}</h2>
        <PlanTable plans={plans} />
        <p className="text-sm leading-relaxed text-subink">{t.pricingPaymentNote}</p>
      </section>
      <section className="space-y-3">
        <h2 className="text-xl font-bold">{t.faqHeadline}</h2>
        <FaqList items={faq} />
      </section>
      <JsonLd data={[...planProductsJsonLd(plans), faqJsonLd(faq)]} />
    </MarketingPage>
  );
}
