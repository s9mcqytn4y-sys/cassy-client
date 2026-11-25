import { en } from "./messages.en";
import { id, type Messages } from "./messages.id";

export type { Messages };
/** Keys of plain-string messages (the nested marketing copy under `mkt` is excluded). */
export type TextKey = { [K in keyof Messages]: Messages[K] extends string ? K : never }[keyof Messages];
export const MESSAGES = { "id-ID": id, "en-US": en };
