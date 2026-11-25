import type { Metadata } from "next";
import { AnswerBlock, Breadcrumbs, MARKETING_UPDATED, FaqList, JsonLd, MarketingHeader, MarketingPage, PlanTable, TrialCta, faqItems, fill, marketingMetadata, paidPrices } from "@/components/Marketing";
import { MESSAGES } from "@/lib/messages";
import { loadPlans } from "@/lib/plans";

export const revalidate = 86400;

const t = MESSAGES["id-ID"].mkt;

export const metadata: Metadata = marketingMetadata("/fitur", t.featuresTitle, t.featuresDescription);

const SCREEN_SIZE: Record<string, { width: number; height: number }> = {
  PosTabletKasir: { width: 1280, height: 600 },
  KdsTabletDapur: { width: 1280, height: 800 },
  TamuPonselMenu: { width: 390, height: 844 },
  TamuPonselQris: { width: 390, height: 844 },
  TamuPonselStruk: { width: 390, height: 844 }
};

export default async function FeaturesPage() {
  const plans = await loadPlans();
  const prices = paidPrices(plans);
  return (
    <MarketingPage>
      <MarketingHeader />
      <Breadcrumbs trail={[{ name: t.navFeatures, path: "/fitur" }]} />

      <section className="grid items-center gap-6 md:min-h-[calc(100dvh-12rem)] md:grid-cols-[1fr_1.1fr]">
        <div className="space-y-4">
          <h1 className="text-3xl font-bold tracking-tight text-balance sm:text-4xl">{t.featuresHeadline}</h1>
          <AnswerBlock text={fill(t.featuresAnswer, { price: prices.starter })} />
          <TrialCta withPricing />
        </div>
        <div className="relative pb-6 pr-6">
          <img
            src="/marketing/PosTabletKasir.webp"
            alt={t.heroImageAlt}
            width={1280}
            height={600}
            fetchPriority="high"
            className="h-auto w-full rounded-2xl border border-subtle shadow-sm"
          />
          <img
            src="/marketing/TamuPonselQris.webp"
            alt={t.heroPhoneAlt}
            width={390}
            height={844}
            className="absolute bottom-0 right-0 h-auto w-[28%] rounded-2xl border border-subtle shadow-md"
          />
        </div>
      </section>

      <section aria-label={t.featureBlocksLabel} className="space-y-10">
        {t.features.map((f, i) => {
          const size = SCREEN_SIZE[f.image];
          const phone = size.width < 500;
          return (
            <article key={f.title} className={`grid items-center gap-5 md:grid-cols-2 ${i % 2 ? "md:[&>div:first-child]:order-2" : ""}`}>
              <div className={phone ? "mx-auto w-full max-w-[240px]" : ""}>
                <img
                  src={`/marketing/${f.image}.webp`}
                  alt={f.alt}
                  width={size.width}
                  height={size.height}
                  loading="lazy"
                  decoding="async"
                  className="h-auto w-full rounded-2xl border border-subtle"
                />
              </div>
              <div className="space-y-2">
                <h2 className="text-xl font-bold">{f.title}</h2>
                <p className="text-base leading-relaxed text-subink">{f.body}</p>
              </div>
            </article>
          );
        })}
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-bold">{t.plansSummaryTitle}</h2>
        <PlanTable plans={plans} />
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-bold">{t.faqHeadline}</h2>
        <FaqList items={faqItems(plans).slice(0, 4)} />
      </section>

      <JsonLd data={{ "@context": "https://schema.org", "@type": "WebPage", name: t.featuresTitle, description: t.featuresDescription, dateModified: MARKETING_UPDATED, inLanguage: "id-ID" }} />
    </MarketingPage>
  );
}
