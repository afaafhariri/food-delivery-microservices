// Load test: latency percentiles (p50, p90, p95, p99) of QuickBite's busiest paths, through the API gateway.
//
//   k6 run scripts/load-test.js                                   # Compose on localhost, default rates
//   BROWSE_RATE=40 ORDER_RATE=10 DURATION=5m k6 run scripts/load-test.js
//   GATEWAY_URL=https://203.0.113.10.sslip.io k6 run scripts/load-test.js   (Keycloak then defaults to <host>/auth)
//
// Open workload model: k6 starts iterations at a fixed rate whether or not earlier ones have finished, the way
// real users don't wait for each other. A slow server builds a queue, and the queueing shows up in the measured
// latency. (A closed model, "N users in a loop", slows down with the server and hides it: coordinated omission.)
//
// A warm-up runs first and is left out of the results, so JIT compilation and cold connection pools don't count.
// Then two scenarios run side by side:
//   browse   anonymous: list restaurants, open one, read its menu       restaurant-service
//   order    customer1: place an order, read it back                    order-service -> gRPC to restaurant- and
//                                                                       customer-service -> Postgres -> outbox
//
// Exits with code 99 if a latency or error threshold is broken. Environment (default in brackets):
//   GATEWAY_URL [http://localhost:8080]  KEYCLOAK_URL [as smoke-test.sh]  PASSWORD [quickbite123]
//   BROWSE_RATE [10 iterations/s]  ORDER_RATE [2 iterations/s]  WARMUP [30s]  DURATION [2m]
//   READ_P95_MS [300]  READ_P99_MS [800]  WRITE_P95_MS [800]  WRITE_P99_MS [1500]  MAX_ERROR_RATE [0.01]
//   SUMMARY_JSON   also write k6's full summary to this file
//
// Each run creates one restaurant (restaurant1) with three menu items and one address on customer1's profile, and
// places ORDER_RATE x DURATION orders. Nobody accepts them; they stay PLACED.
import http from 'k6/http';
import { check } from 'k6';

const GW = (__ENV.GATEWAY_URL || 'http://localhost:8080').replace(/\/$/, '');
const KEYCLOAK = (__ENV.KEYCLOAK_URL || defaultKeycloak(GW)).replace(/\/$/, '');
const PASSWORD = __ENV.PASSWORD || 'quickbite123';

const BROWSE_RATE = num('BROWSE_RATE', 10);
const ORDER_RATE = num('ORDER_RATE', 2);
const WARMUP = __ENV.WARMUP || '30s';
const DURATION = __ENV.DURATION || '2m';
const READ = { p95: num('READ_P95_MS', 300), p99: num('READ_P99_MS', 800) };
const WRITE = { p95: num('WRITE_P95_MS', 800), p99: num('WRITE_P99_MS', 1500) };
const MAX_ERROR_RATE = num('MAX_ERROR_RATE', 0.01);

// Endpoint tag -> latency budget. Only requests made in the measured phase count.
const ENDPOINTS = {
  list_restaurants: READ,
  get_restaurant: READ,
  get_menu: READ,
  place_order: WRITE,
  get_order: READ,
};

const thresholds = {
  dropped_iterations: ['count==0'],    // k6 ran out of VUs: the arrival rate was not delivered
  [`http_req_failed{phase:measure}`]: [`rate<${MAX_ERROR_RATE}`],
};
for (const [endpoint, budget] of Object.entries(ENDPOINTS)) {
  const sel = `{phase:measure,endpoint:${endpoint}}`;
  thresholds[`http_req_duration${sel}`] = [`p(95)<${budget.p95}`, `p(99)<${budget.p99}`];
  thresholds[`http_req_failed${sel}`] = [`rate<${MAX_ERROR_RATE}`];
  thresholds[`http_reqs${sel}`] = ['count>0'];   // also makes the request count appear in the summary
}

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate', exec: 'warmup',
      rate: Math.max(1, Math.ceil((BROWSE_RATE + ORDER_RATE) / 4)), timeUnit: '1s', duration: WARMUP,
      preAllocatedVUs: 5, maxVUs: 50, tags: { phase: 'warmup' },
    },
    browse: {
      executor: 'constant-arrival-rate', exec: 'browse',
      rate: BROWSE_RATE, timeUnit: '1s', duration: DURATION, startTime: WARMUP,
      preAllocatedVUs: vus(BROWSE_RATE), maxVUs: maxVus(BROWSE_RATE), tags: { phase: 'measure' },
    },
    order: {
      executor: 'constant-arrival-rate', exec: 'order',
      rate: ORDER_RATE, timeUnit: '1s', duration: DURATION, startTime: WARMUP,
      preAllocatedVUs: vus(ORDER_RATE), maxVUs: maxVus(ORDER_RATE), tags: { phase: 'measure' },
    },
  },
  thresholds,
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)', 'max'],
  setupTimeout: '2m',
};

// ── Arrange: a restaurant with a menu, and customer1 with an address (as the smoke test does) ─────────────────────

export function setup() {
  const restaurantToken = login('restaurant1');
  const customerToken = login('customer1');
  const suffix = Date.now();

  const restaurant = expect(201, 'create restaurant', post('/api/restaurants', restaurantToken, {
    name: `Load Test Kitchen ${suffix}`, address: '1 Bench St', cuisine_type: 'Fusion', operating_hours: '00:00-23:59',
  }));
  const items = [['Kottu', 750.0, 'Main'], ['Hoppers', 450.0, 'Main'], ['Watalappam', 350.5, 'Dessert']].map(
    ([name, price, category]) => expect(201, 'add menu item',
      post(`/api/restaurants/${restaurant.id}/menu-items`, restaurantToken, { name, price, category })).id);

  let res = post('/api/customers', customerToken,
    { name: 'Load Tester', email: `load${suffix}@example.com`, phone: '+94770000000' });
  const reused = res.status === 409;                                         // customer1 already has a profile
  if (reused) res = get('/api/customers/me', customerToken);
  const customer = expect(reused ? 200 : 201, 'customer1 profile', res);
  const address = expect(201, 'add address', post(`/api/customers/${customer.id}/addresses`, customerToken,
    { label: 'Bench', address_line: '1 Bench St', city: 'Colombo', postal_code: '00300' }));

  console.log(`load test: ${GW}, browse ${BROWSE_RATE}/s + order ${ORDER_RATE}/s for ${DURATION} after ${WARMUP} warm-up`);
  return { restaurant: restaurant.id, items, customer: customer.id, address: address.id,
           token: customerToken, tokenAt: Date.now() };
}

// ── The iterations ────────────────────────────────────────────────────────────────────────────────────────────────

export function browse(data) {
  const list = http.get(`${GW}/api/restaurants?page=0&size=10`, { tags: { endpoint: 'list_restaurants' } });
  check(list, { 'list restaurants: 200': (r) => r.status === 200 });
  const restaurants = list.status === 200 ? list.json('content') || [] : [];
  const id = restaurants.length ? restaurants[Math.floor(Math.random() * restaurants.length)].id : data.restaurant;

  check(http.get(`${GW}/api/restaurants/${id}`, { tags: { endpoint: 'get_restaurant' } }),
    { 'get restaurant: 200': (r) => r.status === 200 });
  check(http.get(`${GW}/api/restaurants/${id}/menu-items`, { tags: { endpoint: 'get_menu' } }),
    { 'get menu: 200': (r) => r.status === 200 });
}

export function order(data) {
  const token = customerToken(data);
  const items = data.items.filter(() => Math.random() < 0.6);
  const body = {
    customer_id: data.customer, restaurant_id: data.restaurant, address_id: data.address,
    items: (items.length ? items : [data.items[0]]).map((id) => ({ menu_item_id: id, quantity: 1 + Math.floor(Math.random() * 3) })),
  };
  const placed = http.post(`${GW}/api/orders`, JSON.stringify(body), auth(token, 'place_order'));
  if (!check(placed, { 'place order: 201': (r) => r.status === 201 })) return;

  check(http.get(`${GW}/api/orders/${placed.json('id')}`, auth(token, 'get_order')),
    { 'get order: 200': (r) => r.status === 200 });
}

export function warmup(data) {
  browse(data);
  if (Math.random() < ORDER_RATE / (BROWSE_RATE + ORDER_RATE)) order(data);
}

// ── Results: one line per endpoint, then the thresholds ───────────────────────────────────────────────────────────

export function handleSummary(data) {
  const ms = (v) => (v === undefined ? '-' : v < 10 ? v.toFixed(1) : Math.round(v).toString());
  const cols = ['requests', 'p50', 'p90', 'p95', 'p99', 'p99.9', 'max', 'errors'];
  const rows = Object.keys(ENDPOINTS).map((endpoint) => {
    const sel = `{phase:measure,endpoint:${endpoint}}`;
    const d = (data.metrics[`http_req_duration${sel}`] || {}).values || {};
    const n = (data.metrics[`http_reqs${sel}`] || {}).values || {};
    const f = (data.metrics[`http_req_failed${sel}`] || {}).values || {};
    return [endpoint, String(n.count || 0), ms(d.med), ms(d['p(90)']), ms(d['p(95)']), ms(d['p(99)']),
      ms(d['p(99.9)']), ms(d.max), `${((f.rate || 0) * 100).toFixed(2)}%`];
  });
  const widths = [16, ...cols.map((c) => Math.max(c.length, 8))];
  const line = (cells) => cells.map((c, i) => (i === 0 ? c.padEnd(widths[i]) : c.padStart(widths[i]))).join('  ');

  const out = ['', `Latency in ms, measured phase only (${DURATION}; warm-up of ${WARMUP} excluded)`, '',
    line(['endpoint', ...cols]), line(widths.map((w) => '-'.repeat(w))), ...rows.map(line), ''];

  const dropped = ((data.metrics.dropped_iterations || {}).values || {}).count || 0;
  out.push(`dropped iterations: ${dropped}${dropped ? '  (k6 had too few VUs; the requested rate was not sent)' : ''}`);
  out.push('', 'Thresholds:');
  for (const metric of Object.keys(thresholds)) {          // the order they are declared in: grouped by endpoint
    for (const [rule, t] of Object.entries((data.metrics[metric] || {}).thresholds || {})) {
      if (rule === 'count>0' && t.ok) continue;
      out.push(`  ${t.ok ? '✔' : '✘'} ${metric}  ${rule}`);
    }
  }
  out.push('');

  const files = { stdout: out.join('\n') + '\n' };
  if (__ENV.SUMMARY_JSON) files[__ENV.SUMMARY_JSON] = JSON.stringify(data, null, 2);
  return files;
}

// ── Helpers ───────────────────────────────────────────────────────────────────────────────────────────────────────

function login(username) {
  const res = http.post(`${KEYCLOAK}/realms/quickbite/protocol/openid-connect/token`,
    { grant_type: 'password', client_id: 'quickbite-app', username, password: PASSWORD },
    { tags: { endpoint: 'login' } });
  if (res.status !== 200) throw new Error(`log in as ${username} at ${KEYCLOAK}: HTTP ${res.status} ${res.body}`);
  return res.json('access_token');
}

// Tokens live 15 minutes. Runs longer than ~12 minutes log in again, once per VU, outside the measured endpoints.
let ownToken = null;
let ownTokenAt = 0;
function customerToken(data) {
  if (Date.now() - data.tokenAt < 12 * 60 * 1000) return data.token;
  if (!ownToken || Date.now() - ownTokenAt > 12 * 60 * 1000) {
    ownToken = login('customer1');
    ownTokenAt = Date.now();
  }
  return ownToken;
}

function auth(token, endpoint) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, tags: { endpoint } };
}

function post(path, token, body) {
  return http.post(`${GW}${path}`, JSON.stringify(body), auth(token, 'setup'));
}

function get(path, token) {
  return http.get(`${GW}${path}`, auth(token, 'setup'));
}

function expect(status, what, res) {
  if (res.status !== status) throw new Error(`${what}: expected ${status}, got ${res.status} ${res.body}`);
  return res.json();
}

function num(name, fallback) {
  const v = __ENV[name];
  if (v === undefined || v === '') return fallback;
  const n = Number(v);
  if (!Number.isFinite(n) || n < 0) throw new Error(`${name} must be a number, got "${v}"`);
  return n;
}

function vus(rate) {
  return Math.max(2, Math.ceil(rate * 2));
}

function maxVus(rate) {
  return Math.max(50, Math.ceil(rate * 20));
}

function defaultKeycloak(gateway) {
  const m = gateway.match(/^https?:\/\/([^/:]+)(:\d+)?/);
  if (!m) return 'http://localhost:8180/auth';
  return m[1] === 'localhost' || m[1] === '127.0.0.1' ? 'http://localhost:8180/auth' : `https://${m[1]}/auth`;
}
