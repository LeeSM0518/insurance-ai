-- 상품 목록
CREATE TABLE products (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    insurance_company_id     UUID         NOT NULL REFERENCES insurance_companies(id),
    name                     VARCHAR(200) NOT NULL,
    category                 VARCHAR(50)  NOT NULL,
    subcategory              VARCHAR(50),
    renewal_type             VARCHAR(20),
    sales_channel            VARCHAR(50),
    min_premium              BIGINT,
    applied_interest_rate    DECIMAL(5,2),
    declared_interest_rate   DECIMAL(5,2),
    guaranteed_interest_rate DECIMAL(5,2),
    maturity_amount          BIGINT,
    yield_rate               DECIMAL(5,2),
    is_active                BOOLEAN      NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (insurance_company_id, name, category)
);

COMMENT ON TABLE products IS '보험 상품 목록 (비교공시 기준)';
COMMENT ON COLUMN products.id IS '상품 고유 ID';
COMMENT ON COLUMN products.insurance_company_id IS '보험사 ID (FK → insurance_companies)';
COMMENT ON COLUMN products.name IS '상품명 (예: 무배당 삼성 종신보험)';
COMMENT ON COLUMN products.category IS '상품 대분류 (보장성, 저축성, 변액보장성, 변액저축성, 연금저축, 실손의료)';
COMMENT ON COLUMN products.subcategory IS '상품 소분류 (암보험, 종신보험, 질병보험 등)';
COMMENT ON COLUMN products.renewal_type IS '갱신 유형 (갱신형 / 비갱신형)';
COMMENT ON COLUMN products.sales_channel IS '판매채널 (대면/TM/CM 등)';
COMMENT ON COLUMN products.min_premium IS '최저가입보험료 (원)';
COMMENT ON COLUMN products.applied_interest_rate IS '보장부분 적용이율/예정이율 (%)';
COMMENT ON COLUMN products.declared_interest_rate IS '공시이율 (%, 저축성/변액보험)';
COMMENT ON COLUMN products.guaranteed_interest_rate IS '적립부분 적용이율/최저보증이율 (%, 저축성/변액보험)';
COMMENT ON COLUMN products.maturity_amount IS '만기보험금 (원, 저축성보험)';
COMMENT ON COLUMN products.yield_rate IS '수익률 (%, 저축성/변액보험)';
COMMENT ON COLUMN products.is_active IS '판매 중 여부 (단종 시 false)';
COMMENT ON COLUMN products.created_at IS '생성일시 (UTC)';
COMMENT ON COLUMN products.updated_at IS '수정일시 (UTC)';

-- 데이터 출처
CREATE TABLE data_sources (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type VARCHAR(20)  NOT NULL,
    source_url  TEXT,
    file_path   TEXT,
    crawled_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    checksum    VARCHAR(64)
);

COMMENT ON TABLE data_sources IS '데이터 출처 (크롤링 원본 추적)';
COMMENT ON COLUMN data_sources.id IS '출처 고유 ID';
COMMENT ON COLUMN data_sources.source_type IS '출처 유형 (ASSOCIATION: 협회 비교공시, PRODUCT_SUMMARY: 상품요약서 PDF, FULL_POLICY: 약관 원문 PDF)';
COMMENT ON COLUMN data_sources.source_url IS '원본 URL';
COMMENT ON COLUMN data_sources.file_path IS '저장된 파일 경로 (PDF 등)';
COMMENT ON COLUMN data_sources.crawled_at IS '수집 시점 (UTC)';
COMMENT ON COLUMN data_sources.checksum IS '변경 감지용 SHA-256 해시';

-- 약관 조문
CREATE TABLE policy_articles (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id     UUID         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    data_source_id UUID         REFERENCES data_sources(id),
    article_number VARCHAR(20),
    article_title  VARCHAR(200),
    content        TEXT         NOT NULL,
    content_type   VARCHAR(30)  NOT NULL,
    section_type   VARCHAR(20),
    effective_date DATE,
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE policy_articles IS '약관 조문 (비교공시 텍스트 + 상품요약서 PDF 추출 + 약관 원문)';
COMMENT ON COLUMN policy_articles.id IS '조문 고유 ID';
COMMENT ON COLUMN policy_articles.product_id IS '상품 ID (FK → products)';
COMMENT ON COLUMN policy_articles.data_source_id IS '데이터 출처 ID (FK → data_sources)';
COMMENT ON COLUMN policy_articles.article_number IS '조문 번호 (제N조, 비교공시 데이터는 null)';
COMMENT ON COLUMN policy_articles.article_title IS '조문 제목 (비교공시 데이터는 null)';
COMMENT ON COLUMN policy_articles.content IS '조문 내용 (본문 텍스트)';
COMMENT ON COLUMN policy_articles.content_type IS '내용 유형 (COVERAGE, EXEMPTION, REDUCTION, QUALIFICATION, SPECIAL_TERMS, QNA, COVERAGE_DETAIL, CLAIM_DOCUMENTS, ARTICLE)';
COMMENT ON COLUMN policy_articles.section_type IS '구분 (주계약 / 특약)';
COMMENT ON COLUMN policy_articles.effective_date IS '적용일자';
COMMENT ON COLUMN policy_articles.is_active IS '활성 여부 (버전 관리: 이전 버전은 false)';
COMMENT ON COLUMN policy_articles.created_at IS '생성일시 (UTC)';
COMMENT ON COLUMN policy_articles.updated_at IS '수정일시 (UTC)';

-- 벡터 임베딩
CREATE TABLE policy_embeddings (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_article_id UUID         NOT NULL REFERENCES policy_articles(id) ON DELETE CASCADE,
    embedding         vector(1536) NOT NULL,
    embedding_model   VARCHAR(50)  NOT NULL DEFAULT 'text-embedding-3-small',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE policy_embeddings IS '약관 조문 벡터 임베딩 (pgvector)';
COMMENT ON COLUMN policy_embeddings.id IS '임베딩 고유 ID';
COMMENT ON COLUMN policy_embeddings.policy_article_id IS '약관 조문 ID (FK → policy_articles)';
COMMENT ON COLUMN policy_embeddings.embedding IS '임베딩 벡터 (1536차원, cosine similarity 검색용)';
COMMENT ON COLUMN policy_embeddings.embedding_model IS '임베딩 모델명 (모델 변경 시 재임베딩 추적용)';
COMMENT ON COLUMN policy_embeddings.created_at IS '생성일시 (UTC)';
