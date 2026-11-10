-- 방송별 확정 후원 내역(최신순 커서)과 합계를 읽는다.
CREATE INDEX idx_donation_broadcast_status ON donation (broadcast_id, status, id);
