# ShopSphere — Database Design (MySQL 8.4)

Only 30 tables were declared in the original Phase 3 contract, plus `youpi_events` added by Flyway `V5` for the optional self-hosted UPI flow. The migrations `V1`–`V5` are the executed source of truth.

## 1. ER Summary (textual)

```
users 1 ──── * addresses
users * ──── * roles            (via user_roles)
users 1 ──── * refresh_tokens / password_reset_tokens / email_verification_tokens
users 1 ──── 1 carts / wishlists
categories (self-ref parent_category_id)
brands 1 ─── * products
categories 1 ── * products
categories 1 ─── * categories          (hierarchical)
products 1 ── * product_images / product_specifications / reviews
inventory 1 ─── 1 products             (1:1 by product_id)
inventory 1 ── * inventory_transactions
carts 1 ─── * cart_items 1 ─── 1 products
wishlists 1 ── * wishlist_items 1 ── 1 products
users 1 ─── * orders 1 ── * order_items 1 ── 1 products
orders 1 ─── * order_status_history
orders 1 ─── 1 payments 1 ── * payment_transactions
payments 1 ── * youpi_events            (optional self-hosted UPI audit)
orders 1 ─── 1 shipments
coupons 1 ─── * coupon_usages
orders 1 ─── * return_requests; payments 1 ─── * refunds
users 1 ─── * notifications
```

## 2. Table Catalogue (31 tables)

| Table | Purpose | Key columns |
|---|---|---|
| users | accounts + authentication | id PK, email UNIQUE, password_hash, full_name, phone, is_active, is_deleted |
| roles | authority registry | id PK, name UNIQUE (ROLE_USER/ROLE_ADMIN) |
| user_roles | join | (user_id, role_id) UNIQUE |
| addresses | shipping destinations | id PK, user_id FK, type (SHIPPING/DEFAULT), line1, city, state, pincode, is_default |
| categories | hierarchical taxonomy | id PK, parent_category_id FK self, name, slug UNIQUE, is_active |
| brands | manufacturer registry | id PK, name UNIQUE, logo_url, is_active |
| products | sellable items | id PK, category_id FK, brand_id FK, name, slug UNIQUE, description, mrp DECIMAL(10,2), selling_price DECIMAL(10,2), is_active, is_deleted |
| product_images | gallery | id PK, product_id FK, url, is_primary, sort_order |
| product_specifications | key/value specs | id PK, product_id FK, spec_key, spec_value |
| inventory | per-product stock | id PK, product_id FK UNIQUE, total_quantity, reserved_quantity, low_stock_threshold, CHECK(total>=0 AND reserved>=0 AND reserved<=total) |
| inventory_transactions | immutable stock audit | id PK, inventory_id FK, type (PURCHASE/ADJUST/RESERVE/RELEASE/COMMIT/RETURN/BREAKAGE), quantity_delta, reason, order_idnullable, user_idnullable, created_at |
| carts | per-user basket | id PK, user_id FK UNIQUE, is_checked_out |
| cart_items | basket line items | id PK, cart_id FK, product_id FK, quantity, UNIQUE(cart_id, product_id) |
| wishlists | per-user wishlist | id PK, user_id FK UNIQUE |
| wishlist_items | wishlist lines | id PK, wishlist_id FK, product_id FK, UNIQUE(wishlist_id, product_id) |
| orders | orders master | id PK, user_id FK, address_snapshot JSON/TEXT, subtotal, discount, shipping, tax(?), total DECIMAL(10,2), status ENUM, payment_mode, idempotency_key UNIQUE |
| order_items | order lines (immutable price snapshot) | id PK, order_id FK, product_id FK, product_name, unit_price DECIMAL, quantity, line_total |
| order_status_history | state audit | id PK, order_id FK, from_status, to_status, changed_by, note, created_at |
| payments | payment aggregate per order | id PK, order_id FK UNIQUE, method (COD/RAZORPAY/UPI), provider (RAZORPAY/YOUPI), gateway_order_id UNIQUE, checkout_url, utr, status, amount_paid |
| youpi_events | YouPI session/webhook audit + delivery dedupe | id PK, delivery_id UNIQUE nullable, payment_id FK nullable, event/event_type, method, settlement_state, result/reason, order_reference, provider_payment_id, amount, utr, payload TEXT, received_at |
| payment_transactions | each gateway attempt/webhook event | id PK, payment_id FK, razorpay_order_id, razorpay_payment_id UNIQUE nullable, razorpay_signature, status, payload_sha256, created_at |
| shipments | fulfilment record | id PK, order_id FK UNIQUE, courier, tracking_number, shipped_at, delivered_at |
| coupons | discounts | id PK, code UNIQUE, type (PERCENT/FIXED), value DECIMAL, min_order_amt, max_discount, start_at, expires_at, max_global_uses, max_uses_per_user, is_active |
| coupon_usages | redemption ledger | id PK, coupon_id FK, user_id FK, order_id FK, UNIQUE(coupon_id, order_id), UNIQUE(coupon_id, user_id, order_id) — plus per-user count queries |
| reviews | customer feedback | id PK, product_id FK, user_id FK, order_id FK nullable (for verified-purchase proof), rating TINYINT CHECK 1..5, title, body, is_verified, UNIQUE(product_id, user_id) |
| notifications | messages to user | id PK, user_id FK, type, title, body, read_at null |
| refresh_tokens | rotating tokens w/ reuse detection | id PK, user_id FK, jti UNIQUE, family_id, expires_at, revoked_at null, replaced_by null |
| password_reset_tokens | forgot-password flow | id PK, user_id FK, token_hash UNIQUE, expires_at, used_at null |
| email_verification_tokens | verify email | id PK, user_id FK, token_hash UNIQUE, expires_at, verified_at null |
| return_requests | post-delivery returns | id PK, order_id FK, user_id FK, reason, status (REQUESTED/APPROVED/REJECTED/PICKED/RETURNED), created_at |
| refunds | money-back records | id PK, return_id FK_nullable, payment_id FK, amount, gateway_refund_id, status, created_at |

## 3. Design Rules

1. 3NF; no derived columns except **immutable price snapshots** in order_items (justified: product price may change after order).
2. All money DECIMAL(10,2) INR — never float/int.
3. `created_at`/`updated_at` on every table.
4. Concurrency: pessimistic `SELECT … FOR UPDATE` inside transact­ions for inventory order/commit and coupon max-uses-per-user enforcement.
5. Idempotency: orders.idempotency_key unique; payments.gateway_order_id unique; youpi_events.delivery_id unique (webhook replay guard).
6. Soft delete only on products/products-associated tables, categories, brands, coupons — `deleted_at` column.
7. Full DDL shipped in Phase 3 as `V1__schema.sql` + `V2__indexes.sql` + `V3__seed.sql` under `backend/src/main/resources/db/migration/`.
