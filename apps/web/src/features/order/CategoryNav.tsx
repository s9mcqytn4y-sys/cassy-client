"use client";

import { useEffect, useRef, useState } from "react";
import { useLocale } from "@/components/Providers";

export const sectionId = (id: string) => `menu-${id}`;

/** Sticky top category rail: a tap scrolls to the section heading, and the active pill tracks scroll position. */
export function CategoryNav({ sections }: { sections: { id: string; name: string }[] }) {
  const { t } = useLocale();
  const [active, setActive] = useState(sections[0]?.id);
  const nav = useRef<HTMLElement>(null);

  useEffect(() => {
    const headings = sections.map((s) => document.getElementById(sectionId(s.id))).filter((h): h is HTMLElement => !!h);
    // Account for the sticky top rail (~64px) when deciding which section is active.
    const observer = new IntersectionObserver(
      (entries) => {
        const hit = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
        if (hit) setActive(hit.target.id.slice("menu-".length));
      },
      { rootMargin: "-64px 0px -65% 0px" }
    );
    headings.forEach((h) => observer.observe(h));
    return () => observer.disconnect();
  }, [sections]);

  useEffect(() => {
    nav.current
      ?.querySelector('[aria-current="true"]')
      ?.scrollIntoView({ block: "nearest", inline: "center", behavior: "auto" });
  }, [active]);

  return (
    <div className="sticky top-0 z-20 mt-4 border-y border-subtle bg-canvas/95 py-2 backdrop-blur supports-[backdrop-filter]:bg-canvas/85">
      <nav
        ref={nav}
        aria-label={t.categories}
        className="no-scrollbar flex snap-x snap-proximity gap-2 overflow-x-auto scroll-px-5 px-5 md:scroll-px-6 md:px-6 [mask-image:linear-gradient(to_right,transparent,black_12px,black_calc(100%-16px),transparent)]"
      >
        {sections.map((s) => (
          <button
            key={s.id}
            type="button"
            aria-current={active === s.id ? "true" : undefined}
            onClick={() => {
              setActive(s.id);
              const target = document.getElementById(sectionId(s.id));
              if (target) {
                const offset = (nav.current?.parentElement?.offsetHeight ?? 60) + 12;
                const y = target.getBoundingClientRect().top + window.scrollY - offset;
                window.scrollTo({ top: Math.max(0, y), behavior: "auto" });
              }
            }}
            className={`min-h-[44px] shrink-0 cursor-pointer snap-start rounded-full border px-4 text-sm font-semibold motion-safe:transition-all motion-safe:active:scale-[0.97] ${
              active === s.id
                ? "border-brand bg-brand text-brand-fg shadow-sm"
                : "border-transparent bg-muted text-ink hover:border-subtle"
            }`}
          >
            {s.name}
          </button>
        ))}
        <span aria-hidden="true" className="w-3 shrink-0" />
      </nav>
    </div>
  );
}
