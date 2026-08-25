-- 일정에 소유자를 붙인다.
--
-- nullable 이다. 이미 저장된 일정은 소유자를 알 수 없다. 인증이 없던 시절에 만들어졌고
-- 만든 사람을 되짚을 근거가 남아 있지 않다. NULL 인 일정은 목록에서 아무에게도 보이지
-- 않으며, 단건 조회는 공유 링크로만 닿는다.
--
-- 이 테이블은 FastAPI 도 쓴다. 값을 채우는 쪽은 FastAPI 이며, 컬럼이 먼저 있어야
-- 그쪽 배포가 가능하다.
ALTER TABLE schedules ADD COLUMN IF NOT EXISTS user_id bigint REFERENCES users(id);

-- 내 일정 목록 조회. 사용자당 일정 수는 적고 전체는 계속 늘어난다.
CREATE INDEX IF NOT EXISTS idx_schedules_user_id ON schedules (user_id);
