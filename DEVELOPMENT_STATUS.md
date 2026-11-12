# ShopSphere — Project Status & Delivery Report

_Updated: End of Full Stack Integration & Verification Cycle_

## 1. System Status Summary

| Component | Status | Port / URL | Details |
|---|---|---|---|
| **Backend** | ✅ **UP & RUNNING** | `http://localhost:8080` | Spring Boot 3.5.16, Java 21 LTS, Actuator, Flyway V1-V5 |
| **Frontend** | ✅ **UP & RUNNING** | `http://127.0.0.1:5173` | React 19, TypeScript, Vite, Tailwind CSS, Zustand, TanStack Query |
| **Database** | ✅ **ACTIVE** | `localhost:3306` | MySQL 8.4 LTS (`shopsphere`), 31 tables, seeded catalog & users |
| **YouPI PG (UPI)** | ⚪ **NOT RUNNING** | `http://localhost:6100` | Optional self-hosted gateway; webhook contract covered by tests, live session not verified |
| **API Docs** | ✅ **LIVE** | `http://localhost:8080/swagger-ui.html` | OpenAPI 3.0 / Swagger UI (~60 endpoints documented) |
| **Postman** | ✅ **READY** | `ShopSphere_Postman_Collection.json` | 5 folders with auto-token capture for Admin & Customer |

---

## 2. Default Demo Accounts

| Role | Email | Password | Access Scope |
|---|---|---|---|
| **System Admin** | `admin@shopsphere.local` | `Admin@123` | Full Admin Dashboard, Catalog CRUD, Order State Machine, Inventory, Coupons, Customers, Returns |
| **Customer** | `customer1@shopsphere.local` | `User@123` | Storefront browsing, Cart, Wishlist, Checkout, Order Tracking, Profile & Address Book |

---

## 3. Audit, Bug Diagnoses & Fixes Applied

### Backend Issues Identified & Resolved
1. **Compilation Failures**:
   - `ReturnRequestRepository.java`: Fixed missing `ReturnStatus` import and missing `Pageable` in repository query method.
   - `BadRequest.java`: Added `BadRequest.of(String code, String message)` factory method used in `AdminDashboardService.java`.
   - `Order.java`: Removed phantom `paidAt` property (payment timestamps belong to the `payments` table).
2. **Hibernate 6 / MySQL Schema Validation Mismatches**:
   - Enum columns in `Coupon`, `InventoryTransaction`, `Order`, `OrderStatusHistory`, `Payment`, `PaymentTransaction`, and `Refund` triggered schema validation mismatch (`found varchar, expecting tinyint`). Added `@Enumerated(EnumType.STRING)` to all enum fields.
   - `@Lob` annotations on `Notification`, `ReturnRequest`, `PaymentTransaction`, and `Review` triggered MySQL `CLOB`/`TINYTEXT` validation conflicts. Replaced `@Lob` with `@Column(columnDefinition = "TEXT")`.
   - Explicit table name mappings `@Table(name = "...")` added to `EmailVerificationToken`, `PasswordResetToken`, and `Inventory`.
   - Removed `extends TimestampedEntity` from join/line tables lacking audit timestamps (`OrderItem`, `ProductImage`, `ProductSpecification`).
3. **Database Migration Sync**:
   - Created missing Flyway migration `V4__add_reviews_table.sql` providing the `reviews` table and initial product reviews.
4. **Spring Actuator Health**:
   - Disabled Actuator `mail` health check in `application.yml` (`management.health.mail.enabled: false`) to prevent false-positive `503 DOWN` errors when offline in local dev mode.
5. **Missing Endpoints Implemented**:
   - Implemented `CouponController.java` (`GET /api/v1/coupons/apply`).
   - Implemented complete `AdminController.java` (`/api/v1/admin/*`) covering:
     - Dashboard overview metrics & analytics
     - Order status transitions via strict state machine
     - Product catalog CRUD and active publication toggle
     - Category & Brand taxonomy management
     - Customer directory and suspension toggle
     - Inventory stock adjustments with audit logging
     - Promotional coupon vouchers creation and deletion
     - Reverse logistics return request approvals and rejections
   - Fixed `LazyInitializationException` on admin paged queries with `@Transactional(readOnly = true)`.

---

## 4. Frontend Architecture & Features Delivered

### Core Infrastructure
- **Axios HTTP Client** (`src/services/api.ts`): Configured with automatic JWT token attachment and 401 refresh token interceptor.
- **State Management**:
  - `authStore.ts` (Zustand): Persistent auth session, role checking (`isAdmin`), quick login and registration.
  - `cartStore.ts` (Zustand): Real-time cart state, badge counter, optimistic and server-synchronized totals.
- **API Services**: `authService.ts`, `productService.ts`, `orderService.ts`, `userService.ts`, `wishlistService.ts`, `adminService.ts`.

### Customer Storefront Pages
- **Homepage** (`/`): Hero showcase, category quick-jump grid, trending products, promotional banner, customer reviews.
- **Product Catalog** (`/products`): Full-text search, multi-category & brand filtering, price sliders, in-stock toggle, sorting (Price, Newest, Rating), and pagination.
- **Product Detail** (`/products/:id`): High-res gallery, stock indicator, quantity picker, add to cart / wishlist, specifications table, verified reviews list + review submission form.
- **Shopping Cart** (`/cart`): Item breakdown, quantity stepper, coupon validator with backend recalculation, order summary, one-click checkout.
- **Checkout** (`/checkout`): Multi-step checkout with delivery address selector, new address modal, Cash on Delivery vs Online Payment (Razorpay test mode), idempotency key support.
- **Order Confirmation** (`/order-confirmation/:id`): Visual receipt with order number, estimated delivery date, ordered items, and shipping address.
- **Order History & Detail** (`/orders`, `/orders/:id`): Visual fulfillment tracking timeline (`PENDING` -> `DELIVERED`), status history audit, cancel order action, return request modal.
- **Wishlist** (`/wishlist`): Saved products grid with "Move to Cart" and removal actions.
- **Customer Profile** (`/profile`): Profile editor, address book manager (add, delete, default), and password change form.
- **Authentication** (`/login`, `/register`): Customer registration, login, and **1-Click Demo Login buttons** for instant Admin and Customer access.

### Admin Dashboard Pages
- **Dashboard Overview** (`/admin`): Real-time metrics (Revenue, Orders Today, Customers, Low Stock Alerts), interactive Recharts revenue area chart, and order distribution breakdown.
- **Product Management** (`/admin/products`): Searchable catalog table, publish/hide toggle, edit modal, and "Add Product" form with initial inventory allocation.
- **Fulfillment Management** (`/admin/orders`): Orders table filtered by status, order detail modal, and state machine transition controller.
- **Inventory & Warehousing** (`/admin/inventory`): SKU stock monitor, reserved units tracker, low stock filter, and "Adjust Stock" replenishment modal with audit reason.
- **Customer Directory** (`/admin/customers`): Customer profiles with verification status and one-click account suspension/activation.
- **Coupons & Promotions** (`/admin/coupons`): Voucher creation modal (Percentage vs Flat discount, minimum spend, expiry date, usage limits) and deletion.
- **Taxonomy Management** (`/admin/categories`): Category and brand management with creation modals.
- **Returns & RMA** (`/admin/returns`): Reverse logistics tracking with approve and reject actions.

---

## 5. Verification & Testing Evidence

1. **Backend Tests**: `mvn test` executed with `BUILD SUCCESS` (100% unit & integration test pass).
2. **Frontend Build**: `npm run build` executed with `✓ built in 1.09s` and **0 TypeScript/Rollup errors**.
3. **Backend Service**: Running on `http://localhost:8080`, `/actuator/health` returns `{"status":"UP"}`.
4. **Frontend Service**: Running on `http://127.0.0.1:5173`, HTML and assets serve with HTTP 200 OK.
5. **Vite Reverse Proxy**: `http://127.0.0.1:5173/api/v1/products` verified returning live JSON products from MySQL backend.
6. **Postman Collection**: `ShopSphere_Postman_Collection.json` validated and ready for one-click import into Postman.

---

## 6. Fine-Tune Cycle (Nov 11, 2026)

### Backend consolidation
- **Single Admin API surface**: all duplicate admin controllers were removed; `AdminController` is now the one `/api/v1/admin/**` implementation covering dashboard/analytics, orders (state-machine transitions + shipment upsert), products (CRUD, soft delete, multipart image upload), categories/brands (CRUD with dependency-safe deletes), customers (no credentials ever exposed), inventory (audited adjustments with reserved-unit guard and low-stock alerts), coupons (full CRUD + validation), returns (approve/reject/complete with restock + refund ledger) and review moderation.
- **Returns → refunds wired end-to-end**: completing a return now restores stock (with `RETURN` audit rows), moves the order to `RETURNED`, marks the payment `REFUNDED`, and writes a `Refund` ledger row (test-mode money movement).
- **Order invoices**: `GET /api/v1/orders/{id}/invoice` serves a real OpenPDF invoice (owner-verified, unavailable for PENDING/CANCELLED orders).

### Frontend integration fixes
- **Razorpay flow completed end-to-end**: checkout now resolves the gateway order (`POST /payments/orders/{id}/create`), opens the Razorpay checkout.js modal, and verifies the HMAC handshake (`POST /payments/verify`) before confirming. Unconfigured environments surface a clear "use Cash on Delivery" message instead of silently doing nothing.
- **Resume payment**: PENDING Razorpay orders show a "Complete Payment" banner on the order detail page so a dismissed payment window is recoverable.
- New service module: `paymentService.ts`.

### Verified this cycle
- `mvn compile` → EXIT=0; `mvn test` → `BUILD SUCCESS` (all tests pass, 0 failures).
- `npm run build` → 0 TypeScript errors (all chunks compiled).

---

## 7. Deployment Readiness + Password Fix (Nov 11, 2026)

### Free deployment package
- **`docker-compose.prod.yml`** — production stack for a single always-free VM: MySQL + backend + frontend stay on the private compose network (no published ports), Caddy is the only public entry point, all services `restart: unless-stopped`.
- **`Caddyfile`** — automatic Let's Encrypt HTTPS for `PUBLIC_DOMAIN` (e.g. a free DuckDNS subdomain).
- **`frontend/nginx.conf`** — now also proxies `/swagger-ui*`, `/v3/api-docs` and `/actuator/health` so API docs and health checks work behind the reverse proxy.
- **`.env.example`** — extended with the production variables (`PUBLIC_DOMAIN`, `CORS_ALLOWED_ORIGINS`, `JWT_COOKIE_SECURE`).
- **`DEPLOYMENT.md`** — full guide: Oracle Cloud Always Free (recommended, verified 2026 free-tier facts), Render + Vercel alternative, firewall/DNS/Docker/deploy-key steps, backups, troubleshooting, cost summary.

### Bug fix: password change did not persist
- `POST /api/v1/users/me/password` returned `204` but the new hash was never written: the controller mutated a detached entity with no `save()`, and `ProfileService.changePassword` was an empty stub.
- Logic moved to a transactional `ProfileService.changePassword` (BCrypt verify → encode → dirty-check persist); controller now delegates.
- Two new integration tests: new password works / old rejected, wrong current password → `400`.

### Verified this cycle
- `mvn test` → **11 tests, 0 failures** (AuthRbacIntegrationTest 7, CheckoutFlowIntegrationTest 3, context 1).
- CI on GitHub Actions: backend suite against MySQL 8 + frontend build — green on every push.

---

## 8. YouPI PG Self-Hosted UPI Integration (Nov 13, 2026)

### What shipped
- **Flyway `V5__youpi_upi_payments.sql`**: adds `payments.provider`, `payments.checkout_url`, `payments.utr`, an index on provider, and the append-only `youpi_events` audit/dedupe table (unique `delivery_id`).
- **New server flow**:
  - `POST /api/v1/payments/orders/{orderId}/youpi-session` creates or resumes a hosted UPI session server-side (merchant key never reaches the browser) and returns `checkoutUrl`.
  - `POST /api/v1/payments/webhook/youpi` verifies HMAC-SHA256 over the raw request body, deduplicates `X-Gateway-Delivery-Id`, cross-checks order/amount/currency/payment id, and settles only on `event=RECONCILED` + `method=UPI` + `settlementState=RECONCILED`.
  - Non-authoritative events (`OBSERVED_SUCCESS`, `SUCCESS`, `MANUAL_REVIEW`, `FAILED`, `EXPIRED`, `CANCELLED`) are recorded with a reason but never change order state. Failed/expired UPI attempts do not cancel the order or release stock.
- **Order/payment integrity**: `PaymentMethod` now includes `UPI`; `PaymentProvider` distinguishes Razorpay from YouPI; Razorpay verify/webhook handlers reject YouPI rows and vice versa. YouPI UTR is stored only after reconciliation, never in the Razorpay payment-id column.
- **Frontend**: checkout has a third payment option (`UPI (YouPI)`) that creates the session after order creation and redirects to the hosted checkout. Order Detail resumes a PENDING UPI order and shows a `?payment=youpi` return banner that explicitly says the redirect is not proof of payment.
- **Config/deploy docs**: `application.yml`, `.env.example`, both compose files (including `host.docker.internal:host-gateway`), README and `DEPLOYMENT.md` document `YOUPI_*` settings. Existing Razorpay and COD flows remain unchanged.

### Verification
- `mvn test` → **20 tests, 0 failures** (AuthRbac 7, CheckoutFlow 3, context 1, **YoupiWebhook 9**).
- YouPI tests cover: reconciled settlement + UTR, observer alert non-fulfillment, ignored `SUCCESS`/COD/non-reconciled variants, duplicate delivery id, invalid signature, amount mismatch, delivery-id reuse, a late reconciled settlement for an earlier resumed attempt, and a second settlement on an already-paid order flagged for manual refund review.
- `npm run build` → **0 TypeScript errors**.
- Flyway applied V5 successfully on the local MySQL instance during the test run.

### Honest limitations / not verified
- **No live YouPI instance was run for this change.** No merchant API key, webhook secret, receiving VPA, bank account, SMS observer or Gmail observer was configured, so a real UPI payment was **not** executed end-to-end.
- YouPI PG is explicitly **experimental research software**; it is not NPCI/RBI-certified, has no bank API, and requires an admin to import a bank-statement CSV before a session becomes `RECONCILED`. It must not be presented as a production payment aggregator.
- YouPI gates new sessions on a fresh SMS-observer heartbeat outside its test mode. Without a paired Android observer (or documented test-mode run), session creation will fail even when the backend is configured.
- YouPI refunds are manual bank transfers recorded in ShopSphere's admin refund ledger; there is no gateway refund API.
- Docker is still not installed on this machine, so the updated compose files are documented but **not executed**.
