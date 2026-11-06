CREATE TABLE broadcast (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    streamer_id       BIGINT       NOT NULL,
    streamer_nickname VARCHAR(12)  NOT NULL,
    title             VARCHAR(50)  NOT NULL,
    description       VARCHAR(500),
    status            VARCHAR(10)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    deleted_at        DATETIME(6),
    PRIMARY KEY (id),
    INDEX ix_broadcast_status_id (status, id),
    INDEX ix_broadcast_streamer_id_id (streamer_id, id)
) ENGINE = InnoDB;
