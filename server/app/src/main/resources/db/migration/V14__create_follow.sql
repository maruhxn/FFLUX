CREATE TABLE follow (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    follower_id BIGINT      NOT NULL,
    streamer_id BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_follow_follower_streamer (follower_id, streamer_id),
    KEY idx_follow_streamer (streamer_id)
) ENGINE = InnoDB;
