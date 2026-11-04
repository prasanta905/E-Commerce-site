# ShopSphere — DEVELOPMENT_STATUS.md

_Living document. Updated at the end of every phase._

## Current Snapshot

| Field | Value |
|---|---|
| Phase | **1 — Complete** |
| Blocks Phase 2? | **YES — WAITING ON**: nothing (tooling installs running in background) |
| Monorepo root | `ShopSphere/` |

---

## Phase 1 — Requirements, Architecture, Database Design ✅ COMPLETE

### Completed
1. Toolchain audit: Java 21 ✔, network ✔, winget ✔
2. Background install launched for: Git, Apache Maven, Node.js LTS, MySQL 8.4 — **pending**
3. Architecture Decision Record (12 decisions: A1–A12 in `docs/BLUEPRINT.md`)
4. Module breakdown, ~18 backend domain modules
5. Order **state machine** defined and enforced server-side
6. Database design: 30 tables, 3NF, reservation-based inventory
7. API surface defined: ~45 endpoints under `/api/v1` in `docs/API_ENDPOINTS.md`
8. Roadmap mapped to 10 phases

### Key engineering assumptions
| ID | Decision |
|---|---|
| A1 | Modular monolith — Spring Boot 3.4.x + Java 21, no microservices |
| A2 | JWT access (15 min, in-memory) + refresh (7d) in HttpOnly cookie, rotating with reuse detection |
| A3 | Inventory reservation model — pessimistic `SELECT FOR UPDATE` at checkout, auto-release |
| A4 | Server recomputes every price; client sends only IDs+quantities |
| A5 | Razorpay test mode; HMAC-SHA256 verified on callback + webhook; idempotent payment creation |
| A6 | Flyway migrations as single schema source of truth; Hibernate=validate only |
| A7 | Soft delete only for products/categories/brands/coupons |
| A8 | All money is DECIMAL(10,2), currency = INR constant |
| A9 | SMTP optional at dev time — SMTP fallback uses logging to console (never fakes success) |
| A10 | Product images generated locally (no hotlinked/broken external URLs) |
| A11 | Charts library = Recharts |
| A12 | DB = MySQL 8.4 @3306/schema `shopsphere`; Docker config delivered but Docker absent locally |

### Known Blockers / Pending
- ❓ winget installs of Maven / Node / Git / MySQL — running in background, will confirm version matrix once done
- ⚠ Docker **not installed** — Dockerfiles + compose file will be produced and validated by structural review only in Phase 10; that limitation will be stated honestly, not hidden.

### Files created — Phase 1
| Path | Purpose |
|---|---|
| `docs/BLUEPRINT.md` | Architecture, module map, roadmap |
| `docs/DATABASE_DESIGN.md` | 30-table ER + DDL + relationship notes |
| `docs/API_ENDPOINTS.md` | Full REST contract |

---

## Phase 2 — NOT STARTED
## Phase 3 — NOT STARTED
## Phase 4 — NOT STARTED
## Phase 5 — NOT STARTED
## Phase 6 — NOT STARTED
## Phase 7 — NOT STARTED
## Phase 8 — NOT STARTED
## Phase 9 — NOT STARTED
## Phase 10 — NOT STARTED

---

## API Implementation Ledger
_Populated continuously from Phase 4 onward — no endpoint listed here until it compiles AND has a passing test._

| Endpoint | DTO in | DTO out | Tests | Status |
|---|---|---|---|---|
| _(empty)_ | | | | |
