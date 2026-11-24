// PWA-10 (PrdOpsAndPwaClose.md section 12, S4-Q9): user-facing copy lives in src/lib/messages.*.ts only.
// Flags JSX text and literal aria-label/title/placeholder/alt in src/**/*.tsx.
// shortcut: line-based heuristics, not an AST; move to eslint react/jsx-no-literals if false hits pile up.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL("../src/", import.meta.url));
// Brand, product and payment network names are not translated.
const ALLOW = new Set(["Cassy", "AzureDevID", "Midtrans", "QRIS", "GoPay", "ShopeePay", "OVO", "DANA", "BCA", "BNI", "BRI", "Mandiri", "Permata"]);
// `a < b || c > d` looks like a tag pair; real JSX text never holds these operators.
const CODE_IN_TEXT = /[;(]|\|\||&&|[a-z]\.[a-z]/i;
const KEYWORD = /^(return|const|let|var|if|else|await|async|export|import|function|case|default|throw|type|interface|break|continue|new|typeof)\b/;
// Object literal entries (`key: value,`) and lines ending a list item are code, not copy.
const OBJECT_LINE = /^[A-Za-z_$][\w$]*\??:\s|[,[]$/;
// A JSX text node on its own line: several words, or one capitalised word such as a button label.
const BARE_TEXT = /^([A-Za-z][^=;{}()<>`"]*\s[^=;{}()<>`"]*|[A-Z][a-z]{2,}[.!?]?)$/;
const JSX_TEXT = /(?<!=)>\s*([^<>{}]*[A-Za-z]{2,}[^<>{}]*?)\s*</g;
const ATTR = /\b(aria-label|title|placeholder|alt)="([^"]*[A-Za-z][^"]*)"/g;

const files = (dir) =>
  readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? files(path) : path.endsWith(".tsx") ? [path] : [];
  });

const findings = [];
for (const file of files(ROOT)) {
  readFileSync(file, "utf8").split("\n").forEach((line, i) => {
    const code = line.replace(/\/\/.*$/, "");
    const bare = code.trim();
    if (/^(\*|\/\*|import |export type|type )/.test(bare)) return;
    const texts = [...code.matchAll(JSX_TEXT)].map((m) => m[1].trim()).filter((t) => !CODE_IN_TEXT.test(t));
    texts.push(...[...code.matchAll(ATTR)].map((m) => m[2]));
    if (BARE_TEXT.test(bare) && !KEYWORD.test(bare) && !OBJECT_LINE.test(bare)) texts.push(bare);
    for (const text of texts) {
      if (text.split(/\s+/).every((w) => ALLOW.has(w.replace(/[^\w]/g, "")))) continue;
      findings.push(`${relative(ROOT, file)}:${i + 1}: ${text}`);
    }
  });
}

if (findings.length) {
  console.error(`Hardcoded UI copy, move it to src/lib/messages.id.ts and messages.en.ts:\n${findings.join("\n")}`);
  process.exit(1);
}
console.log("check:copy ok");
