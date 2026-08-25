-- 신고 처리 이력.
--
-- 누가 언제 처리했는지 남기지 않으면, 같은 신고를 두 관리자가 다시 들여다보거나
-- 처리 결과에 이견이 생겼을 때 되짚을 근거가 없다.
ALTER TABLE reports ADD COLUMN IF NOT EXISTS handled_by bigint REFERENCES users(id);
ALTER TABLE reports ADD COLUMN IF NOT EXISTS handled_at timestamp;

-- 관리자 화면은 대기 중인 신고부터 본다. status 별 선택도가 높다.
CREATE INDEX IF NOT EXISTS idx_reports_status_created_at ON reports (status, created_at DESC);
