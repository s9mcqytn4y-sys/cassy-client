"use client";

import { CassyWordmark } from "@/components/CassyLogo";
import { useLocale } from "@/components/Providers";
import { DataStateBox } from "@/components/Shell";
import { apiFetch } from "@/lib/api";
import { Check, Copy, FileText, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState, type ReactNode } from "react";

interface LegalDocument {
  doc_type: string;
  version: string;
  title: string;
  content_markdown: string;
  content_sha256: string;
  effective_at: string;
}

const GUEST_TYPES = ["guest_privacy_notice", "guest_order_terms"];

function renderInlineBold(text: string): ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, idx) => {
    if (part.startsWith("**") && part.endsWith("**") && part.length > 4) {
      return (
        <strong key={idx} className="font-semibold text-ink">
          {part.slice(2, -2)}
        </strong>
      );
    }
    return part;
  });
}

function renderMarkdownBlocks(markdown: string): ReactNode {
  const blocks = markdown.trim().split(/\n{2,}/);
  return blocks.map((block, idx) => {
    const trimmed = block.trim();
    if (!trimmed) return null;

    if (trimmed.startsWith("### ")) {
      return (
        <h3 key={idx} className="mt-5 text-base font-bold text-ink first:mt-0">
          {renderInlineBold(trimmed.slice(4))}
        </h3>
      );
    }
    if (trimmed.startsWith("## ")) {
      return (
        <h3 key={idx} className="mt-6 text-lg font-bold text-ink first:mt-0">
          {renderInlineBold(trimmed.slice(3))}
        </h3>
      );
    }

    const lines = trimmed.split("\n").map((l) => l.trim());
    if (lines.every((l) => l.startsWith("- ") || l.startsWith("* "))) {
      return (
        <ul key={idx} className="mt-3 list-disc space-y-1.5 pl-5 text-base leading-7 text-ink">
          {lines.map((line, lineIdx) => (
            <li key={lineIdx}>{renderInlineBold(line.slice(2))}</li>
          ))}
        </ul>
      );
    }
    if (lines.every((l) => /^\d+\.\s+/.test(l))) {
      return (
        <ol key={idx} className="mt-3 list-decimal space-y-1.5 pl-5 text-base leading-7 text-ink">
          {lines.map((line, lineIdx) => (
            <li key={lineIdx}>{renderInlineBold(line.replace(/^\d+\.\s+/, ""))}</li>
          ))}
        </ol>
      );
    }

    return (
      <p key={idx} className="mt-3 whitespace-pre-line text-base leading-7 text-ink first:mt-0">
        {renderInlineBold(trimmed)}
      </p>
    );
  });
}

export function LegalView({ audience }: { audience: "guest" | "tenant" }) {
  const { t, locale } = useLocale();
  const requested = useSearchParams().get("doc");
  // Tenant documents exist in Indonesian only.
  const docLocale = audience === "tenant" ? "id-ID" : locale;
  const [docs, setDocs] = useState<LegalDocument[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [copiedHash, setCopiedHash] = useState(false);

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

  const handleCopyHash = useCallback(async (hash: string) => {
    try {
      await navigator.clipboard.writeText(hash);
      setCopiedHash(true);
      setTimeout(() => setCopiedHash(false), 2000);
    } catch {
      // Ignore clipboard permission failure
    }
  }, []);

  return (
    <div className="mx-auto max-w-5xl px-5 py-8">
      <header className="rounded-2xl border border-subtle bg-surface p-5 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-subtle pb-4">
          <CassyWordmark height={22} />
          <Link
            href={audience === "guest" ? "/legal/mitra" : "/legal"}
            className="inline-flex min-h-[48px] items-center gap-2 rounded-xl border border-subtle bg-canvas px-4 py-2.5 text-sm font-semibold text-ink transition hover:border-brand"
          >
            <FileText className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{audience === "guest" ? t.legalToTenant : t.legalToGuest}</span>
          </Link>
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

                <div className="mt-5 space-y-3">{renderMarkdownBlocks(active.content_markdown)}</div>

                <div className="mt-8 rounded-xl border border-subtle bg-canvas p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-subink">
                      <ShieldCheck className="h-4 w-4 shrink-0" aria-hidden="true" />
                      <span>{t.legalIntegrityLabel}</span>
                    </span>
                    <button
                      type="button"
                      onClick={() => void handleCopyHash(active.content_sha256)}
                      className="inline-flex min-h-[44px] items-center gap-1.5 rounded-lg border border-subtle bg-surface px-3 py-1.5 text-xs font-semibold text-ink"
                    >
                      {copiedHash ? (
                        <Check className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      ) : (
                        <Copy className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      )}
                      <span>{copiedHash ? t.copiedToast : t.copyHash}</span>
                    </button>
                  </div>
                  <p className="mt-1.5 break-all font-mono text-xs text-subink">SHA-256 {active.content_sha256}</p>
                </div>
              </article>

              <p className="mt-4 text-sm text-subink">{t.legalContact}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
