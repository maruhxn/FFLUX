CREATE TABLE comment (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    broadcast_id    BIGINT       NOT NULL,
    author_id       BIGINT       NOT NULL,
    author_nickname VARCHAR(12)  NOT NULL,
    content         VARCHAR(500) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6),
    deleted_at      DATETIME(6),
    hidden_at       DATETIME(6),
    PRIMARY KEY (id),
    INDEX ix_comment_broadcast_id_id (broadcast_id, id)
) ENGINE = InnoDB;
