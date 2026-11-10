#!/usr/bin/env bash
# End-to-end smoke test through the API gateway:
#   REST -> order-service -> gRPC (restaurant + customer) -> Postgres -> Kafka
#   -> delivery-service (driver assignment) -> Kafka -> order-service (status sync)
#
# Usage: scripts/smoke-test.sh [gateway-url]     (requires curl + jq)
set -euo pipefail

GW="${1:-http://localhost:8080}"
pass=0
fail=0

ok()   { printf '  \033[32m✔\033[0m %s\n' "$1"; pass=$((pass + 1)); }
bad()  { printf '  \033[31m✘\033[0m %s\n' "$1"; fail=$((fail + 1)); }
step() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# call METHOD PATH [JSON]  -> sets $status and $body
call() {
  local out
  out=$(curl -sS -X "$1" "$GW$2" -H 'Content-Type: application/json' ${3:+-d "$3"} -w $'\n%{http_code}')
  status="${out##*$'\n'}"
  body="${out%$'\n'*}"
}

expect() { # expect CODE DESCRIPTION
  if [[ "$status" == "$1" ]]; then ok "$2 ($status)"; else bad "$2: expected $1, got $status: $body"; fi
}

# wait_for PATH JQ_FILTER EXPECTED DESCRIPTION  (polls; events are asynchronous)
wait_for() {
  local value=""
  for _ in $(seq 1 30); do
    call GET "$1"
    value=$(jq -r "$2" <<<"$body" 2>/dev/null || true)
    [[ "$value" == "$3" ]] && { ok "$4 ($value)"; return; }
    sleep 1
  done
  bad "$4: expected '$3', last saw '$value' (HTTP $status)"
}

suffix=$RANDOM$RANDOM

step "1. Arrange: restaurant, menu, customer + saved address, driver"
call POST /api/restaurants '{"name":"Smoke Kitchen '"$suffix"'","address":"1 Test St","cuisineType":"Fusion","operatingHours":"09:00-21:00"}'
expect 201 "create restaurant"; restaurant=$(jq -r .id <<<"$body")

call POST "/api/restaurants/$restaurant/menu-items" '{"name":"Kottu","description":"Chopped roti","price":750.00,"category":"Main"}'
expect 201 "add menu item (750.00)"; kottu=$(jq -r .id <<<"$body")
call POST "/api/restaurants/$restaurant/menu-items" '{"name":"Watalappam","price":350.50,"category":"Dessert"}'
expect 201 "add menu item (350.50)"; dessert=$(jq -r .id <<<"$body")

call POST /api/restaurants '{"name":"Other Place '"$suffix"'","address":"2 Test St","cuisineType":"Thai"}'
other_restaurant=$(jq -r .id <<<"$body")
call POST "/api/restaurants/$other_restaurant/menu-items" '{"name":"Pad Thai","price":999.00}'
foreign_item=$(jq -r .id <<<"$body")

call POST /api/customers '{"name":"Smoke Tester","email":"smoke'"$suffix"'@example.com","phone":"+94770000000"}'
expect 201 "register customer"; customer=$(jq -r .id <<<"$body")
call POST "/api/customers/$customer/addresses" '{"label":"Home","addressLine":"42 Galle Rd","city":"Colombo","postalCode":"00300"}'
expect 201 "add saved address"; address=$(jq -r .id <<<"$body")

call POST /api/drivers '{"name":"Smoke Driver","phone":"+94771111111","vehicle_type":"Motorcycle"}'
expect 201 "register driver"

step "2. gRPC-backed validation rejects bad orders"
call POST /api/orders '{"customer_id":"'"$customer"'","restaurant_id":"'"$restaurant"'","address_id":"'"$address"'","items":[{"menu_item_id":"'"$foreign_item"'","quantity":1}]}'
expect 400 "item from another restaurant -> FAILED_PRECONDITION"
call POST /api/orders '{"customer_id":"'"$customer"'","restaurant_id":"00000000-0000-0000-0000-000000000000","delivery_address":"x","items":[{"menu_item_id":"'"$kottu"'","quantity":1}]}'
expect 400 "unknown restaurant -> NOT_FOUND"
call POST /api/orders '{"customer_id":"00000000-0000-0000-0000-000000000000","restaurant_id":"'"$restaurant"'","delivery_address":"x","items":[{"menu_item_id":"'"$kottu"'","quantity":1}]}'
expect 400 "unknown customer -> NOT_FOUND"
call POST /api/orders '{"customer_id":"'"$customer"'","restaurant_id":"'"$restaurant"'","items":[{"menu_item_id":"'"$kottu"'","quantity":1}]}'
expect 400 "no address given"

step "3. Place order: server-side pricing + saved address via gRPC"
# Client-supplied names/prices are ignored on purpose.
call POST /api/orders '{"customer_id":"'"$customer"'","restaurant_id":"'"$restaurant"'","address_id":"'"$address"'",
  "items":[{"menu_item_id":"'"$kottu"'","quantity":2,"unit_price":0.01,"menu_item_name":"free?"},{"menu_item_id":"'"$dessert"'","quantity":1}]}'
expect 201 "place order"
order=$(jq -r .id <<<"$body")
total=$(jq -r .total_amount <<<"$body")
[[ "$total" == "1850.5" || "$total" == "1850.50" ]] && ok "total priced by restaurant-service ($total)" || bad "total: expected 1850.50, got $total"
[[ "$(jq -r '.items[0].menu_item_name' <<<"$body")" == "Kottu" ]] && ok "item name from catalog" || bad "item name: $body"
[[ "$(jq -r .delivery_address <<<"$body")" == "42 Galle Rd, Colombo 00300" ]] && ok "saved address resolved by customer-service" || bad "address: $(jq -r .delivery_address <<<"$body")"

step "4. Kafka choreography"
wait_for "/api/deliveries/order/$order" .status ASSIGNED "delivery-service assigned a driver"
delivery=$(jq -r .id <<<"$body")
wait_for "/api/customers/$customer/order-history" '.[0].orderId' "$order" "customer-service recorded order history"

call PATCH "/api/deliveries/$delivery/status" '{"status":"PICKED_UP"}'
expect 200 "delivery -> PICKED_UP"
wait_for "/api/orders/$order" .status PICKED_UP "order-service mirrored PICKED_UP"
call PATCH "/api/deliveries/$delivery/status" '{"status":"DELIVERED"}'
expect 200 "delivery -> DELIVERED"
wait_for "/api/orders/$order" .status DELIVERED "order-service mirrored DELIVERED"

step "5. REST contract"
call GET "/api/orders?customer_id=$customer&page=1&size=5"
expect 200 "list orders"
[[ "$(jq -r .total <<<"$body")" == "1" ]] && ok "filtered list has 1 order" || bad "list: $body"
call DELETE "/api/orders/$order"
expect 400 "cancel after delivery is refused"
call GET /api/orders/00000000-0000-0000-0000-000000000000
expect 404 "unknown order"
call GET "/api/drivers?available=true"
expect 200 "list available drivers"

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[[ $fail -eq 0 ]]
