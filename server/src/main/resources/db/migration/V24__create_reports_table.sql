CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    report_type VARCHAR(20) NOT NULL,
    title VARCHAR(200),
    content TEXT NOT NULL,
    metadata JSONB,
    share_token VARCHAR(64) UNIQUE,
    share_expires_at TIMESTAMPTZ,
    chat_session_id UUID REFERENCES chat_sessions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE reports IS '치트시트, 비교표, 팔로업 리포트 저장';
COMMENT ON COLUMN reports.report_type IS 'CHEATSHEET | COMPARISON | FOLLOWUP';
COMMENT ON COLUMN reports.metadata IS '생성 시 사용된 파라미터 (보험사, 카테고리 등)';
COMMENT ON COLUMN reports.share_token IS '공유 링크용 UUID 토큰 (Phase 3에서 활용)';

CREATE INDEX idx_reports_user_id ON reports(user_id, created_at DESC);
CREATE INDEX idx_reports_share_token ON reports(share_token) WHERE share_token IS NOT NULL;
CREATE INDEX idx_reports_type ON reports(report_type, user_id);
