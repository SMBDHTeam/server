-- 방문지의 도착·출발 시각을 저장한다.
--
-- 일정 생성 시점에는 계산되지만 저장할 컬럼이 없어, 다시 조회하면 항상 null이었다.
-- 응답의 arriveAt·departAt이 비어 화면에서 "몇 시에 도착하는지"를 보여줄 수 없었다.
--
-- 시각만 담는다. 날짜는 소속 schedule_days.date로 결정된다.
-- 기존 행은 값을 복원할 수 없으므로 nullable로 둔다.
ALTER TABLE schedule_stops ADD COLUMN IF NOT EXISTS arrive_at time;
ALTER TABLE schedule_stops ADD COLUMN IF NOT EXISTS depart_at time;
