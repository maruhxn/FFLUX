CREATE TABLE chat (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    broadcast_id    BIGINT       NOT NULL,
    author_id       BIGINT       NOT NULL,
    author_nickname VARCHAR(12)  NOT NULL,
    content         VARCHAR(200) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_chat_broadcast_id_id (broadcast_id, id)
) ENGINE = InnoDB;
