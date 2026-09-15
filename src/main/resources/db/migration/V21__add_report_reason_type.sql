-- 신고 사유를 유형으로 받는다. 설명은 기타일 때만 필수라 reason 의 NOT NULL 을 푼다.
-- 이미 쌓인 신고는 자유 입력 사유만 있으므로 OTHER 로 두고 원문을 그대로 남긴다.
alter table reports add column reason_type varchar(30) not null default 'OTHER';
alter table reports alter column reason drop not null;

create index idx_reports_reason_type on reports (reason_type);
