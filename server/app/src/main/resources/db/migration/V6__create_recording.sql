CREATE TABLE recording (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    broadcast_id     BIGINT       NOT NULL,
    streamer_id      BIGINT       NOT NULL,
    segment_path     VARCHAR(255) NOT NULL,
    status           VARCHAR(10)  NOT NULL,
    file_path        VARCHAR(255),
    duration_seconds BIGINT,
    created_at       DATETIME(6)  NOT NULL,
    hidden_at        DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_recording_broadcast_id UNIQUE (broadcast_id),
    INDEX ix_recording_status_broadcast_id (status, broadcast_id)
) ENGINE = InnoDB;
