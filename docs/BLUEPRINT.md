# ShopSphere — Technical Blueprint

## 1. System Overview
Modular monolith. Single Spring Boot deployable + single React SPA sharing one MySQL 8.4 database.

```
[React 18 + TypeScript + Vite]              [Spring Boot 3.4 backend]
        React Router                              ├─ device-grade REST /api/v1/
        TanStack Query  ──HTTPS/Axios──▶  ├─ Spring Security 6 (JWT + roles)
        Tailwind CSS                            ├─ Spring Data JPA / Hibernate
        Zustand (cart UI state)                   ├─ Spring Mail / Flyway / OpenAPI
        Lucide                                     └─ Razorpay SDK (test mode)
```

Rules enforced by architecture:
- Entities are NEVER serialized to the client; modular DTO mappers per feature.
- Controllers stay thin; all business rules in `@Service` + repo layer.
- Checkout, order state transition, coupon redemption, and payment verification are all transactional and concurrency-safe.

## 2. Backend Package Structure
```
backend/src/main/java/com/shopsphere/
├── ShopSphereApplication.java
├── config/            SecurityConfig, OpenAPIConfig, CacheConfig, AsyncConfig, WebConfig
├── security/          JwtService, JwtAuthFilter, RefreshTokenService, CurrentUser
├── common/            ApiResponse, PageResponse, ApiError, GlobalExceptionHandler, util
├── auth/              AuthController, AuthService, DTOs, entities (User, RefreshToken…)
├── user/              User profile, addresses
├── product/           products, specs, images, ratings
├── category/
├── brand/          ├─ categories, brands
├── inventory/         stock, reservation, low-stock alerts
├── cart/
├── wishlist/
├── order/             orders, items, status history, returns, invoices
├── payment/           Razorpay orders/verify/webhook, refunds
├── shipping/
├── coupon/
├── review/
├── notification/      email + in-app
├── admin/             admin controllers, dashboard aggregation
└── analytics/
```

## 3. Frontend Structure
```
frontend/src/
├── components/        ui/ (Button, Input, Card, Modal, Badge, Pagination, Skeleton …)
├── layouts/           StoreLayout, AdminLayout, AuthLayout
├── pages/             customer/…, admin/…
├── routes/            ProtectedRoute, AdminRoute, router config
├── services/          apiClient, authService, productService, cartService, …
├── hooks/             useAuth, useCart, useWishlist, useDebounce, usePagination
├── store/             Zustand stores (authStore, cartStore, uiStore)
├── types/             domain model types shared across app
├── utils/  validations/  features/ (admin, products, checkout, …)
└── App.tsx
```

## 4. Security Model
| Concern | Implementation |
|---|---|
| Passwords | BCrypt (strength 12) |
| Auth | JWT HS256 access 15 min, refresh 7 days rotating, reuse detection |
| Storage | Access token in memory (React context), refresh in HttpOnly cookie |
| Roles | ROLE_USER, ROLE_ADMIN implemented via `@PreAuthorize` |
| Rate limiting | Spring Security filter on /auth/login, /auth/register, /payments (bucket4j-free hand-rolled token bucket) |
| CORS | Explicit allowed origins only; no wildcard with credentials |
| CSRF | Disabled for pure bearer endpoints; protected via SameSite=Strict cookie |
| Uploads | Extension+MIME allow-list, randomized filenames, size limit, no execution |
| Resource ownership | Every endpoint that reads a user's own data validates `userId == principal.id` OR admin |

## 5. Order State Machine (single source of truth)
```
INIT: PENDING (payment pending or COD placed)
PENDING → CONFIRMED  (on COD, always; on prepaid, on payment verified)
CONFIRMED → PROCESSING → SHIPPED → OUT_FOR_DELIVERY → DELIVERED
PENDING/CONFIRMED → CANCELLED (customer or admin)
DELIVERED → RETURN_REQUESTED → RETURNED (admin approves)
Forbidden transition attempt = 409 ApiError, logged in order_status_history.
```

## 6. Inventory Model
- `products` has `inventory` row 1:1
- `available_quantity = total_quantity - reserved_quantity`
- `create order` (checkout): atomically `UPDATE inventory SET reserved = reserved + ? WHERE id=? AND available >= ?` with brute `SELECT ... FOR UPDATE`
- Prepaid: hold reservation until payment verified (max session = 5 min) → auto-release on payment fail/timeout
- On order confirm: `total -= qty; reserved -= qty; transaction type = COMMIT`
- COD: reservation → immediate deduction on CONFIRMED
- Every publishable change logged into `inventory_transactions` (immutable audit)

## 7. Testing Strategy
- Backend: JUnit 5 + Mockito (unit) + `@DataJpa_Test`/Testcontainers-free — MySQL-based integration tests via Flyway on H2-compat mode? **NO** → since MySQL isn't guaranteed at CI, unit + slice tests only in `mvn test` default profile; full MySQL integration tests are activated with `-Dprofiles=integration` and run manually.
- Frontend: Vitest + React Testing Library for key flows (auth, cart, product grid)
- E2E: Playwright — customer journey (register→browse→cart→checkout→COD→orders) and admin journey (login→add product→update stock)

## 8. Performance
- Frontend: React.lazy per route, TanStack Query cache + prefetch, image lazy load, Next.js-free.
- Backend: JPA Projections for list views (Cart I/O fields only), `JOIN FETCH` only on detail views to avoid N+1, HikariCP tuned, Flyway + indexes, connection pool sizing formula documented, Tomcat max-threads=200
- Database indexes on: all FKs, order statuses, email lookups, product name (idx), coupon code (unique)

## 9. DevOps
- Docker: `backend/Dockerfile` (eclipse-temurin-21 JDK21-alpine 2-stage), `frontend/Dockerfile` (NGINX)
- `docker-compose.yml`: mysql 8.4, backend, frontend, standard Hv + health checks, dependency ordering `depends_on: db:condition: service_healthy`, adminer optional service
- Env vars: `.env.example` (no real secrets, placeholders only)
- GitHub Actions: `ci.yml` — mvn test + npm build on push
- ⚠️ Deployment (cloud | domain | HTTPS) requires external ingress setup — not physically executable inside this local sandbox; config is provided and validated by compose config lint command only, stated honestly in docs.

## 10. Roadmap & Phase Gates
Phase 1 ✅ blueprints → Phase 2 scaffolding → Phase 3 DB+entities → Phase 4 backend modules+tests → Phase 5 frontend pages → Phase 6 integration → Phase 7 payments/emails/reviews → Phase 8 tests → Phase 9 optimization → Phase 10 docker/deploy docs.

Each phase gate = compile + smoke-test + artefact check performed via commands in this workspace. No phase declared done without execution logs.
