-- 차단은 양방향으로 조회한다. 알림 목록과 댓글 작성은 "내가 차단했는가"와
-- "상대가 나를 차단했는가"를 함께 본다.
--
-- 기본키가 (blocker_id, blocked_id) 라 앞 방향은 인덱스를 타지만 반대 방향은 타지 못해
-- 차단 행이 쌓일수록 전체 스캔이 된다. 반대 순서 인덱스를 따로 둔다.
CREATE INDEX IF NOT EXISTS idx_blocks_blocked_blocker
    ON blocks (blocked_id, blocker_id);
