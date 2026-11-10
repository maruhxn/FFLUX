CREATE TABLE donation (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    order_id       VARCHAR(64)  NOT NULL,
    broadcast_id   BIGINT       NOT NULL,
    streamer_id    BIGINT       NOT NULL,
    donor_id       BIGINT       NOT NULL,
    donor_nickname VARCHAR(12)  NOT NULL,
    amount         BIGINT       NOT NULL,
    message        VARCHAR(100),
    status         VARCHAR(10)  NOT NULL,
    payment_key    VARCHAR(200),
    created_at     DATETIME(6)  NOT NULL,
    confirmed_at   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_donation_order_id UNIQUE (order_id)
) ENGINE = InnoDB;
