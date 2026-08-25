-- 같은 대상을 여러 번 신고하지 못하게 한다. 지금은 서비스 코드로만 막고 있어
-- 같은 요청이 동시에 들어오면 중복 행이 남는다.
DELETE FROM reports r
USING reports keep
WHERE r.reporter_id = keep.reporter_id
  AND r.target_type = keep.target_type
  AND r.target_id = keep.target_id
  AND r.id > keep.id;

ALTER TABLE reports
    ADD CONSTRAINT uk_reports_reporter_target
    UNIQUE (reporter_id, target_type, target_id);
