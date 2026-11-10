ALTER TABLE comment
    ADD COLUMN parent_id BIGINT NULL AFTER broadcast_id,
    ADD INDEX ix_comment_parent_id_id (parent_id, id);
