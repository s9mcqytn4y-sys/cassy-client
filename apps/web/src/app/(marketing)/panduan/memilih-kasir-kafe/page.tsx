import type { Metadata } from "next";
import { Check } from "lucide-react";
import { AnswerBlock, Breadcrumbs, MARKETING_UPDATED, JsonLd, MarketingHeader, MarketingPage, TrialCta, marketingMetadata } from "@/components/Marketing";
import { MESSAGES } from "@/lib/messages";
import { siteUrl } from "@/lib/site";

export const revalidate = 86400;

const t = MESSAGES["id-ID"].mkt;
const PATH = "/panduan/memilih-kasir-kafe";

export const metadata: Metadata = marketingMetadata(PATH, t.guideTitle, t.guideDescription);

const articleJsonLd = {
  "@context": "https://schema.org",
  "@type": "Article",
  headline: t.guideTitle,
  description: t.guideDescription,
  inLanguage: "id-ID",
  datePublished: MARKETING_UPDATED,
  dateModified: MARKETING_UPDATED,
  mainEntityOfPage: new URL(PATH, siteUrl).toString(),
  author: { "@type": "Organization", name: "AzureDevID" },
  publisher: { "@type": "Organization", name: "AzureDevID", url: siteUrl }
};

export default function GuidePage() {
  return (
    <MarketingPage>
      <MarketingHeader />
      <Breadcrumbs trail={[{ name: t.guideHeadline, path: PATH }]} />
      <article className="max-w-3xl space-y-6">
        <h1 className="text-3xl font-bold tracking-tight text-balance sm:text-4xl">{t.guideHeadline}</h1>
        <AnswerBlock text={t.guideAnswer} />
        {t.guideSections.map((s) => (
          <section key={s.title} className="space-y-2">
            <h2 className="text-xl font-bold">{s.title}</h2>
            <p className="text-base leading-relaxed text-subink">{s.body}</p>
          </section>
        ))}
        <section className="rounded-xl border border-subtle bg-surface p-4">
          <h2 className="text-lg font-bold">{t.guideChecklistTitle}</h2>
          <ul className="mt-3 space-y-2">
            {t.guideChecklist.map((item) => (
              <li key={item} className="flex items-start gap-2 text-sm text-ink">
                <Check className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                {item}
              </li>
            ))}
          </ul>
        </section>
        <TrialCta />
      </article>
      <JsonLd data={articleJsonLd} />
    </MarketingPage>
  );
}
