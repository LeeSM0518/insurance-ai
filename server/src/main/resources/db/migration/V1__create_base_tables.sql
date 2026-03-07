-- 보험사 목록
CREATE TABLE insurance_companies (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(100) NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    association_code VARCHAR(50),
    is_active        BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (name, type)
);

COMMENT ON TABLE insurance_companies IS '보험사 목록 (생보 20개사 + 손보 16개사)';
COMMENT ON COLUMN insurance_companies.id IS '보험사 고유 ID';
COMMENT ON COLUMN insurance_companies.name IS '보험사명 (예: 삼성생명, 현대해상)';
COMMENT ON COLUMN insurance_companies.type IS '보험 유형 (LIFE: 생명보험, NON_LIFE: 손해보험)';
COMMENT ON COLUMN insurance_companies.association_code IS '협회 사이트 보험사 식별 코드 (크롤링 매핑용)';
COMMENT ON COLUMN insurance_companies.is_active IS '활성 여부 (합병/폐업 시 false)';
COMMENT ON COLUMN insurance_companies.created_at IS '생성일시 (UTC)';
COMMENT ON COLUMN insurance_companies.updated_at IS '수정일시 (UTC)';

-- 사용자
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    provider      VARCHAR(20)  NOT NULL,
    provider_id   VARCHAR(255) NOT NULL,
    nickname      VARCHAR(50)  NOT NULL,
    email         VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_id)
);

COMMENT ON TABLE users IS '서비스 사용자 (OAuth 소셜 로그인)';
COMMENT ON COLUMN users.id IS '사용자 고유 ID';
COMMENT ON COLUMN users.provider IS 'OAuth 제공자 (KAKAO, GOOGLE, APPLE 등)';
COMMENT ON COLUMN users.provider_id IS 'OAuth 제공자별 고유 사용자 ID';
COMMENT ON COLUMN users.nickname IS 'OAuth 프로필 닉네임';
COMMENT ON COLUMN users.email IS 'OAuth 프로필 이메일 (사용자 동의 시에만 제공)';
COMMENT ON COLUMN users.created_at IS '가입일시 (UTC)';
COMMENT ON COLUMN users.updated_at IS '수정일시 (UTC)';
COMMENT ON COLUMN users.last_login_at IS '마지막 로그인 일시 (UTC)';

-- 리프레시 토큰
CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

COMMENT ON TABLE refresh_tokens IS 'JWT 리프레시 토큰 (서버측 무효화 지원)';
COMMENT ON COLUMN refresh_tokens.id IS '리프레시 토큰 고유 ID';
COMMENT ON COLUMN refresh_tokens.user_id IS '토큰 소유 사용자 ID';
COMMENT ON COLUMN refresh_tokens.token IS 'JWT 토큰 문자열';
COMMENT ON COLUMN refresh_tokens.expires_at IS '토큰 만료 일시';
COMMENT ON COLUMN refresh_tokens.created_at IS '생성일시 (UTC)';
COMMENT ON COLUMN refresh_tokens.updated_at IS '수정일시 (UTC)';
