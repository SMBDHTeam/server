-- 관리자 조치 기록.
--
-- 지금까지 관리자가 게시물을 지우거나 사용자를 정지시키면 애플리케이션 로그 한 줄만 남았다.
-- 컨테이너를 다시 띄우면 사라지고 조회할 방법도 없어, "누가 언제 무엇을 왜 했는가"에
-- 답할 수 없었다. 제재는 되돌릴 수 없는 행위라 기록이 곧 책임이다.
create table admin_actions (
    id          bigserial primary key,
    admin_id    bigint       not null references users (id),
    action      varchar(40)  not null,
    target_type varchar(20)  not null,
    target_id   bigint,
    reason      varchar(500),
    detail      varchar(500),
    created_at  timestamp    not null
);

-- 최근 순 조회가 기본 화면이다.
create index idx_admin_actions_created_at on admin_actions (created_at desc);

-- "이 사용자에게 무슨 조치가 있었나"를 대상으로 되짚는다. 분쟁 대응의 주 경로다.
create index idx_admin_actions_target on admin_actions (target_type, target_id);

-- 관리자별 조치 이력.
create index idx_admin_actions_admin on admin_actions (admin_id, created_at desc);

-- 관리자가 지운 게시물은 작성자가 되살릴 수 없어야 한다.
--
-- restore 는 작성자인지와 기한만 보고 누가 지웠는지는 보지 않았다. 그래서 관리자가
-- 조치로 지운 글을 작성자가 그대로 복구할 수 있었다. 지우지 않고 표시만 남기는 이유는
-- 오판을 되돌릴 수 있어야 하고, 분쟁이 붙었을 때 원문이 근거가 되기 때문이다.
alter table posts add column deleted_by_admin boolean not null default false;
