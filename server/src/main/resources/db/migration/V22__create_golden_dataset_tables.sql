-- 골든 데이터셋 버전 관리
CREATE TABLE golden_datasets (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    version     VARCHAR(20)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    total_count INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE(name, version)
);

COMMENT ON TABLE golden_datasets IS '골든 데이터셋 버전 관리';
COMMENT ON COLUMN golden_datasets.id IS '데이터셋 고유 ID';
COMMENT ON COLUMN golden_datasets.name IS '데이터셋 이름 (예: MVP v1.0)';
COMMENT ON COLUMN golden_datasets.description IS '데이터셋 설명';
COMMENT ON COLUMN golden_datasets.version IS '버전 (예: 1.0.0)';
COMMENT ON COLUMN golden_datasets.status IS '상태: DRAFT(구축중), ACTIVE(평가용), ARCHIVED(보관)';
COMMENT ON COLUMN golden_datasets.total_count IS '전체 QA 쌍 수';
COMMENT ON COLUMN golden_datasets.created_at IS '생성 시각';
COMMENT ON COLUMN golden_datasets.updated_at IS '수정 시각';

-- 골든 데이터셋 질문-답변 쌍
CREATE TABLE golden_qa_pairs (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id        UUID         NOT NULL REFERENCES golden_datasets(id),
    qa_code           VARCHAR(20)  NOT NULL UNIQUE,
    category          VARCHAR(30)  NOT NULL,
    question_type     VARCHAR(20)  NOT NULL,
    difficulty        VARCHAR(10)  NOT NULL,
    risk_level        VARCHAR(10)  NOT NULL,
    question          TEXT         NOT NULL,
    expected_answer   TEXT         NOT NULL,
    must_include      TEXT[],
    must_not_include  TEXT[],
    source_must_match BOOLEAN      NOT NULL DEFAULT true,
    status            VARCHAR(20)  NOT NULL DEFAULT 'SILVER',
    notes             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE golden_qa_pairs IS '골든 데이터셋 질문-답변 쌍';
COMMENT ON COLUMN golden_qa_pairs.id IS 'QA 쌍 고유 ID';
COMMENT ON COLUMN golden_qa_pairs.dataset_id IS '소속 데이터셋 ID';
COMMENT ON COLUMN golden_qa_pairs.qa_code IS '고유 코드 (GD-001 형식)';
COMMENT ON COLUMN golden_qa_pairs.category IS '질문 카테고리: COVERAGE, EXEMPTION, REDUCTION, QUALIFICATION, SPECIAL_TERMS, CLAIM_DOCUMENTS, ARTICLE, QNA, COVERAGE_DETAIL';
COMMENT ON COLUMN golden_qa_pairs.question_type IS '질문 유형: SINGLE_LOOKUP, COMPARISON, CONDITIONAL, COMPLEX, REJECTION, CONTEXTUAL';
COMMENT ON COLUMN golden_qa_pairs.difficulty IS '난이도: EASY, MEDIUM, HARD';
COMMENT ON COLUMN golden_qa_pairs.risk_level IS '리스크 수준: CRITICAL, HIGH, MEDIUM, LOW';
COMMENT ON COLUMN golden_qa_pairs.question IS '질문 텍스트';
COMMENT ON COLUMN golden_qa_pairs.expected_answer IS '기대 답변 텍스트';
COMMENT ON COLUMN golden_qa_pairs.must_include IS '답변에 반드시 포함해야 할 키워드 배열';
COMMENT ON COLUMN golden_qa_pairs.must_not_include IS '답변에 포함되면 안 되는 키워드 배열';
COMMENT ON COLUMN golden_qa_pairs.source_must_match IS '출처 일치 검증 여부';
COMMENT ON COLUMN golden_qa_pairs.status IS '검증 상태: SILVER(LLM 생성), GOLD(검증 완료), REJECTED(탈락)';
COMMENT ON COLUMN golden_qa_pairs.notes IS '검증자 메모';
COMMENT ON COLUMN golden_qa_pairs.created_at IS '생성 시각';
COMMENT ON COLUMN golden_qa_pairs.updated_at IS '수정 시각';

-- 골든 데이터셋 기대 출처 (Ground Truth Sources)
CREATE TABLE golden_qa_sources (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    qa_pair_id        UUID         NOT NULL REFERENCES golden_qa_pairs(id) ON DELETE CASCADE,
    policy_article_id UUID         NOT NULL REFERENCES policy_articles(id),
    insurance_company VARCHAR(100) NOT NULL,
    product_name      VARCHAR(200) NOT NULL,
    article_number    VARCHAR(20),
    content_type      VARCHAR(30),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE golden_qa_sources IS '골든 데이터셋 기대 출처 (Ground Truth Sources)';
COMMENT ON COLUMN golden_qa_sources.id IS '출처 고유 ID';
COMMENT ON COLUMN golden_qa_sources.qa_pair_id IS 'QA 쌍 ID';
COMMENT ON COLUMN golden_qa_sources.policy_article_id IS '실제 약관 조문 FK';
COMMENT ON COLUMN golden_qa_sources.insurance_company IS '보험사명 (비정규화, 검증 편의용)';
COMMENT ON COLUMN golden_qa_sources.product_name IS '상품명 (비정규화, 검증 편의용)';
COMMENT ON COLUMN golden_qa_sources.article_number IS '조문번호';
COMMENT ON COLUMN golden_qa_sources.content_type IS '내용 유형: COVERAGE, EXEMPTION 등';
COMMENT ON COLUMN golden_qa_sources.created_at IS '생성 시각';

-- 골든 데이터셋 평가 결과 이력
CREATE TABLE golden_eval_results (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id             UUID         NOT NULL REFERENCES golden_datasets(id),
    qa_pair_id             UUID         NOT NULL REFERENCES golden_qa_pairs(id),
    eval_run_id            VARCHAR(50)  NOT NULL,
    eval_type              VARCHAR(20)  NOT NULL,
    -- 검색 평가
    retrieved_article_ids  UUID[],
    context_precision      NUMERIC(5,4),
    context_recall         NUMERIC(5,4),
    mrr                    NUMERIC(5,4),
    ndcg                   NUMERIC(5,4),
    -- 생성 평가 (규칙 기반)
    must_include_pass      BOOLEAN,
    must_not_include_pass  BOOLEAN,
    source_match_pass      BOOLEAN,
    -- 생성 평가 (LLM-as-Judge)
    faithfulness_score     NUMERIC(3,2),
    accuracy_score         NUMERIC(3,2),
    completeness_score     NUMERIC(3,2),
    source_citation_score  NUMERIC(3,2),
    weighted_total         NUMERIC(3,2),
    hallucination_detected BOOLEAN,
    hallucination_details  TEXT,
    -- AI 응답 원문
    actual_answer          TEXT,
    -- 메타
    model_version          VARCHAR(50),
    prompt_version         VARCHAR(50),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE golden_eval_results IS '골든 데이터셋 평가 결과 이력';
COMMENT ON COLUMN golden_eval_results.id IS '평가 결과 고유 ID';
COMMENT ON COLUMN golden_eval_results.dataset_id IS '데이터셋 ID';
COMMENT ON COLUMN golden_eval_results.qa_pair_id IS 'QA 쌍 ID';
COMMENT ON COLUMN golden_eval_results.eval_run_id IS '평가 실행 식별자 (동일 run의 결과를 묶음)';
COMMENT ON COLUMN golden_eval_results.eval_type IS '평가 유형: RULE_BASED(규칙 기반), LLM_JUDGE(LLM 판정)';
COMMENT ON COLUMN golden_eval_results.retrieved_article_ids IS '검색된 조문 ID 목록';
COMMENT ON COLUMN golden_eval_results.context_precision IS 'Context Precision@K 점수';
COMMENT ON COLUMN golden_eval_results.context_recall IS 'Context Recall 점수';
COMMENT ON COLUMN golden_eval_results.mrr IS 'MRR (Mean Reciprocal Rank) 점수';
COMMENT ON COLUMN golden_eval_results.ndcg IS 'NDCG@K 점수';
COMMENT ON COLUMN golden_eval_results.must_include_pass IS 'must_include 키워드 포함 여부';
COMMENT ON COLUMN golden_eval_results.must_not_include_pass IS 'must_not_include 키워드 미포함 여부';
COMMENT ON COLUMN golden_eval_results.source_match_pass IS '출처 일치 여부';
COMMENT ON COLUMN golden_eval_results.actual_answer IS 'AI가 생성한 실제 답변';
COMMENT ON COLUMN golden_eval_results.model_version IS '사용된 AI 모델';
COMMENT ON COLUMN golden_eval_results.prompt_version IS '사용된 프롬프트 버전';
COMMENT ON COLUMN golden_eval_results.created_at IS '생성 시각';

-- 인덱스
CREATE INDEX idx_golden_qa_pairs_dataset ON golden_qa_pairs(dataset_id);
CREATE INDEX idx_golden_qa_pairs_status ON golden_qa_pairs(status);
CREATE INDEX idx_golden_qa_sources_qa_pair ON golden_qa_sources(qa_pair_id);
CREATE INDEX idx_golden_eval_results_dataset ON golden_eval_results(dataset_id);
CREATE INDEX idx_golden_eval_results_run ON golden_eval_results(eval_run_id);
