-- YouPI PG self-hosted UPI integration.
--
-- YouPI is an experimental gateway: UPI orders may only be fulfilled after a
-- signed webhook reports event=RECONCILED, method=UPI and
-- settlementState=RECONCILED. The extra columns keep the merchant-side
-- checkout URL and the bank UTR alongside the existing Razorpay fields.

ALTER TABLE payments
    ADD COLUMN provider     VARCHAR(15)    NOT NULL DEFAULT 'RAZORPAY' AFTER method,
    ADD COLUMN checkout_url VARCHAR(1024) NULL AFTER gateway_order_id,
    ADD COLUMN utr          VARCHAR(32)   NULL AFTER razorpay_payment_id;

CREATE INDEX ix_payments_provider ON payments (provider);

-- Append-only audit ledger for YouPI session creation and webhook deliveries.
-- delivery_id is nullable for SESSION_CREATED rows; the unique index is the
-- idempotency guard for webhook retries (MySQL permits multiple NULLs).
CREATE TABLE youpi_events (
    id                  BIGINT        AUTO_INCREMENT PRIMARY KEY,
    delivery_id         VARCHAR(64)   NULL,
    payment_id          BIGINT        NULL,
    event_type          VARCHAR(32)   NOT NULL, -- SESSION_CREATED | WEBHOOK
    event               VARCHAR(32)   NULL,     -- OBSERVED_SUCCESS | RECONCILED | ...
    method              VARCHAR(32)   NULL,     -- UPI | COD | ...
    settlement_state    VARCHAR(32)   NULL,     -- RECONCILED | NOT_RECONCILED | ...
    result              VARCHAR(20)   NOT NULL, -- SESSION_CREATED | SETTLED | OBSERVED | IGNORED | REJECTED
    reason              VARCHAR(255)  NULL,
    order_reference     VARCHAR(64)   NOT NULL,
    provider_payment_id VARCHAR(64)   NULL,
    amount              DECIMAL(10,2) NULL,
    utr                 VARCHAR(32)   NULL,
    payload             TEXT          NULL,
    received_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_youpi_delivery UNIQUE (delivery_id),
    CONSTRAINT fk_youpi_event_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB;

CREATE INDEX ix_youpi_event_order   ON youpi_events (order_reference);
CREATE INDEX ix_youpi_event_payment ON youpi_events (payment_id);
