-- =====================================================================
-- ShopSphere — V2: Performance indexes for the hot catalog/order paths.
-- =====================================================================

-- Product listing: active catalog + category filter scans
CREATE INDEX ix_products_active ON products (is_active, is_deleted, category_id);
CREATE INDEX ix_products_price   ON products (selling_price);
CREATE INDEX ix_products_rating  ON products (rating_avg DESC);
CREATE INDEX ix_products_featured ON products (is_featured, is_active, is_deleted);

-- Coupon validation queries filter on (code, active, expiry)
CREATE INDEX ix_coupons_expiry ON coupons (expires_at);

-- Payment reconciliation by gateway order id (dedupe / webhook idempotency)
CREATE INDEX ix_ptx_gw_order ON payment_transactions (razorpay_order_id);

-- FK heat points
CREATE INDEX ix_orders_coupon ON orders (coupon_id);
CREATE INDEX ix_cr_order      ON coupon_redemptions (order_id);
CREATE INDEX ix_ci_product    ON cart_items (product_id);
CREATE INDEX ix_wi_product    ON wishlist_items (product_id);
CREATE INDEX ix_oi_product    ON order_items (product_id);
CREATE INDEX ix_invtxn_type   ON inventory_transactions (transaction_type);
