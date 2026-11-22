import type { CartLine } from "./cart";
import type { CatalogCategory, CatalogProduct, CatalogVariant } from "./types";

export interface MenuSection {
  id: string;
  name: string;
  products: CatalogProduct[];
}

/** One list in category order; empty categories are skipped and unknown ones go last under `otherName`. */
export function menuSections(categories: CatalogCategory[], products: CatalogProduct[], otherName: string): MenuSection[] {
  const known = new Set(categories.map((c) => c.id));
  const sections = categories.map((c) => ({ id: c.id, name: c.name, products: products.filter((p) => p.category_id === c.id) }));
  sections.push({ id: "other", name: otherName, products: products.filter((p) => !known.has(p.category_id ?? "")) });
  return sections.filter((s) => s.products.length > 0);
}

function defaultAvailableVariant(product: CatalogProduct): CatalogVariant | undefined {
  return product.variants.find((v) => v.is_default && v.available) ?? product.variants.find((v) => v.available);
}

/**
 * Up to `max` available simple products (1-tap add) not yet in cart.
 * Prefers server-configured `recommended_product_ids` for items in cart, then complementary categories.
 */
export function pickSmartPairings(products: CatalogProduct[], lines: CartLine[], max = 2): CatalogProduct[] {
  if (lines.length === 0 || max <= 0) return [];
  const inCartIds = new Set(lines.map((l) => l.productId));
  const inCartCategories = new Set(
    products.filter((p) => inCartIds.has(p.id) && p.category_id).map((p) => p.category_id as string)
  );
  const simpleAvailable = products.filter(
    (p) =>
      p.available &&
      !inCartIds.has(p.id) &&
      p.variants.length <= 1 &&
      p.modifier_groups.length === 0 &&
      !(p.bundle_slots?.length ?? 0) &&
      !!defaultAvailableVariant(p)
  );
  const simpleById = new Map(simpleAvailable.map((p) => [p.id, p]));
  const picked: CatalogProduct[] = [];
  const pickedIds = new Set<string>();
  const addCandidate = (p: CatalogProduct | undefined) => {
    if (!p || pickedIds.has(p.id) || picked.length >= max) return;
    pickedIds.add(p.id);
    picked.push(p);
  };

  for (const product of products) {
    if (!inCartIds.has(product.id) || !product.recommended_product_ids?.length) continue;
    for (const recId of product.recommended_product_ids) {
      addCandidate(simpleById.get(recId));
    }
  }
  for (const p of simpleAvailable) {
    if (p.category_id && !inCartCategories.has(p.category_id)) addCandidate(p);
  }
  for (const p of simpleAvailable) {
    addCandidate(p);
  }
  return picked;
}
