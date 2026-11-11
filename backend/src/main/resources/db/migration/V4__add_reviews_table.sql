-- =====================================================================
-- ShopSphere — V4: Add Reviews table and sample seed reviews
-- =====================================================================

CREATE TABLE reviews (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    order_id    BIGINT       NULL,
    rating      INT          NOT NULL,
    title       VARCHAR(120) NULL,
    body        TEXT         NULL,
    is_verified TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_rev_prod  FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_rev_user  FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT fk_rev_order FOREIGN KEY (order_id)   REFERENCES orders (id)
) ENGINE = InnoDB;

CREATE UNIQUE INDEX ux_reviews_prod_user ON reviews (product_id, user_id);
CREATE INDEX ix_reviews_prod ON reviews (product_id);

-- Seed initial sample reviews for featured products
INSERT INTO reviews (product_id, user_id, rating, title, body, is_verified) VALUES
(1, 2, 5, 'Superb performance and camera', 'The 120Hz display is buttery smooth and battery easily lasts all day. Highly recommended!', 1),
(1, 3, 4, 'Great value flagship', 'Very good build quality and camera. Fast charging is very convenient.', 1),
(3, 2, 5, 'Best ultrabook in this segment', 'Extremely lightweight, crisp display and fantastic keyboard. Easily 10+ hours battery life.', 1),
(5, 3, 5, 'Great ANC and sound stage', 'Noise cancellation blocks out office hum nicely. Punchy bass and clear vocals.', 1);
