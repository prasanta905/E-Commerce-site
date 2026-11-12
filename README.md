# ShopSphere — Full-Stack E-Commerce Platform

A production-style e-commerce platform with a **Spring Boot 3.5 (Java 21) backend**, a **React + TypeScript + Vite frontend**, and **MySQL 8**. Includes a full customer storefront and an admin dashboard, JWT auth with refresh-token rotation, Razorpay test-mode payments, an optional **self-hosted YouPI PG UPI** flow, real invoices (PDF), and an order state machine enforced server-side.

> Final verification state (Nov 13, 2026): backend compiles and all **20 tests** pass (`mvn test`), including **9 YouPI webhook integration tests** covering settlement gating, replay dedupe, bad signatures, amount mismatches, late settlement of an earlier attempt and duplicate-settlement refund flagging. Frontend builds with zero TypeScript errors (`npm run build`). The earlier Razorpay/COD lifecycle smoke test remains valid; the YouPI contract is proven by tests because no live YouPI gateway is configured on this machine. See `DEVELOPMENT_STATUS.md` for details and limitations.

---

## 1. Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21 LTS, Spring Boot 3.5.16, Spring Security (JWT HS256), Spring Data JPA (Hibernate), Flyway, springdoc-openapi (Swagger) |
| Payments | Razorpay Java SDK (test mode), HMAC signature verification, idempotent webhooks; optional self-hosted **YouPI PG** UPI with signed `RECONCILED` webhook settlement |
| PDF | OpenPDF (invoices) |
| Database | MySQL 8.x (31 tables, `shopsphere`), Flyway migrations as the single schema truth |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, Zustand, TanStack Query, Recharts, Axios |
| Testing | JUnit 5 + TestRestTemplate (integration over real HTTP + MySQL), Vitest + RTL available for the frontend |

## 2. Project layout

```
ShopSphere/
├── backend/                  Spring Boot API (port 8080)
│   ├── src/main/java/com/shopsphere/…   modular monolith (auth, product, cart,
│   │        checkout, order, payment, admin, review, coupon, inventory, …)
│   ├── src/main/resources/db/migration/ Flyway V1–V5 (schema + seed + YouPI)
│   ├── uploads/products/     36 seeded product images (served at /uploads/**)
│   └── src/test/             20 integration tests over real HTTP + MySQL
├── frontend/                 React SPA (port 5173, proxies /api → 8080)
├── docs/                     BLUEPRINT.md, API_ENDPOINTS.md, DATABASE_DESIGN.md
├── docker-compose.yml        MySQL + backend + nginx frontend
├── .env.example              template for all secrets
└── DEVELOPMENT_STATUS.md     delivery report with verification evidence
```

## 3. Prerequisites

- **Java 21** (`java -version`)
- **Maven 3.9+** (or use the wrapper if you add one)
- **MySQL 8.x** running locally
- **Node 18+ / npm 9+**

## 4. Local setup

### 4.1 Database

Create two schemas (one for the app, one reserved for testing):

```sql
CREATE DATABASE shopsphere CHARACTER SET utf8mb4;
CREATE USER 'shopsphere_user'@'localhost' IDENTIFIED BY 'SsApp!2026';
GRANT ALL PRIVILEGES ON shopsphere.* TO 'shopsphere_user'@'localhost';
FLUSH PRIVILEGES;
```

Flyway applies all migrations automatically on first boot (V1 schema → V5 YouPI UPI).

### 4.2 Backend configuration

Credentials are **never committed**. Copy the pattern of `backend/src/main/resources/application-local.properties` (this file is gitignored on this machine) and set:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/shopsphere?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=shopsphere_user
spring.datasource.password=YOUR_PASSWORD
JWT_SECRET_KEY=<openssl rand -base64 64>
JWT_COOKIE_SECURE=false
CORS_ALLOWED_ORIGINS=http://localhost:5173
RAZORPAY_ENABLED=false          # set true + test keys to enable online payment
RAZORPAY_KEY_ID=
RAZORPAY_KEY_SECRET=
RAZORPAY_WEBHOOK_SECRET=
YOUPI_ENABLED=false             # set true after the self-hosted gateway is configured
YOUPI_BASE_URL=http://localhost:6100
YOUPI_MERCHANT_ID=
YOUPI_MERCHANT_KEY=
YOUPI_WEBHOOK_SECRET=
YOUPI_RETURN_BASE_URL=http://localhost:5173
UPLOAD_DIR=uploads
MAIL_CONSOLE_FALLBACK=true      # emails print to the console when no SMTP is set
```

`application.yml` reads every secret through environment placeholders — only the `local` profile file (gitignored) or real environment variables carry values.

### 4.3 Run the backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

### 4.4 Run the frontend

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173  (proxies /api and /uploads to :8080)
```

### 4.5 Self-hosted UPI with YouPI PG (optional)

UPI orders can be routed to a separately hosted **YouPI PG** instance. Razorpay and COD remain available.

> **YouPI PG is experimental research software, not an NPCI/RBI-certified payment aggregator.** It has no bank API. A payment becomes `RECONCILED` only after an admin imports a bank-statement CSV and one line matches the receiving account, amount in paise and 12-digit UTR. SMS/Gmail observers are alerting inputs, never settlement proof. Read the upstream `youpi-pg` README and warnings before using real money.

Flow:

1. Checkout creates a `PENDING` UPI order; no gateway call runs inside the order transaction.
2. `POST /api/v1/payments/orders/{id}/youpi-session` calls YouPI **server-side** and returns the hosted `checkoutUrl`.
3. The browser redirects; the order stays `PENDING`.
4. YouPI posts signed webhooks to `POST /api/v1/payments/webhook/youpi`.
5. **Only** `event=RECONCILED` + `method=UPI` + `settlementState=RECONCILED` confirms the order. `OBSERVED_SUCCESS`, `SUCCESS` (COD/counter), `MANUAL_REVIEW`, `FAILED`, `EXPIRED` and `CANCELLED` never do.
6. A **second** reconciled settlement for an already-paid order is recorded as `REJECTED` with a “manual refund review” reason; the first UTR and order state are kept unchanged.

Start the gateway (from the sibling `youpi-pg` checkout):

```bash
cp .env.example .env
# set YOUPI_ALLOWED_RETURN_ORIGINS=http://localhost:5173
# set YOUPI_ALLOWED_ORIGINS=http://localhost:5173
npm install
npm start
```

Then configure a receiving VPA + webhook with YouPI's admin API (`Authorization: Bearer $ADMIN_API_TOKEN`):

```bash
POST /api/v1/admin/upi-settings
{"upiId":"your-name@bank","payeeName":"ShopSphere"}

POST /api/v1/admin/merchant-settings
{"webhookUrl":"http://localhost:8080/api/v1/payments/webhook/youpi",
 "webhookSecret":"<random-secret>"}

POST /api/v1/admin/merchant-key/rotate
# copy the returned merchantKey once — it is never shown again
```

Put `YOUPI_MERCHANT_ID`, `YOUPI_MERCHANT_KEY` and `YOUPI_WEBHOOK_SECRET` in ShopSphere's environment, set `YOUPI_ENABLED=true`, and restart the backend. The merchant key stays server-side; only `checkoutUrl` reaches the browser. YouPI gates new sessions on a fresh SMS-observer heartbeat outside its test mode, so pair an Android observer (or run its documented test mode) before expecting session creation to succeed.

## 5. Testing

Backend (real HTTP against the local DB — creates `it-*` test users/orders, safe to re-run):

```bash
cd backend
mvn test
# Tests run: 20, Failures: 0, Errors: 0 — BUILD SUCCESS
```

Covered: context boot + Flyway, register/duplicate-register/wrong-password, anonymous 401, customer→admin 403, admin access 200, cross-user order hidden, full COD purchase with server-side pricing + idempotent replay, unknown coupon clean 4xx, terms rejection, plus 9 YouPI webhook tests: reconciled settlement, observer-only non-fulfillment, ignored `SUCCESS`/`COD`/non-reconciled variants, replay dedupe, bad signature, amount mismatch, delivery-id reuse, a late reconciled settlement for an earlier attempt, and duplicate settlement flagged for manual refund review.

Frontend type/build check:

```bash
cd frontend
npm run build       # 0 TypeScript errors
```

## 6. Docker deployment

> **Known limitation:** Docker is not installed on the development machine used to build this project, so the images/compose stack have **not been executed**. The files follow the standard patterns and are provided ready to verify.

```bash
# 1. Configure secrets
cp .env.example .env
#    edit .env — generate JWT_SECRET_KEY with: openssl rand -base64 64

# 2. Build + start everything (MySQL, backend, frontend)
docker compose --env-file .env up --build

#    frontend  http://localhost:5173  (nginx serves the SPA, proxies /api)
#    backend   http://localhost:8080
#    db        mysql:8.4 with a persistent named volume (db-data)
```

YouPI is **not** part of this compose file. Run it separately and point `YOUPI_BASE_URL` at it (`http://host.docker.internal:6100` reaches a host process because both compose files add the `host-gateway` mapping).

Files: `backend/Dockerfile` (multi-stage Maven → JRE 21 alpine, non-root, healthcheck), `frontend/Dockerfile` + `nginx.conf` (SPA fallback + reverse proxy), `backend/src/main/resources/application-docker.properties`, `docker-compose.yml`.

**Production (free VM):** use `docker-compose.prod.yml` — only Caddy exposes ports 80/443, with automatic HTTPS. Full step-by-step: `DEPLOYMENT.md`.

## 7. Production notes

- Set `JWT_COOKIE_SECURE=true` and real CORS origins behind HTTPS.
- Razorpay webhooks need a publicly reachable `POST /api/v1/payments/webhook` and `RAZORPAY_WEBHOOK_SECRET` set.
- YouPI webhooks need a publicly reachable `POST /api/v1/payments/webhook/youpi`, a matching `YOUPI_WEBHOOK_SECRET`, and `YOUPI_RETURN_BASE_URL` set to the exact storefront origin allowlisted in YouPI (`YOUPI_ALLOWED_RETURN_ORIGINS`). Keep YouPI's merchant API on a private network; only the checkout portal and the webhook receiver need exposure. YouPI UPI refunds are manual bank transfers recorded in ShopSphere's admin refund ledger — the gateway has no refund API.
- Schema changes only via new Flyway migrations (`ddl-auto: validate` is on).
- All admin endpoints require `ROLE_ADMIN`; passwords are BCrypt-hashed; DTOs never leak entities; customer passwords are never returned by any admin endpoint.

## 8. Documentation index

- `DEPLOYMENT.md` — free live deployment guide (Oracle Always Free recommended, Render alternative)
- `DEVELOPMENT_STATUS.md` — delivery report, verification evidence, known limitations
- `docs/API_ENDPOINTS.md` — full `/api/v1` contract
- `docs/DATABASE_DESIGN.md` — ER design and table relationships
- `docs/BLUEPRINT.md` — original architecture blueprint
- `ShopSphere_Postman_Collection.json` — importable API collection
