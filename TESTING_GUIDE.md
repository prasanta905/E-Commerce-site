# ShopSphere — Live Testing Guide

> Everything below was executed against the running local stack on **11 Nov 2026**.
> Two servers must be running for the web UI to work:

| Service | URL | Status |
|---|---|---|
| Backend (Spring Boot) | http://localhost:8080 | ✅ UP |
| Frontend (Vite dev) | http://localhost:5173 | ✅ UP |
| Swagger UI | http://localhost:8080/swagger-ui/index.html | ✅ 200 |
| OpenAPI JSON | http://localhost:8080/v3/api-docs | ✅ 65 paths |
| API base path | http://localhost:8080/api/v1 | ✅ |

If you restart your machine, start them again with:

```powershell
# Terminal 1 — backend (from ShopSphere\backend)
$env:Path = [Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [Environment]::GetEnvironmentVariable("Path","User") + ";C:\Users\ASUS\tools\apache-maven-3.9.9\bin"
mvn spring-boot:run "-Dspring-boot.run.profiles=local"

# Terminal 2 — frontend (from ShopSphere\frontend)
& "C:\Program Files\nodejs\npm.cmd" run dev
```

---

## 1. Demo accounts (seeded by Flyway V3)

| Role | Email | Password |
|---|---|---|
| Admin | `admin@shopsphere.local` | `Admin@123` |
| Customer 1 | `customer1@shopsphere.local` | `User@123` |
| Customer 2 | `customer2@shopsphere.local` | `User@123` |

Admin lands on `/admin`, customers land on the storefront. Access tokens live 15 minutes; the refresh token is an HttpOnly `SS_REFRESH` cookie that rotates on every `/auth/refresh`.

---

## 2. Customer journey (web UI — http://localhost:5173)

### 2.1 Register / Login
1. Click **Register** → create a new account (or **Sign In** with `customer1@shopsphere.local` / `User@123`).
2. Wrong password → red error toast, HTTP 401. Duplicate email → 400 with a clear message.
3. Reload the page → still logged in (refresh-cookie rotation works).

### 2.2 Browse the catalogue (18 seeded products)
1. **Explore Catalogue** → filter by category, brand, price range, sort by price/rating.
2. Search `Nexora` in the header → results filter live.
3. Open a product → image, gallery, MRP vs selling price, discount %, rating, stock badge, description/specs.
4. Add to **Wishlist** twice → the second click is idempotent (no duplicate row).

### 2.3 Cart
1. Add 2–3 products, change quantity, remove one.
2. Cart totals update; stock limits are enforced server-side (add more than stock → 400).

### 2.4 Coupon
- Working code: **`FESTIVE25`** (25% off, seeded).
- Also seeded: **`WELCOME10`** (10% off first order).
- Apply an invalid code → 400 `INVALID_COUPON`.
- Per-user limit: apply `FESTIVE25`, place the order, try again → 400 (limit reached). This was live-verified.

### 2.5 Checkout
1. Go to **Checkout** → select/enter a shipping address.
2. Choose delivery speed:
   - **STANDARD** — ₹49, free above ₹999
   - **EXPRESS** — ₹149, free above ₹1999
3. Tick **I agree to the Terms** (required — backend rejects without `termsAccepted: "true"`).
4. Watch the **live totals preview** (server-computed: subtotal, discount, shipping, tax, grand total).
5. Payment method:
   - **Cash on Delivery** → order becomes `CONFIRMED` immediately.
   - **Razorpay** → order becomes `PENDING` and the Razorpay modal opens. See §6 for test keys.

### 2.6 Orders
1. **My Orders** → status badges (`CONFIRMED`, `SHIPPED`, `DELIVERED`, …).
2. Open an order → item snapshot, address, payment summary, status timeline.
3. **Download Invoice** → PDF (verified: `%PDF`, ~1.5 KB). Only the owner can download it; another user gets 404.
4. **Request Return** (only `DELIVERED` orders) → order becomes `RETURN_REQUESTED`, then admin approves.

### 2.7 Reviews
1. On a `DELIVERED` product, submit a rating + comment.
2. Review appears as `PENDING` until an admin approves it (moderation), then shows publicly.

### 2.8 Auth / RBAC checks
- Logged out, open `/account` → redirected to login.
- Logged in as customer, open `/admin` → **403** page (not a 500).
- Call an admin API with a customer token → HTTP **403**; no token → **401**.

---

## 3. Admin journey (web UI — log in as `admin@shopsphere.local` / `Admin@123`)

| Area | What to test |
|---|---|
| **Dashboard** | Revenue, orders today, low-stock alerts, top products, 7-day chart |
| **Products** | Create/edit/delete; upload images (multipart → saved under `/uploads/products/`); set price, MRP, stock, category, brand |
| **Categories / Brands** | Full CRUD; deleting an in-use category is blocked |
| **Inventory** | Adjust stock with a reason → every change writes an `InventoryAudit` row (`COMMIT`/`RETURN`/`ADJUST`) |
| **Orders** | Move status through the FSM: `PENDING→CONFIRMED→PROCESSING→SHIPPED→OUT_FOR_DELIVERY→DELIVERED`; illegal jumps return **409** (verified, e.g. `CONFIRMED→DELIVERED`) |
| **Shipments** | Upsert carrier + tracking number per order |
| **Returns** | Approve → `RETURNED` + automatic restock (`RETURN +n` audit) + payment `REFUNDED` + `Refund` ledger row (online payments only) |
| **Coupons** | CRUD, percent/flat, min order, per-user & global limits, active window |
| **Customers** | List, view order history, enable/disable accounts |
| **Reviews** | Approve / reject pending reviews |

---

## 4. API testing (Swagger + Postman)

### Swagger UI
Open http://localhost:8080/swagger-ui/index.html → **Authorize** → paste the `accessToken` from `POST /api/v1/auth/login` → try any endpoint. 65 documented paths across auth, catalog, cart, wishlist, checkout, orders, payments, returns, reviews, admin, invoice.

### Postman
Import `ShopSphere_Postman_Collection.json` (repo root). It contains the login requests that auto-save `accessToken`/`refreshToken` into collection variables, plus every major flow.

### Raw cURL/PowerShell example
```powershell
# login
$login = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/v1/auth/login" `
  -ContentType "application/json" `
  -Body '{"email":"customer1@shopsphere.local","password":"User@123"}'

# browse products
Invoke-RestMethod "http://localhost:8080/api/v1/products?page=0&size=5"

# authenticated call
Invoke-RestMethod "http://localhost:8080/api/v1/cart" `
  -Headers @{ Authorization = "Bearer $($login.data.accessToken)" }
```

Note: responses use the envelope `{ "success": true, "data": ... }`; lists are paginated as `data.items` + `data.meta`.

---

## 5. Automated tests

```powershell
# Backend — 20 integration tests (context + auth/RBAC + checkout + YouPI webhooks)
cd backend
$env:Path = [Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [Environment]::GetEnvironmentVariable("Path","User") + ";C:\Users\ASUS\tools\apache-maven-3.9.9\bin"
mvn test

# Frontend — production build (TypeScript strict)
cd ../frontend
& "C:\Program Files\nodejs\npm.cmd" run build
```

Expected: `Tests run: 20, Failures: 0, Errors: 0` and `BUILD SUCCESS`; frontend build emits `dist/` with 0 TS errors.

Test classes:
- `ShopSphereApplicationTests` — Spring context boots against the test schema.
- `AuthRbacIntegrationTest` (7) — register/duplicate/wrong password, anonymous 401, customer→admin 403, admin 200, cross-user order hidden.
- `CheckoutFlowIntegrationTest` (3) — full COD purchase with idempotency replay, unknown coupon 4xx, missing terms rejection.
- `YoupiWebhookIntegrationTest` (9) — reconciled UPI settlement, observer-only/non-reconciled ignores, duplicate delivery, bad signature, amount mismatch, delivery-id reuse, earlier-attempt settlement and duplicate-settlement refund flagging.

---

## 6. Razorpay (online payment) — optional

Razorpay is **disabled by default**; the checkout modal returns HTTP **503** `RAZORPAY_NOT_CONFIGURED` with a "use Cash on Delivery" message. To test the real online flow:

1. Create a Razorpay **test-mode** account and get `key_id` / `key_secret`.
2. Put them in the gitignored `backend/src/main/resources/application-local.properties`:

```properties
razorpay.enabled=true
razorpay.key-id=rzp_test_XXXXXXXX
razorpay.key-secret=XXXXXXXXXXXXXXXX
razorpay.webhook-secret=some-dev-secret
```

3. Restart the backend. Place an order with **Razorpay** → pay with test card `4111 1111 1111 1111`, any future expiry, CVV `123`.
4. Signature verification is HMAC-SHA256 server-side (`POST /payments/verify`); replayed verification is idempotent.
5. Webhook endpoint: `POST /api/v1/payments/webhook` (raw body HMAC, idempotent by event id).
6. Dismiss the modal → order stays `PENDING`; **Order Detail** shows a "Complete Payment" banner to resume.

### YouPI PG (self-hosted UPI) — optional, experimental

UPI uses a separately hosted YouPI PG gateway (see README §4.5 and `DEPLOYMENT.md` §2.10). The browser is redirected by `POST /api/v1/payments/orders/{id}/youpi-session`; the order stays `PENDING` until a signed `RECONCILED` webhook. To exercise the contract without real money, send a raw JSON body signed with `HMAC-SHA256(YOUPI_WEBHOOK_SECRET)` in `X-Gateway-Signature` and a unique `X-Gateway-Delivery-Id`. `OBSERVED_SUCCESS`/`SUCCESS` must never confirm an order; only `event=RECONCILED` + `method=UPI` + `settlementState=RECONCILED` does. `YoupiWebhookIntegrationTest` covers this end-to-end over HTTP.

---

## 7. Known limitations (honest list)

1. **Docker not installed** — `docker-compose.yml` + both Dockerfiles + nginx config are written and reviewed but **never executed**. Local Maven/npm runs are the verified path.
2. **Razorpay / YouPI** require your own credentials (above). Without them, online payment returns 503; COD works fully. YouPI is experimental and needs a separately running gateway plus a reconciled bank statement before a UPI order settles.
3. **Seed data** — 18 products; some share placeholder images (9 files reused). Everything else is real DB data, not mocks.
4. **COD refunds are offline** — a returned COD order restocks inventory and marks the order `RETURNED`, but no `Refund` ledger row is created because there was no captured online payment. Documented in the admin Returns screen.
5. Dev database contains smoke-test artifacts (orders 1–2, review id 5, wishlist items, `it-*@shopsphere.local` users). Harmless; wipe with Flyway clean or drop/recreate the `shopsphere` schema if you want a pristine demo.
6. Email is not wired to an SMTP provider — verification/reset tokens are generated but not delivered; use the seeded verified accounts.
7. GitHub push is pending: remote `prasanta905/E-Commerce-site` returned "repository not found"; the work is committed locally (`7d9c31e`) on branch `Main`.

---

## 8. Quick 5-minute smoke script

1. Open http://localhost:5173 → browse → add 2 items → cart.
2. Apply `FESTIVE25` → checkout → STANDARD → tick terms → **COD** → place order.
3. My Orders → open order → download invoice → request return? (only after delivery).
4. Log out → log in as admin → Orders → advance the order status step by step.
5. Admin → Returns → approve → verify stock increased in Inventory + payment `REFUNDED`.
6. Swagger → `GET /api/v1/admin/dashboard` with the admin token → 200; with the customer token → 403.
