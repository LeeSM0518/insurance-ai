-- Refresh Token Family 추적 컬럼 추가 (RT Reuse Detection)
ALTER TABLE refresh_tokens
    ADD COLUMN family_id   UUID    NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN replaced_by UUID    REFERENCES refresh_tokens(id);

-- Token Family 기반 조회를 위한 인덱스
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);

COMMENT ON COLUMN refresh_tokens.family_id IS '토큰 패밀리 ID (동일 로그인 세션의 토큰 계보 추적)';
COMMENT ON COLUMN refresh_tokens.revoked IS '토큰 폐기 여부 (true면 사용 불가)';
COMMENT ON COLUMN refresh_tokens.replaced_by IS '이 토큰을 교체한 새 토큰 ID (rotation 추적)';
