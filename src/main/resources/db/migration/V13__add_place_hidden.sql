-- 잘못 적재된 장소를 가린다.
--
-- 행을 지우면 TourAPI 증분 동기화가 다음 실행에서 같은 장소를 다시 만든다. 숨김 표시로
-- 검색과 일정 후보에서 빼는 편이 확실하다. 되돌리기도 쉽다.
ALTER TABLE places ADD COLUMN IF NOT EXISTS hidden_at timestamp;
ALTER TABLE places ADD COLUMN IF NOT EXISTS hidden_reason text;

-- 검색과 일정 후보 조회가 매번 이 조건을 붙인다. 숨긴 장소는 극소수라 부분 인덱스로 둔다.
CREATE INDEX IF NOT EXISTS idx_places_hidden_at ON places (hidden_at) WHERE hidden_at IS NOT NULL;
