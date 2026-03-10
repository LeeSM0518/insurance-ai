CREATE TABLE chat_feedbacks (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_message_id UUID         NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    feedback_type   VARCHAR(10)  NOT NULL,
    category        VARCHAR(30),
    comment         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE(chat_message_id, user_id),
    CONSTRAINT chk_feedback_type CHECK (feedback_type IN ('POSITIVE', 'NEGATIVE'))
);

COMMENT ON TABLE chat_feedbacks IS 'AI 답변에 대한 사용자 피드백 (좋아요/싫어요)';
COMMENT ON COLUMN chat_feedbacks.id IS '피드백 고유 ID';
COMMENT ON COLUMN chat_feedbacks.chat_message_id IS '피드백 대상 채팅 메시지 ID';
COMMENT ON COLUMN chat_feedbacks.user_id IS '피드백 작성자 ID';
COMMENT ON COLUMN chat_feedbacks.feedback_type IS '피드백 유형: POSITIVE(좋아요), NEGATIVE(싫어요)';
COMMENT ON COLUMN chat_feedbacks.category IS '부정 피드백 카테고리: INACCURATE, MISUNDERSTOOD, INSUFFICIENT, WRONG_SOURCE, OTHER (nullable)';
COMMENT ON COLUMN chat_feedbacks.comment IS '자유 텍스트 코멘트 — 카테고리 OTHER 선택 시 (nullable)';
COMMENT ON COLUMN chat_feedbacks.created_at IS '피드백 생성 시각';
COMMENT ON COLUMN chat_feedbacks.updated_at IS '피드백 수정 시각';
