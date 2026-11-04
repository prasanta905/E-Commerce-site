-- =====================================================================
-- ShopSphere — V1: Full schema. MySQL 8.x. Engine=InnoDB, utf8mb4.
-- Flyway is the single source of truth. Hibernate only validates.
-- Statuses stored as VARCHAR with application-level validation and
-- FK-protected relationships; enums documented in comments.
-- =====================================================================

-- ---------------------------------------------------------------- 1. Identity
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    phone         VARCHAR(20)  NULL,
    is_active     TINYINT(1)   NOT NULL DEFAULT 1,
    is_email_verified TINYINT(1) NOT NULL DEFAULT 0,
    is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_users_email ON users (email);

CREATE TABLE roles (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(40) NOT NULL
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_roles_name ON roles (name);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ----------------------------------------------------------- 2. User content
CREATE TABLE addresses (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    phone        VARCHAR(20)  NULL,
    line1        VARCHAR(255) NOT NULL,
    line2        VARCHAR(255) NULL,
    city         VARCHAR(100) NOT NULL,
    state        VARCHAR(100) NOT NULL,
    pincode      VARCHAR(10)  NOT NULL,
    is_default   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_addr_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX ix_addr_user ON addresses (user_id);

CREATE TABLE refresh_tokens (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    jti         VARCHAR(64) NOT NULL,
    family_id   VARCHAR(64) NOT NULL,
    expires_at  DATETIME    NOT NULL,
    revoked_at  DATETIME    NULL,
    replaced_by VARCHAR(64) NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_refresh_jti ON refresh_tokens (jti);
CREATE INDEX ix_refresh_user ON refresh_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at DATETIME     NOT NULL,
    used_at    DATETIME     NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_prt_token ON password_reset_tokens (token_hash);

CREATE TABLE email_verification_tokens (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    expires_at  DATETIME     NOT NULL,
    verified_at DATETIME     NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_evt_token ON email_verification_tokens (token_hash);

CREATE TABLE notifications (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(120) NOT NULL,
    body       TEXT         NULL,
    read_at    DATETIME     NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
CREATE INDEX ix_notifications_user_unread ON notifications (user_id, read_at);

-- --------------------------------------------------------- 3. Catalogue
CREATE TABLE categories (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    parent_category_id  BIGINT       NULL,
    name                VARCHAR(100) NOT NULL,
    slug                VARCHAR(120) NOT NULL,
    image_url           VARCHAR(255) NULL,
    is_active           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cat_parent FOREIGN KEY (parent_category_id) REFERENCES categories (id)
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_categories_slug ON categories (slug);

CREATE TABLE brands (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(120) NOT NULL,
    logo_url   VARCHAR(255) NULL,
    is_active  TINYINT(1)   NOT NULL DEFAULT 1,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_brands_slug ON brands (slug);

CREATE TABLE products (
    id            BIGINT        AUTO_INCREMENT PRIMARY KEY,
    category_id   BIGINT        NOT NULL,
    brand_id      BIGINT        NOT NULL,
    name          VARCHAR(180)  NOT NULL,
    slug          VARCHAR(200)  NOT NULL,
    description   TEXT          NULL,
    mrp           DECIMAL(10,2) NOT NULL,
    selling_price DECIMAL(10,2) NOT NULL,
    rating_avg    DECIMAL(3,2)  NOT NULL DEFAULT 0,
    rating_count  INT           NOT NULL DEFAULT 0,
    is_featured   TINYINT(1)    NOT NULL DEFAULT 0,
    is_active     TINYINT(1)    NOT NULL DEFAULT 1,
    is_deleted    TINYINT(1)    NOT NULL DEFAULT 0,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_prod_cat   FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_prod_brand FOREIGN KEY (brand_id)    REFERENCES brands (id)
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_products_slug ON products (slug);
CREATE INDEX ix_products_cat   ON products (category_id);
CREATE INDEX ix_products_brand ON products (brand_id);

CREATE TABLE product_images (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    image_url  VARCHAR(255) NOT NULL,
    is_primary TINYINT(1)   NOT NULL DEFAULT 0,
    sort_order INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_img_prod FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB;
CREATE INDEX ix_img_prod ON product_images (product_id);

CREATE TABLE product_specifications (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    spec_key   VARCHAR(80)  NOT NULL,
    spec_value VARCHAR(255) NOT NULL,
    CONSTRAINT fk_spec_prod FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_spec_prod_key ON product_specifications (product_id, spec_key);

-- ---------------------------------------------------------- 4. Inventory
CREATE TABLE inventory (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id          BIGINT NOT NULL,
    total_quantity      INT    NOT NULL DEFAULT 0,
    reserved_quantity   INT    NOT NULL DEFAULT 0,
    low_stock_threshold INT    NOT NULL DEFAULT 10,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_inv_prod FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_inventory_prod ON inventory (product_id);
-- Non-negativity / no-oversell guarded in UPDATE ... WHERE available >= n statements executed
-- inside transactions (see InventoryRepository), not by MySQL CHECK constraints.

CREATE TABLE inventory_transactions (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    inventory_id     BIGINT       NOT NULL,
    transaction_type VARCHAR(20)  NOT NULL,  -- PURCHASE|ADJUST|RESERVE|RELEASE|COMMIT|RETURN
    quantity_delta   INT          NOT NULL,
    note             VARCHAR(255) NULL,
    order_id         BIGINT       NULL,
    user_id          BIGINT       NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invtxn_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (id)
) ENGINE = InnoDB;
CREATE INDEX ix_invtxn_inventory_time ON inventory_transactions (inventory_id, created_at);

-- ------------------------------------------------------ 5. Cart / wishlist
CREATE TABLE carts (
    id         BIGINT    AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_carts_user ON carts (user_id);

CREATE TABLE cart_items (
    id         BIGINT    AUTO_INCREMENT PRIMARY KEY,
    cart_id    BIGINT    NOT NULL,
    product_id BIGINT    NOT NULL,
    quantity   INT       NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ci_cart    FOREIGN KEY (cart_id)    REFERENCES carts (id)    ON DELETE CASCADE,
    CONSTRAINT fk_ci_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_cart_items ON cart_items (cart_id, product_id);

CREATE TABLE wishlists (
    id         BIGINT    AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_wl_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_wishlists_user ON wishlists (user_id);

CREATE TABLE wishlist_items (
    id          BIGINT    AUTO_INCREMENT PRIMARY KEY,
    wishlist_id BIGINT    NOT NULL,
    product_id  BIGINT    NOT NULL,
    added_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wi_wishlist FOREIGN KEY (wishlist_id) REFERENCES wishlists (id) ON DELETE CASCADE,
    CONSTRAINT fk_wi_product  FOREIGN KEY (product_id)  REFERENCES products (id)
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_wishlist_items ON wishlist_items (wishlist_id, product_id);

-- ------------------------------------------------------------- 7. Coupons
-- Declared BEFORE `orders` because `orders.coupon_id` holds an FK to this
-- table and InnoDB requires the referenced table to exist within the same file.
CREATE TABLE coupons (
    id                  BIGINT        AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(30)   NOT NULL,
    discount_type       VARCHAR(10)   NOT NULL,  -- PERCENT | FIXED
    discount_value      DECIMAL(10,2) NOT NULL,
    min_order_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_discount_amount DECIMAL(10,2) NULL,
    expires_at          DATETIME      NOT NULL,
    max_global_uses     INT           NULL,
    max_uses_per_user   INT           NULL DEFAULT 1,
    is_active           TINYINT(1)    NOT NULL DEFAULT 1,
    deleted_at          DATETIME      NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_coupons_code ON coupons (code);
CREATE INDEX ix_coupons_active ON coupons (is_active);

-- ------------------------------------------------------------- 6. Orders
CREATE TABLE orders (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    order_number    VARCHAR(20)   NOT NULL,
    address_snapshot TEXT         NOT NULL,             -- immutable JSON snapshot
    subtotal        DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    shipping_fee    DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_amount    DECIMAL(10,2) NOT NULL,
    status          VARCHAR(30)   NOT NULL,  -- PENDING|CONFIRMED|PROCESSING|SHIPPED|OUT_FOR_DELIVERY|DELIVERED|CANCELLED|RETURN_REQUESTED|RETURNED
    payment_method  VARCHAR(15)   NOT NULL,  -- COD | RAZORPAY
    coupon_id       BIGINT        NULL,
    idempotency_key VARCHAR(64)   NULL,
    cancel_reason   VARCHAR(255)  NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_user   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT fk_order_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id)
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_orders_number  ON orders (order_number);
CREATE UNIQUE INDEX ux_orders_idemkey ON orders (idempotency_key);
CREATE INDEX ix_orders_user_time ON orders (user_id, created_at);
CREATE INDEX ix_orders_status    ON orders (status);

CREATE TABLE order_items (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    order_id          BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    product_name      VARCHAR(180)  NOT NULL,
    product_image_url VARCHAR(255)  NULL,
    unit_price        DECIMAL(10,2) NOT NULL,  -- immutable snapshot at purchase time
    quantity          INT           NOT NULL,
    line_total        DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_oi_order   FOREIGN KEY (order_id)   REFERENCES orders (id)   ON DELETE CASCADE,
    CONSTRAINT fk_oi_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB;
CREATE INDEX ix_oi_order ON order_items (order_id);

CREATE TABLE order_status_history (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT       NOT NULL,
    previous_status VARCHAR(30)  NULL,
    new_status      VARCHAR(30)  NOT NULL,
    changed_by      VARCHAR(60)  NOT NULL,
    note            VARCHAR(255) NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB;
CREATE INDEX ix_osh_order ON order_status_history (order_id);

CREATE TABLE shipments (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT      NOT NULL,
    courier         VARCHAR(60) NULL,
    tracking_number VARCHAR(60) NULL,
    shipped_at      DATETIME    NULL,
    delivered_at    DATETIME    NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ship_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_shipments_order ON shipments (order_id);

CREATE TABLE return_requests (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    reason     TEXT        NULL,
    status     VARCHAR(15) NOT NULL, -- REQUESTED|APPROVED|REJECTED|PICKED|RETURNED
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ret_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_ret_user  FOREIGN KEY (user_id)  REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX ix_ret_order ON return_requests (order_id);

CREATE TABLE coupon_redemptions (
    id          BIGINT    AUTO_INCREMENT PRIMARY KEY,
    coupon_id   BIGINT    NOT NULL,
    user_id     BIGINT    NOT NULL,
    order_id    BIGINT    NOT NULL,
    redeemed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cr_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_cr_user   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT fk_cr_order  FOREIGN KEY (order_id)  REFERENCES orders (id)
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_cr_coupon_order ON coupon_redemptions (coupon_id, order_id);
CREATE INDEX ix_cr_user ON coupon_redemptions (user_id, coupon_id);

-- ------------------------------------------------------------ 8. Payments
CREATE TABLE payments (
    id                  BIGINT        AUTO_INCREMENT PRIMARY KEY,
    order_id            BIGINT        NOT NULL,
    method              VARCHAR(15)   NOT NULL, -- COD | RAZORPAY
    gateway_order_id    VARCHAR(64)   NULL,
    razorpay_payment_id VARCHAR(64)   NULL,
    status              VARCHAR(15)   NOT NULL, -- PENDING | PAID | FAILED | REFUNDED
    amount              DECIMAL(10,2) NOT NULL,
    paid_at             DATETIME      NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pay_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB;
CREATE UNIQUE INDEX ux_payments_order   ON payments (order_id);
CREATE UNIQUE INDEX ux_payments_gw_order ON payments (gateway_order_id);

CREATE TABLE payment_transactions (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    payment_id          BIGINT       NOT NULL,
    razorpay_order_id   VARCHAR(64)  NULL,
    razorpay_payment_id VARCHAR(64)  NULL,
    signature           VARCHAR(255) NULL,
    event_type          VARCHAR(30)  NOT NULL, -- ORDER_CREATED | CAPTURED | FAILED | WEBHOOK
    payload             TEXT         NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ptx_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE
) ENGINE = InnoDB;
CREATE INDEX ix_ptx_payment ON payment_transactions (payment_id);

CREATE TABLE refunds (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    return_request_id BIGINT        NULL,
    payment_id        BIGINT        NOT NULL,
    refund_amount     DECIMAL(10,2) NOT NULL,
    gateway_refund_id VARCHAR(64)   NULL,
    status            VARCHAR(15)   NOT NULL, -- REQUESTED | PROCESSING | COMPLETED | FAILED
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_refund_return  FOREIGN KEY (return_request_id) REFERENCES return_requests (id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id)        REFERENCES payments (id)
) ENGINE = InnoDB;
