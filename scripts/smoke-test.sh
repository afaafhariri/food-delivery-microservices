#!/usr/bin/env bash
# End-to-end smoke test through the API gateway:
#   Keycloak login -> REST -> order-service -> gRPC (restaurant + customer, caller's token
#   forwarded) -> Postgres -> Kafka -> delivery-service (driver assignment) -> Kafka ->
#   order-service (status sync); cancellation frees the driver; every role is refused where it
#   should be (401 without a token, 403 for someone else's data).
#
# Usage: scripts/smoke-test.sh [gateway-url]          (requires curl + jq)
#
# Logs in as the test users of the dev realm (keycloak/dev-users): admin, customer1, customer2,
# restaurant1, driver1. Environment:
#   KEYCLOAK_URL     default http://localhost:8180/auth for a localhost gateway, else https://<host>/auth
#   SMOKE_PASSWORD   default quickbite123
# Safe to run repeatedly: customer1 and driver1 keep the profiles they created on the first run.
set -euo pipefail

GW="${1:-http://localhost:8080}"
if [[ -z "${KEYCLOAK_URL:-}" ]]; then
  if [[ "$GW" =~ ^http://(localhost|127\.0\.0\.1)(:[0-9]+)?$ ]]; then
    KEYCLOAK_URL="http://localhost:8180/auth"
  else
    host="${GW#*://}"; KEYCLOAK_URL="https://${host%%/*}/auth"
  fi
fi
PASSWORD="${SMOKE_PASSWORD:-quickbite123}"
pass=0
fail=0

ok()   { printf '  \033[32m✔\033[0m %s\n' "$1"; pass=$((pass + 1)); }
bad()  { printf '  \033[31m✘\033[0m %s\n' "$1"; fail=$((fail + 1)); }
step() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# login USER -> sets TOKEN_<user> (bash 3 compatible, no associative arrays)
login() {
  local token
  token=$(curl -sS -X POST "$KEYCLOAK_URL/realms/quickbite/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=quickbite-app \
    --data-urlencode "username=$1" --data-urlencode "password=$PASSWORD" | jq -r '.access_token // empty')
  if [[ -z "$token" ]]; then
    bad "log in as $1 at $KEYCLOAK_URL"
    printf '\n%d passed, %d failed (cannot continue without tokens)\n' "$pass" "$fail"
    exit 1
  fi
  printf -v "TOKEN_$1" '%s' "$token"
  ok "logged in as $1"
}

# as USER | as nobody   -> who the next calls are made as
TOKEN=""
as() { if [[ "$1" == nobody ]]; then TOKEN=""; else local v="TOKEN_$1"; TOKEN="${!v}"; fi; }

# call METHOD PATH [JSON]  -> sets $status and $body
call() {
  local out
  local auth=()
  [[ -n "$TOKEN" ]] && auth=(-H "Authorization: Bearer $TOKEN")
  out=$(curl -sS -X "$1" "$GW$2" -H 'Content-Type: application/json' ${auth[@]+"${auth[@]}"} ${3:+-d "$3"} -w $'\n%{http_code}')
  status="${out##*$'\n'}"
  body="${out%$'\n'*}"
}

expect() { # expect CODE DESCRIPTION
  if [[ "$status" == "$1" ]]; then ok "$2 ($status)"; else bad "$2: expected $1, got $status: $body"; fi
}

# wait_for PATH JQ_FILTER EXPECTED DESCRIPTION  (polls as the current user; events are asynchronous)
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

step "0. Authentication ($KEYCLOAK_URL)"
for user in admin customer1 customer2 restaurant1 driver1; do login "$user"; done
as nobody
call GET /api/restaurants
expect 200 "browsing restaurants needs no login"
call POST /api/orders '{}'
expect 401 "placing an order without a token is rejected by the gateway"
as customer1
call POST /api/restaurants '{"name":"Nope","address":"x","cuisine_type":"x"}'
expect 403 "a customer cannot create a restaurant"

step "1. Arrange: restaurant + menu (restaurant1), customer profile + address (customer1), driver (driver1)"
as restaurant1
call POST /api/restaurants '{"name":"Smoke Kitchen '"$suffix"'","address":"1 Test St","cuisine_type":"Fusion","operating_hours":"09:00-21:00"}'
expect 201 "create restaurant"; restaurant=$(jq -r .id <<<"$body")
[[ "$(jq -r .cuisine_type <<<"$body")" == "Fusion" ]] && ok "restaurant JSON is snake_case" || bad "snake_case: $body"

call POST "/api/restaurants/$restaurant/menu-items" '{"name":"Kottu","description":"Chopped roti","price":750.00,"category":"Main"}'
expect 201 "add menu item (750.00)"; kottu=$(jq -r .id <<<"$body")
call POST "/api/restaurants/$restaurant/menu-items" '{"name":"Watalappam","price":350.50,"category":"Dessert"}'
expect 201 "add menu item (350.50)"; dessert=$(jq -r .id <<<"$body")

call POST /api/restaurants '{"name":"Other Place '"$suffix"'","address":"2 Test St","cuisine_type":"Thai"}'
other_restaurant=$(jq -r .id <<<"$body")
call POST "/api/restaurants/$other_restaurant/menu-items" '{"name":"Pad Thai","price":999.00}'
foreign_item=$(jq -r .id <<<"$body")

as customer2
call POST "/api/restaurants/$restaurant/menu-items" '{"name":"Free food","price":1.00}'
expect 403 "another account cannot change restaurant1's menu"

as customer1
call POST /api/customers '{"name":"Smoke Tester","email":"smoke'"$suffix"'@example.com","phone":"+94770000000"}'
if [[ "$status" == 409 ]]; then
  call GET /api/customers/me
  expect 200 "customer1 already has a profile (reused)"
else
  expect 201 "create customer1's profile"
fi
customer=$(jq -r .id <<<"$body")
call POST "/api/customers/$customer/addresses" '{"label":"Home","address_line":"42 Galle Rd","city":"Colombo","postal_code":"00300"}'
expect 201 "add saved address"; address=$(jq -r .id <<<"$body")

as customer2
call GET "/api/customers/$customer"
expect 403 "customer2 cannot read customer1's profile"

as driver1
call POST /api/drivers '{"name":"Smoke Driver","phone":"+94771111111","vehicle_type":"Motorcycle"}'
if [[ "$status" == 409 ]]; then
  call GET /api/drivers/me
  expect 200 "driver1 is already registered (reused)"
else
  expect 201 "register driver1"
fi
driver1=$(jq -r .id <<<"$body")
call PUT "/api/drivers/$driver1" '{"available":true}'
expect 200 "driver1 is available"

step "2. gRPC-backed validation rejects bad orders"
as customer1
call POST /api/orders '{"customer_id":"'"$customer"'","restaurant_id":"'"$restaurant"'","address_id":"'"$address"'","items":[{"menu_item_id":"'"$foreign_item"'","quantity":1}]}'
expect 400 "item from another restaurant -> FAILED_PRECONDITION"
call POST /api/orders '{"customer_id":"'"$customer"'","restaurant_id":"00000000-0000-0000-0000-000000000000","delivery_address":"x","items":[{"menu_item_id":"'"$kottu"'","quantity":1}]}'
expect 400 "unknown restaurant -> NOT_FOUND"
call POST /api/orders '{"customer_id":"00000000-0000-0000-0000-000000000000","restaurant_id":"'"$restaurant"'","delivery_address":"x","items":[{"menu_item_id":"'"$kottu"'","quantity":1}]}'
expect 400 "unknown customer -> NOT_FOUND"
call POST /api/orders '{"customer_id":"'"$customer"'","restaurant_id":"'"$restaurant"'","items":[{"menu_item_id":"'"$kottu"'","quantity":1}]}'
expect 400 "no address given"
as customer2
call POST /api/orders '{"customer_id":"'"$customer"'","restaurant_id":"'"$restaurant"'","address_id":"'"$address"'","items":[{"menu_item_id":"'"$kottu"'","quantity":1}]}'
expect 403 "customer2 cannot order on customer1's profile"

step "3. Place order: server-side pricing + saved address via gRPC"
as customer1
# Client-supplied names/prices are ignored on purpose.
call POST /api/orders '{"customer_id":"'"$customer"'","restaurant_id":"'"$restaurant"'","address_id":"'"$address"'",
  "items":[{"menu_item_id":"'"$kottu"'","quantity":2,"unit_price":0.01,"menu_item_name":"free?"},{"menu_item_id":"'"$dessert"'","quantity":1}]}'
expect 201 "place order"
order=$(jq -r .id <<<"$body")
total=$(jq -r .total_amount <<<"$body")
[[ "$total" == "1850.5" || "$total" == "1850.50" ]] && ok "total priced by restaurant-service ($total)" || bad "total: expected 1850.50, got $total"
[[ "$(jq -r '.items[0].menu_item_name' <<<"$body")" == "Kottu" ]] && ok "item name from catalog" || bad "item name: $body"
[[ "$(jq -r .delivery_address <<<"$body")" == "42 Galle Rd, Colombo 00300" ]] && ok "saved address resolved by customer-service" || bad "address: $(jq -r .delivery_address <<<"$body")"
as customer2
call GET "/api/orders/$order"
expect 403 "customer2 cannot read customer1's order"

step "4. Kafka choreography"
as admin
wait_for "/api/deliveries/order/$order" .status ASSIGNED "delivery-service assigned a driver"
delivery=$(jq -r .id <<<"$body")
assigned=$(jq -r .driver_id <<<"$body")
as customer1
wait_for "/api/customers/$customer/order-history" "map(select(.order_id == \"$order\")) | length" 1 "customer-service recorded order history"

# The longest-waiting free driver gets the order. That is driver1 on a fresh database; with older
# drivers in it (seed data, earlier runs) it can be one of them, which only an admin may move.
if [[ "$assigned" == "$driver1" ]]; then
  ok "assigned to driver1, who moves it"
  mover=driver1
else
  as driver1
  call PATCH "/api/deliveries/$delivery/status" '{"status":"PICKED_UP"}'
  expect 403 "driver1 cannot move a delivery assigned to another driver"
  mover=admin
fi
as "$mover"
call PATCH "/api/deliveries/$delivery/status" '{"status":"PICKED_UP"}'
expect 200 "delivery -> PICKED_UP (as $mover)"
as customer1
wait_for "/api/orders/$order" .status PICKED_UP "order-service mirrored PICKED_UP"
as "$mover"
call PATCH "/api/deliveries/$delivery/status" '{"status":"DELIVERED"}'
expect 200 "delivery -> DELIVERED (as $mover)"
as customer1
wait_for "/api/orders/$order" .status DELIVERED "order-service mirrored DELIVERED"

step "5. Cancellation: order.cancelled frees the driver"
as customer1
call POST /api/orders '{"customer_id":"'"$customer"'","restaurant_id":"'"$restaurant"'","address_id":"'"$address"'","items":[{"menu_item_id":"'"$kottu"'","quantity":1}]}'
expect 201 "place a second order"
order2=$(jq -r .id <<<"$body")
as admin
wait_for "/api/deliveries/order/$order2" .status ASSIGNED "driver assigned to the second order"
driver2=$(jq -r .driver_id <<<"$body")
as customer2
call DELETE "/api/orders/$order2"
expect 403 "customer2 cannot cancel customer1's order"
as customer1
call DELETE "/api/orders/$order2"
expect 200 "cancel the second order"
[[ "$(jq -r .status <<<"$body")" == "CANCELLED" ]] && ok "order is CANCELLED" || bad "cancel: $body"
as admin
wait_for "/api/deliveries/order/$order2" .status CANCELLED "delivery-service cancelled the delivery"
wait_for "/api/drivers?available=true" "map(select(.id == \"$driver2\")) | length" 1 "the driver is available again"

step "6. REST contract and access rules"
as customer1
call GET "/api/orders?customer_id=$customer&page=1&size=100"
expect 200 "list orders"
[[ "$(jq --arg a "$order" --arg b "$order2" '[.data[].id] | (index($a) != null and index($b) != null)' <<<"$body")" == "true" ]] \
  && ok "customer1's list contains both orders" || bad "list: $body"
[[ "$(jq '[.data[] | select(.customer_id != "'"$customer"'")] | length' <<<"$body")" == "0" ]] \
  && ok "and nothing that belongs to anyone else" || bad "list leaks other orders: $body"
call DELETE "/api/orders/$order"
expect 400 "cancel after delivery is refused"
call GET /api/orders/00000000-0000-0000-0000-000000000000
expect 404 "unknown order"
call GET "/api/drivers?available=true"
expect 403 "customers cannot list drivers"
as driver1
call GET /api/orders
expect 403 "drivers cannot list orders"
as admin
call GET "/api/drivers?available=true"
expect 200 "admin lists available drivers"

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[[ $fail -eq 0 ]]
