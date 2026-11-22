"use client";

import React, { useMemo, useState } from "react";
import { useLocale } from "@/components/Providers";
import { Minus, Plus } from "lucide-react";
import { Sheet } from "@/components/Sheet";
import { formatIdr } from "@/lib/format";
import { normalizeSubmissionText, stripControlChars } from "@/lib/sanitize";
import type { CartLine, SelectedBundleItem, SelectedOption } from "./cart";
import { defaultVariant } from "./MenuItems";
import { ProductImage } from "./ProductImage";
import type { BundleSlot, CatalogGroup, CatalogProduct } from "./types";

type Selection = Record<string, string[]>;

interface SlotState {
  productId?: string;
  variantId?: string;
  selection: Selection;
}

function groupMissing(group: CatalogGroup, selection: Selection): boolean {
  const need = group.required ? Math.max(group.min, 1) : group.min;
  return (selection[group.id]?.length ?? 0) < need;
}

function toggle(selection: Selection, group: CatalogGroup, optionId: string): Selection {
  const current = selection[group.id] ?? [];
  if (group.max === 1) return { ...selection, [group.id]: current[0] === optionId ? [] : [optionId] };
  if (current.includes(optionId)) return { ...selection, [group.id]: current.filter((id) => id !== optionId) };
  if (group.max > 0 && current.length >= group.max) return selection;
  return { ...selection, [group.id]: [...current, optionId] };
}

function activeSubGroups(group: CatalogGroup, selection: Selection): CatalogGroup[] {
  return group.options.filter((o) => selection[group.id]?.includes(o.id)).flatMap((o) => o.sub_groups ?? []);
}

function modifiersIncomplete(groups: CatalogGroup[], selection: Selection): boolean {
  return groups.some((g) => groupMissing(g, selection) || activeSubGroups(g, selection).some((sub) => groupMissing(sub, selection)));
}

function collectSelectedOptions(groups: CatalogGroup[], selection: Selection): SelectedOption[] {
  return groups.flatMap((group) =>
    (selection[group.id] ?? []).map((optionId) => ({
      optionId,
      subOptionIds: (group.options.find((o) => o.id === optionId)?.sub_groups ?? []).flatMap((sub) => selection[sub.id] ?? [])
    }))
  );
}

function collectOptionLabels(groups: CatalogGroup[], selection: Selection): string[] {
  const nameOf = (list: CatalogGroup[], id: string): string | undefined => list.flatMap((g) => g.options).find((o) => o.id === id)?.name;
  return groups
    .flatMap((group) =>
      (selection[group.id] ?? []).flatMap((optionId) => {
        const subs = group.options.find((o) => o.id === optionId)?.sub_groups ?? [];
        return [nameOf([group], optionId), ...subs.flatMap((sub) => (selection[sub.id] ?? []).map((id) => nameOf([sub], id)))];
      })
    )
    .filter((d): d is string => Boolean(d));
}

function GroupFields({
  group,
  selection,
  onChange,
  namePrefix = ""
}: {
  group: CatalogGroup;
  selection: Selection;
  onChange: (next: Selection) => void;
  namePrefix?: string;
}) {
  const { t } = useLocale();
  const selected = selection[group.id] ?? [];
  return (
    <fieldset className="mt-5">
      <legend className="text-base font-bold">
        {group.name}
        <span className="ml-2 text-xs font-normal text-subink">
          {group.required ? t.required : ""}
          {group.max > 1 ? ` ${t.chooseUpTo} ${group.max}` : ""}
        </span>
      </legend>
      {group.options.map((option) => (
        <label
          key={option.id}
          className="mt-2 flex min-h-[48px] cursor-pointer items-center gap-3 rounded-xl border border-subtle px-3.5 text-[0.9375rem] has-[:checked]:border-brand has-[:checked]:bg-muted has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-50"
        >
          <input
            type={group.max === 1 ? "radio" : "checkbox"}
            name={`${namePrefix}${group.id}`}
            disabled={!option.available}
            checked={selected.includes(option.id)}
            onClick={() => onChange(toggle(selection, group, option.id))}
            onChange={() => undefined}
            className="h-5 w-5 accent-[var(--brand-primary)]"
          />
          <span className="flex-1">{option.available ? option.name : `${option.name} (${t.soldOut})`}</span>
          {option.price_delta > 0 ? <span className="tabular-nums text-subink">+{formatIdr(option.price_delta)}</span> : null}
        </label>
      ))}
    </fieldset>
  );
}

export function ProductOptionsSheet({
  product,
  canOrder = true,
  catalogProducts = [],
  onAdd,
  onClose
}: {
  product: CatalogProduct;
  canOrder?: boolean;
  catalogProducts?: CatalogProduct[];
  onAdd: (line: Omit<CartLine, "key">) => void;
  onClose: () => void;
}) {
  const { t } = useLocale();
  const variants = product.variants.filter((v) => v.available);
  const soldOut = !product.available || variants.length === 0;
  const slots = useMemo<BundleSlot[]>(
    () => [...(product.bundle_slots ?? [])].sort((a, b) => a.display_order - b.display_order),
    [product.bundle_slots]
  );
  const productById = useMemo(() => new Map(catalogProducts.map((p) => [p.id, p])), [catalogProducts]);

  const [variantId, setVariantId] = useState((variants.find((v) => v.is_default) ?? variants[0])?.id);
  const [selection, setSelection] = useState<Selection>({});
  const [slotChoices, setSlotChoices] = useState<Record<string, SlotState>>({});
  const [notes, setNotes] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [showError, setShowError] = useState(false);

  const slotsIncomplete = slots.some((slot) => {
    const choice = slotChoices[slot.id];
    if (!choice?.productId) return true;
    const chosen = productById.get(choice.productId);
    if (!chosen || !chosen.available) return true;
    const availableVariants = chosen.variants.filter((v) => v.available);
    if (availableVariants.length === 0 || (choice.variantId && !availableVariants.some((v) => v.id === choice.variantId))) return true;
    return modifiersIncomplete(chosen.modifier_groups, choice.selection);
  });

  const incomplete = modifiersIncomplete(product.modifier_groups, selection) || slotsIncomplete;

  const selectSlotProduct = (slotId: string, candidate: CatalogProduct) => {
    const pickedVariant = defaultVariant(candidate)?.id;
    setSlotChoices((prev) => ({
      ...prev,
      [slotId]: { productId: candidate.id, variantId: pickedVariant, selection: {} }
    }));
  };

  const updateSlotVariant = (slotId: string, nextVariantId: string) => {
    setSlotChoices((prev) => ({
      ...prev,
      [slotId]: { ...(prev[slotId] ?? { selection: {} }), variantId: nextVariantId }
    }));
  };

  const updateSlotSelection = (slotId: string, nextSelection: Selection) => {
    setSlotChoices((prev) => ({
      ...prev,
      [slotId]: { ...(prev[slotId] ?? { selection: {} }), selection: nextSelection }
    }));
  };

  const submit = () => {
    if (!canOrder || soldOut) return;
    if (incomplete) {
      setShowError(true);
      return;
    }
    const options = collectSelectedOptions(product.modifier_groups, selection);
    const bundleItems: SelectedBundleItem[] = slots.flatMap((slot) => {
      const choice = slotChoices[slot.id];
      const chosen = choice?.productId ? productById.get(choice.productId) : undefined;
      if (!choice?.productId || !chosen) return [];
      return [
        {
          slotId: slot.id,
          productId: chosen.id,
          variantId: choice.variantId ?? defaultVariant(chosen)?.id,
          options: collectSelectedOptions(chosen.modifier_groups, choice.selection)
        }
      ];
    });

    const bundleDetails = slots.flatMap((slot) => {
      const choice = slotChoices[slot.id];
      const chosen = choice?.productId ? productById.get(choice.productId) : undefined;
      if (!chosen) return [];
      const chosenVariant = chosen.variants.length > 1 ? chosen.variants.find((v) => v.id === choice.variantId)?.name : undefined;
      const chosenMods = collectOptionLabels(chosen.modifier_groups, choice.selection);
      const extras = [chosenVariant, ...chosenMods].filter(Boolean).join(", ");
      return [extras ? `${chosen.name} (${extras})` : chosen.name];
    });

    const details = [
      product.variants.length > 1 ? product.variants.find((v) => v.id === variantId)?.name : undefined,
      ...collectOptionLabels(product.modifier_groups, selection),
      ...bundleDetails
    ].filter((d): d is string => Boolean(d));

    onAdd({
      productId: product.id,
      variantId,
      name: product.name,
      options,
      ...(bundleItems.length > 0 ? { bundleItems } : {}),
      quantity,
      notes: normalizeSubmissionText(notes, 200),
      details
    });
  };

  return (
    <Sheet title={product.name} onClose={onClose}>
      {product.image_url ? (
        <ProductImage src={product.image_url} alt={product.name} width={640} height={480} className="mb-4 aspect-[4/3] w-full rounded-2xl" />
      ) : null}
      {product.description ? <p className="text-[0.9375rem] leading-relaxed text-subink">{product.description}</p> : null}
      {soldOut ? (
        <p role="status" className="mt-3 rounded-xl bg-[var(--state-err-bg)] px-3.5 py-2.5 text-sm font-semibold text-[var(--state-err-fg)]">
          {product.sold_out_reason ? `${t.soldOut}: ${product.sold_out_reason}` : t.soldOut}
        </p>
      ) : null}

      {product.variants.length > 1 ? (
        <fieldset className="mt-6">
          <legend className="text-base font-bold">{t.choose}</legend>
          {product.variants.map((v) => (
            <label
              key={v.id}
              className="mt-2 flex min-h-[48px] cursor-pointer items-center gap-3 rounded-xl border border-subtle px-3.5 text-[0.9375rem] has-[:checked]:border-brand has-[:checked]:bg-muted has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-50"
            >
              <input
                type="radio"
                name="variant"
                disabled={!v.available}
                checked={variantId === v.id}
                onChange={() => setVariantId(v.id)}
                className="h-5 w-5 accent-[var(--brand-primary)]"
              />
              <span className="flex-1">{v.available ? v.name : `${v.name} (${t.soldOut})`}</span>
              <span className="font-semibold tabular-nums">{formatIdr(v.price)}</span>
            </label>
          ))}
        </fieldset>
      ) : null}

      {slots.map((slot) => {
        const candidates = slot.product_ids.map((id) => productById.get(id)).filter((p): p is CatalogProduct => Boolean(p));
        const choice = slotChoices[slot.id] ?? { selection: {} };
        const chosen = choice.productId ? productById.get(choice.productId) : undefined;
        return (
          <div key={slot.id} className="mt-6 rounded-2xl border border-subtle bg-surface p-4">
            <fieldset>
              <legend className="text-base font-bold text-ink">
                {slot.name}
                {slot.quantity > 1 ? <span className="ml-1.5 text-xs font-semibold tabular-nums text-subink">x{slot.quantity}</span> : null}
                <span className="ml-2 text-xs font-normal text-subink">{t.required}</span>
              </legend>
              {candidates.map((candidate) => {
                const candidateVariant = defaultVariant(candidate);
                const unavailable = !candidate.available || !candidateVariant;
                return (
                  <label
                    key={candidate.id}
                    className="mt-2 flex min-h-[48px] cursor-pointer items-center gap-3 rounded-xl border border-subtle px-3.5 text-[0.9375rem] has-[:checked]:border-brand has-[:checked]:bg-muted has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-50"
                  >
                    <input
                      type="radio"
                      name={`bundle-slot-${slot.id}`}
                      disabled={unavailable}
                      checked={choice.productId === candidate.id}
                      onChange={() => selectSlotProduct(slot.id, candidate)}
                      className="h-5 w-5 accent-[var(--brand-primary)]"
                    />
                    <span className="flex-1">{unavailable ? `${candidate.name} (${t.soldOut})` : candidate.name}</span>
                  </label>
                );
              })}
            </fieldset>

            {chosen && chosen.variants.length > 1 ? (
              <fieldset className="mt-4 border-t border-subtle pt-4">
                <legend className="text-sm font-bold text-ink">{t.choose}</legend>
                {chosen.variants.map((v) => (
                  <label
                    key={v.id}
                    className="mt-2 flex min-h-[48px] cursor-pointer items-center gap-3 rounded-xl border border-subtle px-3.5 text-sm has-[:checked]:border-brand has-[:checked]:bg-muted has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-50"
                  >
                    <input
                      type="radio"
                      name={`bundle-variant-${slot.id}`}
                      disabled={!v.available}
                      checked={choice.variantId === v.id}
                      onChange={() => updateSlotVariant(slot.id, v.id)}
                      className="h-5 w-5 accent-[var(--brand-primary)]"
                    />
                    <span className="flex-1">{v.available ? v.name : `${v.name} (${t.soldOut})`}</span>
                  </label>
                ))}
              </fieldset>
            ) : null}

            {chosen
              ? chosen.modifier_groups.map((group) => (
                  <div key={group.id}>
                    <GroupFields
                      group={group}
                      selection={choice.selection}
                      onChange={(next) => updateSlotSelection(slot.id, next)}
                      namePrefix={`${slot.id}_`}
                    />
                    {activeSubGroups(group, choice.selection).map((sub) => (
                      <div key={sub.id} className="pl-6">
                        <GroupFields
                          group={sub}
                          selection={choice.selection}
                          onChange={(next) => updateSlotSelection(slot.id, next)}
                          namePrefix={`${slot.id}_`}
                        />
                      </div>
                    ))}
                  </div>
                ))
              : null}
          </div>
        );
      })}

      {product.modifier_groups.map((group) => (
        <div key={group.id}>
          <GroupFields group={group} selection={selection} onChange={setSelection} />
          {activeSubGroups(group, selection).map((sub) => (
            <div key={sub.id} className="pl-8">
              <GroupFields group={sub} selection={selection} onChange={setSelection} />
            </div>
          ))}
        </div>
      ))}

      {canOrder ? (
        <>
          <label htmlFor="line-notes" className="mt-6 block text-base font-bold">
            {t.notes}
          </label>
          <textarea
            id="line-notes"
            value={notes}
            onChange={(e) => setNotes(stripControlChars(e.target.value, 200))}
            maxLength={200}
            rows={2}
            aria-describedby="line-notes-hint"
            className="input-field mt-1 w-full"
          />
          <p id="line-notes-hint" className="mb-4 mt-1 text-xs text-subink">
            {t.notesHint}
          </p>

          {showError && incomplete ? (
            <p role="alert" className="mb-4 mt-2 text-sm font-semibold text-[var(--state-err-fg)]">
              {t.needChoice}
            </p>
          ) : null}

          <div className="sticky bottom-0 z-10 -mx-5 mt-6 flex items-center gap-3 border-t border-subtle bg-surface px-5 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3.5 shadow-[0_-4px_12px_rgba(23,33,30,0.06)]">
            <div role="group" aria-label={t.quantity} className="flex items-center gap-1 rounded-full bg-muted p-1">
              <button
                type="button"
                aria-label={t.decrease}
                disabled={quantity <= 1}
                onClick={() => setQuantity((q) => q - 1)}
                className="btn-secondary h-11 w-11 rounded-full"
              >
                <Minus size={18} />
              </button>
              <span aria-live="polite" className="min-w-7 text-center text-base font-bold tabular-nums">
                {quantity}
              </span>
              <button
                type="button"
                aria-label={t.increase}
                onClick={() => setQuantity((q) => q + 1)}
                className="btn-secondary h-11 w-11 rounded-full"
              >
                <Plus size={18} />
              </button>
            </div>
            <button
              type="button"
              disabled={soldOut}
              onClick={submit}
              className="btn-primary min-h-[52px] flex-1 cursor-pointer rounded-xl text-base font-bold disabled:cursor-not-allowed"
            >
              {soldOut ? t.soldOut : t.addToCart}
            </button>
          </div>
        </>
      ) : (
        <p role="status" className="mt-6 rounded-xl bg-muted px-4 py-3 text-sm font-medium text-subink">
          {t.storeClosedAction}
        </p>
      )}
    </Sheet>
  );
}
