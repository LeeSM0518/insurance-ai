-- 채팅 세션
CREATE TABLE chat_sessions (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE chat_sessions IS 'AI 질의응답 채팅 세션';
COMMENT ON COLUMN chat_sessions.id IS '세션 고유 ID';
COMMENT ON COLUMN chat_sessions.user_id IS '사용자 ID (FK → users)';
COMMENT ON COLUMN chat_sessions.title IS '세션 제목 (첫 질문 요약으로 자동 생성)';
COMMENT ON COLUMN chat_sessions.created_at IS '생성일시 (UTC)';
COMMENT ON COLUMN chat_sessions.updated_at IS '마지막 활동 일시 (UTC)';

-- 채팅 메시지
CREATE TABLE chat_messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_session_id UUID        NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role            VARCHAR(10) NOT NULL,
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE chat_messages IS '채팅 메시지 (사용자 질문 + AI 답변)';
COMMENT ON COLUMN chat_messages.id IS '메시지 고유 ID';
COMMENT ON COLUMN chat_messages.chat_session_id IS '채팅 세션 ID (FK → chat_sessions)';
COMMENT ON COLUMN chat_messages.role IS '메시지 역할 (USER: 사용자, ASSISTANT: AI)';
COMMENT ON COLUMN chat_messages.content IS '메시지 내용';
COMMENT ON COLUMN chat_messages.created_at IS '생성일시 (UTC)';

-- 메시지-약관 참조 관계
CREATE TABLE chat_message_sources (
    id                UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_message_id   UUID  NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    policy_article_id UUID  NOT NULL REFERENCES policy_articles(id),
    relevance_score   FLOAT NOT NULL
);

COMMENT ON TABLE chat_message_sources IS 'AI 답변의 약관 출처 참조 (인용 근거)';
COMMENT ON COLUMN chat_message_sources.id IS '참조 고유 ID';
COMMENT ON COLUMN chat_message_sources.chat_message_id IS '채팅 메시지 ID (FK → chat_messages)';
COMMENT ON COLUMN chat_message_sources.policy_article_id IS '참조 약관 조문 ID (FK → policy_articles)';
COMMENT ON COLUMN chat_message_sources.relevance_score IS '유사도 점수 (0.0~1.0)';
