ALTER TABLE chat
    ADD COLUMN hidden_at DATETIME(6) NULL AFTER content;
