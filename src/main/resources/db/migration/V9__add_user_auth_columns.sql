-- 구글 로그인과 권한을 위해 users 를 확장한다.
--
-- provider, provider_id, email 은 nullable 이다. V6 로 이미 만들어진 행이 있으면
-- NOT NULL 은 실패한다. 로그인으로 생기는 행은 애플리케이션이 항상 채운다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider varchar(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_id varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS role varchar(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN IF NOT EXISTS status varchar(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended_until timestamp;
-- 정지 사유. 관리자가 남기고 필요하면 사용자에게 안내한다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended_reason text;

-- 같은 구글 계정으로 두 번 가입되지 않게 한다. 닉네임 고유 인덱스와 같은 이유로
-- 탈퇴하지 않은 행에만 적용한다. 탈퇴 후 재가입은 새 행으로 들어온다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_provider_active
    ON users (provider, provider_id)
    WHERE deleted_at IS NULL;

-- 관리자 목록 조회용. ADMIN 은 극소수라 선택도가 높다.
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);
