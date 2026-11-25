import type { Metadata } from "next";
import { AnswerBlock, Breadcrumbs, FaqList, JsonLd, MarketingHeader, MarketingPage, TrialCta, faqItems, faqJsonLd, marketingMetadata } from "@/components/Marketing";
import { MESSAGES } from "@/lib/messages";
import { loadPlans } from "@/lib/plans";

export const revalidate = 86400;

const t = MESSAGES["id-ID"].mkt;

export const metadata: Metadata = marketingMetadata("/faq", t.faqTitle, t.faqDescription);

export default async function FaqPage() {
  const faq = faqItems(await loadPlans());
  return (
    <MarketingPage>
      <MarketingHeader />
      <Breadcrumbs trail={[{ name: t.navFaq, path: "/faq" }]} />
      <section className="space-y-4">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{t.faqHeadline}</h1>
        <AnswerBlock text={t.faqAnswer} />
      </section>
      <FaqList items={faq} />
      <TrialCta />
      <JsonLd data={faqJsonLd(faq)} />
    </MarketingPage>
  );
}
