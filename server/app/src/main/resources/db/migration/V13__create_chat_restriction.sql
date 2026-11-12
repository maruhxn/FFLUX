CREATE TABLE chat_restriction (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    streamer_id     BIGINT      NOT NULL,
    member_id       BIGINT      NOT NULL,
    member_nickname VARCHAR(12) NOT NULL,
    until           DATETIME(6) NULL,
    restricted_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_restriction_streamer_member (streamer_id, member_id)
) ENGINE = InnoDB;
