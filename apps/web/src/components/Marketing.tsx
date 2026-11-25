import Link from "next/link";
import { Check, Minus } from "lucide-react";
import { CassyWordmark } from "@/components/CassyLogo";
import { formatIdr } from "@/lib/format";
import { MESSAGES } from "@/lib/messages";
import { isTenMonthAnnual, type Plan } from "@/lib/plans";
import { siteUrl } from "@/lib/site";

// Marketing pages are server components rendered in Indonesian, like the home page.
const t = MESSAGES["id-ID"].mkt;

/** Date the marketing copy was last reviewed; shown on every page (GEO) and used as dateModified. */
export const MARKETING_UPDATED = "2026-11-25";

/** Support inbox for trial requests until self-serve sign-up ships. */
export const TRIAL_HREF = `mailto:support@abbdullaziz.online?subject=${encodeURIComponent(t.ctaTrialSubject)}`;

/** Replaces `{name}` placeholders in a message. */
export function fill(text: string, values: Record<string, string | number>): string {
  return text.replace(/\{(\w+)\}/g, (_, key: string) => String(values[key] ?? `{${key}}`));
}

export function planByCode(plans: Plan[], code: Plan["code"]): Plan {
  const plan = plans.find((p) => p.code === code);
  if (!plan) throw new Error(`plan ${code} missing`);
  return plan;
}

/** Monthly prices of the paid plans, formatted, for copy placeholders. */
export function paidPrices(plans: Plan[]) {
  return {
    starter: formatIdr(planByCode(plans, "starter_umkm").monthly_price_idr),
    pro: formatIdr(planByCode(plans, "pro_cafe").monthly_price_idr),
    enterprise: formatIdr(planByCode(plans, "enterprise_whitelabel").monthly_price_idr)
  };
}

export function JsonLd({ data }: { data: object | object[] }) {
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(data) }} />;
}

const pill =
  "inline-flex min-h-[48px] items-center rounded-xl px-3.5 text-sm font-semibold transition";

export function MarketingHeader() {
  return (
    <header className="flex flex-wrap items-center justify-between gap-3 border-b border-subtle pb-4">
      <Link href="/" className="inline-flex min-h-[48px] items-center">
        <CassyWordmark height={24} />
      </Link>
      <nav aria-label={t.navLabel} className="flex flex-wrap items-center gap-1 text-sm">
        <Link href="/fitur" className={`${pill} text-ink hover:bg-muted`}>{t.navFeatures}</Link>
        <Link href="/harga" className={`${pill} text-ink hover:bg-muted`}>{t.navPricing}</Link>
        <Link href="/faq" className={`${pill} text-ink hover:bg-muted`}>{t.navFaq}</Link>
        <a href={TRIAL_HREF} className={`${pill} bg-brand text-brand-fg hover:opacity-95`}>{t.ctaTrial}</a>
      </nav>
    </header>
  );
}

export interface Crumb {
  name: string;
  path: string;
}

/** Visible trail plus BreadcrumbList JSON-LD. The home crumb is added here. */
export function Breadcrumbs({ trail }: { trail: Crumb[] }) {
  const items = [{ name: t.breadcrumbHome, path: "/" }, ...trail];
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((c, i) => ({
      "@type": "ListItem",
      position: i + 1,
      name: c.name,
      item: new URL(c.path, siteUrl).toString()
    }))
  };
  return (
    <nav aria-label={t.breadcrumbLabel} className="text-xs text-subink">
      <JsonLd data={jsonLd} />
      <ol className="flex flex-wrap items-center gap-1.5">
        {items.map((c, i) => (
          <li key={c.path} className="flex items-center gap-1.5">
            {i > 0 ? <span aria-hidden="true">/</span> : null}
            {i < items.length - 1 ? (
              <Link href={c.path} className="underline underline-offset-2 hover:text-ink">{c.name}</Link>
            ) : (
              <span aria-current="page" className="text-ink">{c.name}</span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}

/** Short factual answer at the top of a page (GEO) with the visible review date. */
export function AnswerBlock({ text }: { text: string }) {
  const date = new Intl.DateTimeFormat("id-ID", { dateStyle: "long", timeZone: "Asia/Jakarta" }).format(new Date(MARKETING_UPDATED));
  return (
    <section aria-label={t.answerLabel} className="rounded-xl border border-subtle bg-surface p-4">
      <p className="text-base leading-relaxed text-ink">{text}</p>
      <p className="mt-2 text-xs text-subink">
        {t.updated} <time dateTime={MARKETING_UPDATED}>{date}</time>
      </p>
    </section>
  );
}

export function limit(value: number): string {
  return value === 0 ? t.unlimited : String(value);
}

/** Numbers in the spec sheet: tabular mono, never wrapped. Words stay in the body font. */
function Num({ children }: { children: string }) {
  return <span className="whitespace-nowrap font-mono tabular-nums">{children}</span>;
}

function quotaCell(value: number) {
  return value === 0 ? t.unlimited : <Num>{String(value)}</Num>;
}

function priceCell(amount: number, suffix: string, zero: string) {
  return amount === 0 ? zero : <Num>{`${formatIdr(amount)}${suffix}`}</Num>;
}

function Flag({ on }: { on: boolean }) {
  return on ? (
    <span className="inline-flex items-center gap-1.5"><Check className="h-4 w-4 shrink-0" aria-hidden="true" />{t.included}</span>
  ) : (
    <span className="inline-flex items-center gap-1.5 text-subink"><Minus className="h-4 w-4 shrink-0" aria-hidden="true" />{t.notIncluded}</span>
  );
}

/** Spec-sheet table of every plan: thin rules, no zebra, tabular mono numbers. All values come from the API. */
export function PlanTable({ plans }: { plans: Plan[] }) {
  // A row whose value an older backend does not send is left out rather than guessed.
  const known = (pick: (p: Plan) => number | undefined) => plans.every((p) => pick(p) !== undefined);
  const quota = (label: string, pick: (p: Plan) => number | undefined) =>
    known(pick) ? [{ label, cell: (p: Plan) => quotaCell(pick(p) as number) }] : [];
  const rows: { label: string; cell: (p: Plan) => React.ReactNode }[] = [
    { label: t.rowMonthly, cell: (p) => priceCell(p.monthly_price_idr, t.perMonth, t.free) },
    { label: t.rowAnnual, cell: (p) => priceCell(p.annual_price_idr, t.perYear, t.notApplicable) },
    ...quota(t.rowTables, (p) => p.max_tables),
    ...quota(t.rowProducts, (p) => p.max_products),
    ...quota(t.rowStaff, (p) => p.max_staff),
    ...quota(t.rowDevices, (p) => p.max_devices),
    ...quota(t.rowPrinters, (p) => p.max_printers),
    known((p) => p.max_kds_stations)
      ? { label: t.rowKds, cell: (p: Plan) => (p.supports_kds_queue_tv ? quotaCell(p.max_kds_stations as number) : t.notIncluded) }
      : { label: t.rowKds, cell: (p: Plan) => <Flag on={p.supports_kds_queue_tv} /> },
    { label: t.rowQueueTv, cell: (p) => <Flag on={p.supports_kds_queue_tv} /> },
    { label: t.rowByok, cell: (p) => <Flag on={p.supports_byok_midtrans && p.commission_bps === 0} /> },
    { label: t.rowWhitelabel, cell: (p) => <Flag on={p.supports_whitelabel} /> },
    { label: t.rowByoNotifications, cell: (p) => <Flag on={p.supports_byo_notifications} /> }
  ];
  return (
    <div tabIndex={0} role="region" aria-label={t.pricingTableCaption} className="overflow-x-auto rounded-xl border border-subtle bg-surface">
      <table className="w-full min-w-[720px] border-collapse text-left text-sm">
        <caption className="sr-only">{t.pricingTableCaption}</caption>
        <thead>
          <tr className="border-b border-strong">
            <th scope="col" className="px-3 py-3 text-xs font-semibold uppercase tracking-wide text-subink">{t.pricingTableLabel}</th>
            {plans.map((p) => (
              <th key={p.code} scope="col" className="px-3 py-3 align-bottom text-base font-bold text-ink">{p.name_id}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.label} className="border-b border-subtle last:border-b-0">
              <th scope="row" className="px-3 py-2.5 font-medium text-subink">{r.label}</th>
              {plans.map((p) => (
                <td key={p.code} className="px-3 py-2.5 text-ink">{r.cell(p)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {plans.some(isTenMonthAnnual) ? <p className="border-t border-subtle px-3 py-2.5 text-xs text-subink">{t.tenMonthNote}</p> : null}
    </div>
  );
}

/** Product + Offer JSON-LD, one per plan, prices straight from the API. */
export function planProductsJsonLd(plans: Plan[]) {
  const url = new URL("/harga", siteUrl).toString();
  return plans.map((p) => ({
    "@context": "https://schema.org",
    "@type": "Product",
    name: `Cassy ${p.name_id}`,
    brand: { "@type": "Brand", name: "Cassy" },
    description: t.pricingDescription,
    url,
    offers: {
      "@type": "Offer",
      price: String(p.monthly_price_idr),
      priceCurrency: "IDR",
      availability: "https://schema.org/InStock",
      url,
      priceSpecification: {
        "@type": "UnitPriceSpecification",
        price: String(p.monthly_price_idr),
        priceCurrency: "IDR",
        billingDuration: p.code === "trial_14d" ? "P14D" : "P1M"
      }
    }
  }));
}

export interface FaqItem {
  q: string;
  a: string;
}

/** FAQ entries built from the API so prices and the Starter KDS answer never drift from the backend. */
export function faqItems(plans: Plan[]): FaqItem[] {
  const f = t.faq;
  const starter = planByCode(plans, "starter_umkm");
  return [
    { q: f.priceQ, a: fill(f.priceA, paidPrices(plans)) },
    { q: f.freeQ, a: f.freeA },
    { q: f.trialEndQ, a: f.trialEndA },
    { q: f.commissionQ, a: f.commissionA },
    { q: f.devicesQ, a: f.devicesA },
    { q: f.kdsQ, a: !starter.supports_kds_queue_tv ? f.kdsNoA : starter.max_kds_stations === undefined ? f.kdsYesPlainA : fill(f.kdsYesA, { count: limit(starter.max_kds_stations) }) },
    { q: f.dataQ, a: f.dataA }
  ];
}

export function faqJsonLd(items: FaqItem[]) {
  return {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: items.map((i) => ({ "@type": "Question", name: i.q, acceptedAnswer: { "@type": "Answer", text: i.a } }))
  };
}

export function FaqList({ items }: { items: FaqItem[] }) {
  return (
    <div className="divide-y divide-subtle rounded-xl border border-subtle bg-surface">
      {items.map((i) => (
        <details key={i.q} className="group px-4">
          <summary className="flex min-h-[48px] cursor-pointer items-center py-3 text-base font-semibold text-ink">{i.q}</summary>
          <p className="pb-4 text-sm leading-relaxed text-subink">{i.a}</p>
        </details>
      ))}
    </div>
  );
}

/** Trial button, an optional link to the price list, and the no-card note under them. */
export function TrialCta({ withPricing = false }: { withPricing?: boolean }) {
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-3">
        <a href={TRIAL_HREF} className={`${pill} bg-brand text-brand-fg hover:opacity-95`}>{t.ctaTrial}</a>
        {withPricing ? (
          <Link href="/harga" className={`${pill} border border-subtle bg-surface text-ink hover:border-brand`}>{t.ctaPricing}</Link>
        ) : null}
      </div>
      <p className="text-xs text-subink">{t.ctaTrialNote}</p>
    </div>
  );
}

/** Page metadata shared by the marketing routes: indexable, canonical, Open Graph. */
export function marketingMetadata(path: string, title: string, description: string) {
  return {
    title,
    description,
    alternates: { canonical: path },
    robots: { index: true, follow: true },
    openGraph: {
      type: "website" as const,
      title,
      description,
      url: new URL(path, siteUrl).toString(),
      siteName: "Cassy oleh AzureDevID",
      locale: "id_ID",
      images: [{ url: "/AndroidChrome512.png", width: 512, height: 512, alt: "Cassy oleh AzureDevID" }]
    },
    twitter: { card: "summary" as const, title, description }
  };
}

/** Holder for every marketing page: header, then the page sections. */
export function MarketingPage({ children }: { children: React.ReactNode }) {
  return <div className="mx-auto flex w-full max-w-5xl flex-col gap-8 px-5 py-5 text-ink">{children}</div>;
}
