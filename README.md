# ShopSphere — Full-Stack E-Commerce Platform

A production-style e-commerce platform with a **Spring Boot 3.5 (Java 21) backend**, a **React + TypeScript + Vite frontend**, and **MySQL 8**. Includes a full customer storefront and an admin dashboard, JWT auth with refresh-token rotation, Razorpay test-mode payments, real invoices (PDF), and an order state machine enforced server-side.

> Final verification state (Nov 11, 2026): backend compiles and all 9 tests pass (`mvn test` → `BUILD SUCCESS`), frontend builds with zero TypeScript errors (`npm run build`), and the full purchase lifecycle (cart → preview → COD checkout → idempotent replay → admin fulfillment → return/refund → invoice PDF) was smoke-tested against the live stack. See `DEVELOPMENT_STATUS.md` for details.

---

## 1. Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21 LTS, Spring Boot 3.5.16, Spring Security (JWT HS256), Spring Data JPA (Hibernate), Flyway, springdoc-openapi (Swagger) |
| Payments | Razorpay Java SDK (test mode), HMAC signature verification, idempotent webhooks |
| PDF | OpenPDF (invoices) |
| Database | MySQL 8.x (30 tables, `shopsphere`), Flyway migrations as the single schema truth |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, Zustand, TanStack Query, Recharts, Axios |
| Testing | JUnit 5 + TestRestTemplate (integration over real HTTP + MySQL), Vitest + RTL available for the frontend |

## 2. Project layout

```
ShopSphere/
├── backend/                  Spring Boot API (port 8080)
│   ├── src/main/java/com/shopsphere/…   modular monolith (auth, product, cart,
│   │        checkout, order, payment, admin, review, coupon, inventory, …)
│   ├── src/main/resources/db/migration/ Flyway V1–V4 (schema + seed)
│   ├── uploads/products/     36 seeded product images (served at /uploads/**)
│   └── src/test/             9 integration tests over real HTTP + MySQL
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

Flyway applies all migrations automatically on first boot (V1 schema → V4 data).

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

### 4.5 Demo accounts (seeded)

| Role | Email | Password |
|---|---|---|
| Admin | `admin@shopsphere.local` | `Admin@123` |
| Customer | `customer1@shopsphere.local` | `User@123` |
| Customer | `customer2@shopsphere.local` | `User@123` |

## 5. Testing

Backend (real HTTP against the local DB — creates `it-*` test users/orders, safe to re-run):

```bash
cd backend
mvn test
# Tests run: 9, Failures: 0, Errors: 0 — BUILD SUCCESS
```

Covered: context boot + Flyway, register/duplicate-register/wrong-password, anonymous 401, customer→admin 403, admin access 200, cross-user order hidden, full COD purchase with server-side pricing + idempotent replay, unknown coupon clean 4xx, terms rejection.

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

Files: `backend/Dockerfile` (multi-stage Maven → JRE 21 alpine, non-root, healthcheck), `frontend/Dockerfile` + `nginx.conf` (SPA fallback + reverse proxy), `backend/src/main/resources/application-docker.properties`, `docker-compose.yml`.

## 7. Production notes

- Set `JWT_COOKIE_SECURE=true` and real CORS origins behind HTTPS.
- Razorpay webhooks need a publicly reachable `POST /api/v1/payments/webhook` and `RAZORPAY_WEBHOOK_SECRET` set.
- Schema changes only via new Flyway migrations (`ddl-auto: validate` is on).
- All admin endpoints require `ROLE_ADMIN`; passwords are BCrypt-hashed; DTOs never leak entities; customer passwords are never returned by any admin endpoint.

## 8. Documentation index

- `DEVELOPMENT_STATUS.md` — delivery report, verification evidence, known limitations
- `docs/API_ENDPOINTS.md` — full `/api/v1` contract
- `docs/DATABASE_DESIGN.md` — ER design and table relationships
- `docs/BLUEPRINT.md` — original architecture blueprint
- `ShopSphere_Postman_Collection.json` — importable API collection
