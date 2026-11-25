#!/usr/bin/env bash
# PWA-09 (S5-Q5) and FL1 W4: prints the pages Lighthouse measures against the test stack, one per line:
# the guest menu of table U1, the short receipt of a real paid order, and the marketing pages /, /harga, /fitur. The order is a cash sale on
# "Laci Uji 2" in a shift that is opened and force-closed here. Needs curl and node.
set -eu

API="${DIRECT_API_URL:-http://127.0.0.1:18081/api/v1}"
WEB="${PLAYWRIGHT_TEST_BASE_URL:-http://127.0.0.1:3000}"
uuid() { node -e 'console.log(crypto.randomUUID())'; }
# pick EXPR: evaluates EXPR against the JSON on stdin (bound to j) and prints the result, empty if absent.
pick() { node -e 'const j = JSON.parse(require("fs").readFileSync(0, "utf8") || "{}"); const v = eval(process.argv[1]); console.log(v ?? "")' "$1"; }

login() {
  curl -fsS -X POST "$API/auth/staff/login" -H 'Content-Type: application/json' \
    -d "{\"store_slug\":\"e2e-kasir\",\"email\":\"$1\",\"pin\":\"$2\"}" | pick 'j.access_token'
}
call() {
  method="$1"; path="$2"; token="$3"; body="${4:-}"
  if [ -n "$body" ]; then
    curl -fsS -X "$method" "$API$path" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body"
  else
    curl -fsS -X "$method" "$API$path" -H "Authorization: Bearer $token"
  fi
}

cashier="$(login cashier.e2e@cassy.local 528307)"
manager="$(login manager.e2e@cassy.local 739164)"
drawer="$(call GET /pos/drawers "$cashier" | pick 'j.drawers.find((d) => d.name === "Laci Uji 2").id')"
leftover="$(curl -sS "$API/pos/shifts/current?drawer_id=$drawer" -H "Authorization: Bearer $cashier" | pick 'j.id')"
if [ -n "$leftover" ]; then
  call POST "/pos/shifts/$leftover/force-close" "$manager" '{"reason":"Sisa percobaan uji e2e sebelumnya"}' > /dev/null
fi
shift_id="$(call POST /pos/shifts "$cashier" "{\"drawer_id\":\"$drawer\",\"opening_float\":100000}" | pick 'j.shift.id')"

kopi="$(call GET /pos/catalog "$cashier" | pick 'j.products.find((p) => p.name === "Kopi Uji").id')"
order="$(call POST /pos/orders "$cashier" "{\"idempotency_key\":\"$(uuid)\",\"customer_name\":\"Lighthouse\",\"order_type\":\"takeaway\",\"billing_mode\":\"pay_first\",\"items\":[{\"product_id\":\"$kopi\",\"quantity\":1}]}" | pick 'j.order.id')"
call POST "/pos/orders/$order/payments" "$cashier" "{\"idempotency_key\":\"$(uuid)\",\"method\":\"cash\",\"cash_tendered_amount\":50000,\"drawer_id\":\"$drawer\"}" > /dev/null
code="$(call POST "/pos/orders/$order/receipt-link" "$cashier" | pick 'j.short_code')"
call POST "/pos/shifts/$shift_id/force-close" "$manager" '{"reason":"Akhir persiapan Lighthouse"}' > /dev/null

echo "$WEB/order/e2e-kasir-meja-u1-qrcode"
echo "$WEB/r/$code"
echo "$WEB/"
echo "$WEB/harga"
echo "$WEB/fitur"
