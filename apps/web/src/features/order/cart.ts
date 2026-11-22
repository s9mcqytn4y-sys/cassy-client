export interface SelectedOption {
  optionId: string;
  subOptionIds: string[];
}

export interface SelectedBundleItem {
  slotId: string;
  productId: string;
  variantId?: string;
  options: SelectedOption[];
  notes?: string;
}

export interface CartLine {
  key: string;
  productId: string;
  variantId?: string;
  name: string;
  options: SelectedOption[];
  bundleItems?: SelectedBundleItem[];
  quantity: number;
  notes: string;
  // Names of the chosen size and add-ons, kept for display so two lines of one product never look alike
  // while the quote loads. Never sent to the API; carts saved before this field fall back to the quote.
  details?: string[];
}

export type CartAction =
  | { type: "add"; line: Omit<CartLine, "key"> }
  | { type: "setQuantity"; key: string; quantity: number }
  | { type: "replace"; lines: CartLine[] }
  | { type: "clear" };

function serializeOptions(options: SelectedOption[]): string {
  return options
    .map((o) => `${o.optionId}(${[...o.subOptionIds].sort().join(",")})`)
    .sort()
    .join("|");
}

function lineKey(line: Omit<CartLine, "key">): string {
  const options = serializeOptions(line.options);
  const bundles = (line.bundleItems ?? [])
    .map((b) => `${b.slotId}:${b.productId}:${b.variantId ?? ""}:${serializeOptions(b.options)}:${b.notes ?? ""}`)
    .sort()
    .join(";");
  return [line.productId, line.variantId ?? "", options, bundles, line.notes].join("#");
}

export function cartReducer(state: CartLine[], action: CartAction): CartLine[] {
  switch (action.type) {
    case "add": {
      const key = lineKey(action.line);
      const existing = state.find((l) => l.key === key);
      if (existing) {
        return state.map((l) => (l.key === key ? { ...l, quantity: l.quantity + action.line.quantity } : l));
      }
      return [...state, { ...action.line, key }];
    }
    case "setQuantity":
      return action.quantity > 0
        ? state.map((l) => (l.key === action.key ? { ...l, quantity: action.quantity } : l))
        : state.filter((l) => l.key !== action.key);
    case "replace":
      return action.lines;
    case "clear":
      return [];
  }
}

interface OptionSnapshot {
  option_name?: string;
  sub_addons?: OptionSnapshot[];
}

// The quote returns selected_options as a JSON string of option snapshots.
export function optionNames(raw?: string): string[] {
  if (!raw) return [];
  try {
    const flatten = (list: OptionSnapshot[]): string[] =>
      list.flatMap((o) => [o.option_name ?? "", ...flatten(o.sub_addons ?? [])]);
    return flatten(JSON.parse(raw) as OptionSnapshot[]).filter(Boolean);
  } catch {
    return [];
  }
}

function mapModifiers(options: SelectedOption[]) {
  return options.map((o) => ({
    option_id: o.optionId,
    quantity: 1,
    sub_addons: o.subOptionIds.map((id) => ({ option_id: id, quantity: 1 }))
  }));
}

export function toOrderItems(lines: CartLine[]) {
  return lines.map((l) => ({
    product_id: l.productId,
    variant_id: l.variantId,
    quantity: l.quantity,
    notes: l.notes,
    modifiers: mapModifiers(l.options),
    ...(l.bundleItems?.length
      ? {
          bundle_items: l.bundleItems.map((b) => ({
            slot_id: b.slotId,
            product_id: b.productId,
            variant_id: b.variantId,
            modifiers: mapModifiers(b.options),
            ...(b.notes ? { notes: b.notes } : {})
          }))
        }
      : {})
  }));
}
