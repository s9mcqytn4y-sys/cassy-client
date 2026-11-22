"use client";

import { CassyWordmark } from "@/components/CassyLogo";
import { useLocale } from "@/components/Providers";
import { DataStateBox } from "@/components/Shell";
import { apiFetch } from "@/lib/api";
import { ArrowLeft, FileText, Globe, Printer } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState, type ReactNode } from "react";

interface LegalDocument {
  doc_type: string;
  version: string;
  title: string;
  content_markdown: string;
  effective_at: string;
}

const GUEST_TYPES = ["guest_privacy_notice", "guest_order_terms"];

function slugifyHeading(text: string): string {
  return text
    .toLowerCase()
    .replace(/\*\*([^*]+)\*\*/g, "$1")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function extractToc(markdown: string): { id: string; title: string }[] {
  const seen = new Map<string, number>();
  return markdown
    .split(/\n{2,}/)
    .map((b) => b.trim())
    .filter((b) => b.startsWith("## "))
    .map((b) => {
      const title = b.slice(3).replace(/\*\*([^*]+)\*\*/g, "$1").trim();
      const base = slugifyHeading(title) || "section";
      const count = seen.get(base) ?? 0;
      seen.set(base, count + 1);
      return { id: count === 0 ? base : `${base}-${count}`, title };
    });
}

function isSafeHref(href: string): boolean {
  return href.startsWith("https://") || href.startsWith("http://") || href.startsWith("mailto:") || href.startsWith("/");
}

function renderInlineMarkdown(text: string): ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*|\[[^\]]+\]\([^)]+\))/g);
  return parts.map((part, idx) => {
    if (part.startsWith("**") && part.endsWith("**") && part.length > 4) {
      return (
        <strong key={idx} className="font-semibold text-ink">
          {part.slice(2, -2)}
        </strong>
      );
    }
    const linkMatch = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(part);
    if (linkMatch && isSafeHref(linkMatch[2])) {
      const [, label, href] = linkMatch;
      return (
        <a
          key={idx}
          href={href}
          target={href.startsWith("http") ? "_blank" : undefined}
          rel={href.startsWith("http") ? "noopener noreferrer" : undefined}
          className="font-medium text-brand underline underline-offset-4 hover:opacity-80"
        >
          {label}
        </a>
      );
    }
    return part;
  });
}

function renderMarkdownBlocks(markdown: string): ReactNode {
  const blocks = markdown.trim().split(/\n{2,}/);
  const seenIds = new Map<string, number>();
  return blocks.map((block, idx) => {
    const trimmed = block.trim();
    if (!trimmed) return null;

    if (trimmed.startsWith("### ")) {
      return (
        <h4 key={idx} className="mt-5 text-base font-bold text-ink first:mt-0">
          {renderInlineMarkdown(trimmed.slice(4))}
        </h4>
      );
    }
    if (trimmed.startsWith("## ")) {
      const rawTitle = trimmed.slice(3);
      const base = slugifyHeading(rawTitle) || "section";
      const count = seenIds.get(base) ?? 0;
      seenIds.set(base, count + 1);
      const id = count === 0 ? base : `${base}-${count}`;
      return (
        <h3 key={idx} id={id} className="mt-6 scroll-mt-6 text-lg font-bold text-ink first:mt-0">
          {renderInlineMarkdown(rawTitle)}
        </h3>
      );
    }

    const lines = trimmed.split("\n").map((l) => l.trim());
    if (lines.length >= 2 && lines.every((l) => l.startsWith("|") && l.endsWith("|"))) {
      const rows = lines
        .filter((l) => !/^\|[\s:-]+\|$/.test(l.replace(/[^|:-\s]/g, "")))
        .map((l) => l.slice(1, -1).split("|").map((c) => c.trim()));
      const [header, ...body] = rows;
      if (header && header.length > 0) {
        return (
          <div key={idx} className="mt-3 overflow-x-auto rounded-xl border border-subtle">
            <table className="w-full border-collapse text-left text-sm">
              <thead className="bg-canvas text-xs font-bold uppercase text-subink">
                <tr>
                  {header.map((cell, cIdx) => (
                    <th key={cIdx} className="border-b border-subtle px-3 py-2">{renderInlineMarkdown(cell)}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {body.map((row, rIdx) => (
                  <tr key={rIdx}>
                    {row.map((cell, cIdx) => (
                      <td key={cIdx} className="px-3 py-2 text-ink">{renderInlineMarkdown(cell)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      }
    }
    if (lines.every((l) => l.startsWith("- ") || l.startsWith("* "))) {
      return (
        <ul key={idx} className="mt-3 list-disc space-y-1.5 pl-5 text-base leading-7 text-ink">
          {lines.map((line, lineIdx) => (
            <li key={lineIdx}>{renderInlineMarkdown(line.slice(2))}</li>
          ))}
        </ul>
      );
    }
    if (lines.every((l) => /^\d+\.\s+/.test(l))) {
      return (
        <ol key={idx} className="mt-3 list-decimal space-y-1.5 pl-5 text-base leading-7 text-ink">
          {lines.map((line, lineIdx) => (
            <li key={lineIdx}>{renderInlineMarkdown(line.replace(/^\d+\.\s+/, ""))}</li>
          ))}
        </ol>
      );
    }

    return (
      <p key={idx} className="mt-3 whitespace-pre-line text-base leading-7 text-ink first:mt-0">
        {renderInlineMarkdown(trimmed)}
      </p>
    );
  });
}

export function LegalView({ audience }: { audience: "guest" | "tenant" }) {
  const { t, locale, toggleLocale } = useLocale();
  const requested = useSearchParams().get("doc");
  // Tenant documents exist in Indonesian only.
  const docLocale = audience === "tenant" ? "id-ID" : locale;
  const [docs, setDocs] = useState<LegalDocument[] | null>(null);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    setFailed(false);
    setDocs(null);
    try {
      const res = await apiFetch<{ documents?: LegalDocument[] }>(`/api/v1/public/legal?locale=${docLocale}`);
      const forGuests = (d: LegalDocument) => GUEST_TYPES.includes(d.doc_type);
      setDocs((res.documents ?? []).filter((d) => (audience === "guest" ? forGuests(d) : !forGuests(d))));
    } catch {
      setFailed(true);
    }
  }, [docLocale, audience]);

  useEffect(() => {
    void load();
  }, [load]);

  const base = audience === "guest" ? "/legal" : "/legal/mitra";
  const active = docs?.find((d) => d.doc_type === requested) ?? docs?.[0];
  const toc = active ? extractToc(active.content_markdown) : [];

  return (
    <div className="mx-auto max-w-5xl px-5 py-8">
      <header className="rounded-2xl border border-subtle bg-surface p-5 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-subtle pb-4">
          <div className="flex items-center gap-3">
            <Link
              href="/"
              aria-label={locale === "en-US" ? "Cassy Home" : "Beranda Cassy"}
              className="inline-flex min-h-[48px] items-center rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2"
            >
              <CassyWordmark height={22} />
            </Link>
            <Link
              href="/"
              className="inline-flex min-h-[48px] items-center gap-1.5 rounded-xl border border-subtle bg-canvas px-3 py-2 text-xs font-semibold text-ink transition hover:border-brand print:hidden"
            >
              <ArrowLeft className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>{locale === "en-US" ? "Home" : "Beranda"}</span>
            </Link>
          </div>
          <div className="flex flex-wrap items-center gap-2 print:hidden">
            {audience === "guest" ? (
              <button
                type="button"
                onClick={toggleLocale}
                aria-label={locale === "en-US" ? "Switch language to Indonesian" : "Ganti bahasa ke Inggris"}
                className="inline-flex min-h-[48px] items-center gap-1.5 rounded-xl border border-subtle bg-canvas px-3.5 py-2 text-xs font-semibold text-ink transition hover:border-brand"
              >
                <Globe className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span>{locale === "en-US" ? "EN | ID" : "ID | EN"}</span>
              </button>
            ) : null}
            <button
              type="button"
              onClick={() => window.print()}
              className="inline-flex min-h-[48px] items-center gap-1.5 rounded-xl border border-subtle bg-canvas px-3.5 py-2 text-xs font-semibold text-ink transition hover:border-brand"
            >
              <Printer className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>{locale === "en-US" ? "Print / PDF" : "Cetak / PDF"}</span>
            </button>
            <Link
              href={audience === "guest" ? "/legal/mitra" : "/legal"}
              className="inline-flex min-h-[48px] items-center gap-2 rounded-xl border border-subtle bg-canvas px-4 py-2.5 text-sm font-semibold text-ink transition hover:border-brand"
            >
              <FileText className="h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{audience === "guest" ? t.legalToTenant : t.legalToGuest}</span>
            </Link>
          </div>
        </div>
        <h1 className="mt-4 text-2xl font-bold sm:text-3xl">
          {audience === "guest" ? t.legalGuestTitle : t.legalTenantTitle}
        </h1>
        <p className="mt-2 max-w-3xl text-base leading-relaxed text-subink">
          {audience === "guest" ? t.legalGuestIntro : t.legalTenantIntro}
        </p>
      </header>

      <div className="mt-6">
        {failed ? (
          <DataStateBox state="error" title={t.legalFailed} description={t.legalFailedBody} onRetry={load} />
        ) : !docs ? (
          <DataStateBox state="loading" title={t.legalLoading} description="" />
        ) : !active ? (
          <DataStateBox state="empty" title={t.legalEmpty} description={t.legalContact} />
        ) : (
          <div className={docs.length > 1 ? "lg:grid lg:grid-cols-[280px_1fr] lg:items-start lg:gap-6" : ""}>
            {docs.length > 1 ? (
              <nav aria-label={t.legalDocs} className="flex flex-wrap gap-2 lg:flex-col">
                {docs.map((d) => {
                  const isCurrent = d === active;
                  return (
                    <Link
                      key={d.doc_type}
                      href={`${base}?doc=${d.doc_type}`}
                      aria-current={isCurrent ? "page" : undefined}
                      className={`inline-flex min-h-[48px] items-center rounded-xl px-4 py-2.5 text-sm font-semibold transition ${
                        isCurrent
                          ? "bg-brand text-brand-fg shadow-xs"
                          : "border border-subtle bg-surface text-ink hover:bg-canvas"
                      }`}
                    >
                      {d.title}
                    </Link>
                  );
                })}
              </nav>
            ) : null}

            <div className={docs.length > 1 ? "mt-6 lg:mt-0" : ""}>
              <article className="rounded-2xl border border-subtle bg-surface p-5 sm:p-7">
                <div className="border-b border-subtle pb-4">
                  <h2 className="text-xl font-bold sm:text-2xl">{active.title}</h2>
                  <p className="mt-1.5 text-sm text-subink">
                    {t.legalVersion} {active.version}
                    {active.effective_at
                      ? ` | ${t.legalEffective} ${new Date(active.effective_at).toLocaleDateString(locale)}`
                      : ""}
                  </p>
                </div>

                {toc.length > 1 ? (
                  <nav
                    aria-label={locale === "en-US" ? "Table of contents" : "Daftar isi"}
                    className="mt-4 rounded-xl border border-subtle bg-canvas p-4 print:hidden"
                  >
                    <p className="text-xs font-bold uppercase tracking-wide text-subink">
                      {locale === "en-US" ? "Contents" : "Daftar Isi"}
                    </p>
                    <ul className="mt-2 flex flex-wrap gap-2">
                      {toc.map((item) => (
                        <li key={item.id}>
                          <a
                            href={`#${item.id}`}
                            className="inline-flex min-h-[40px] items-center rounded-lg border border-subtle bg-surface px-3 py-1.5 text-xs font-medium text-ink transition hover:border-brand"
                          >
                            {item.title}
                          </a>
                        </li>
                      ))}
                    </ul>
                  </nav>
                ) : null}

                <div className="mt-5 space-y-3">{renderMarkdownBlocks(active.content_markdown)}</div>
              </article>

              <p className="mt-4 text-sm text-subink">{t.legalContact}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
