ALTER TABLE broadcast
    ADD COLUMN recording_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN media_path        VARCHAR(30) NULL,
    ADD CONSTRAINT uk_broadcast_media_path UNIQUE (media_path),
    ADD INDEX ix_broadcast_streamer_id_status (streamer_id, status);

CREATE TABLE publish_token (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    broadcast_id BIGINT      NOT NULL,
    token_hash   CHAR(64)    NOT NULL,
    expires_at   DATETIME(6) NOT NULL,
    consumed_at  DATETIME(6),
    revoked_at   DATETIME(6),
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_publish_token_token_hash UNIQUE (token_hash),
    INDEX ix_publish_token_broadcast_id (broadcast_id)
) ENGINE = InnoDB;
