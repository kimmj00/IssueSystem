BEGIN;

CREATE TABLE IF NOT EXISTS account (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

COMMENT ON TABLE account IS 'TC Bank 사용자 계정 테이블';
COMMENT ON COLUMN account.id IS '계정 고유 ID';
COMMENT ON COLUMN account.user_id IS '로그인 ID';
COMMENT ON COLUMN account.password_hash IS 'BCrypt 해시 비밀번호';
COMMENT ON COLUMN account.name IS '사용자 이름';
COMMENT ON COLUMN account.role IS '계정 권한(USER, ADMIN)';
COMMENT ON COLUMN account.created_at IS '가입 일시';
COMMENT ON COLUMN account.updated_at IS '마지막 수정 일시';

COMMIT;
