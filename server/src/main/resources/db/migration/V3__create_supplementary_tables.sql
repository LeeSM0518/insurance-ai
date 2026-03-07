-- 보험료 예시
CREATE TABLE premium_examples (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id       UUID         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    gender           VARCHAR(10)  NOT NULL,
    age              INT          NOT NULL,
    insurance_period VARCHAR(50)  NOT NULL,
    payment_period   VARCHAR(50)  NOT NULL,
    insurance_amount BIGINT       NOT NULL,
    premium          BIGINT       NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE premium_examples IS '보험료 예시 (비교공시 기준 조건별 월 보험료)';
COMMENT ON COLUMN premium_examples.id IS '보험료 예시 고유 ID';
COMMENT ON COLUMN premium_examples.product_id IS '상품 ID (FK → products)';
COMMENT ON COLUMN premium_examples.gender IS '성별 (남 / 여)';
COMMENT ON COLUMN premium_examples.age IS '나이 (비교 기준)';
COMMENT ON COLUMN premium_examples.insurance_period IS '보험기간 (예: 80세만기, 20년만기)';
COMMENT ON COLUMN premium_examples.payment_period IS '납입기간 (예: 20년납, 전기납)';
COMMENT ON COLUMN premium_examples.insurance_amount IS '보험가입금액 (원)';
COMMENT ON COLUMN premium_examples.premium IS '월 보험료 (원)';
COMMENT ON COLUMN premium_examples.created_at IS '생성일시 (UTC)';
COMMENT ON COLUMN premium_examples.updated_at IS '수정일시 (UTC)';

-- 상품 지수
CREATE TABLE product_indices (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    index_type    VARCHAR(30)   NOT NULL,
    gender        VARCHAR(10),
    coverage_name VARCHAR(200),
    index_value   DECIMAL(10,4) NOT NULL,
    base_date     DATE          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE product_indices IS '상품 지수 (보험가격지수, 보장범위지수 등)';
COMMENT ON COLUMN product_indices.id IS '상품 지수 고유 ID';
COMMENT ON COLUMN product_indices.product_id IS '상품 ID (FK → products)';
COMMENT ON COLUMN product_indices.index_type IS '지수 유형 (PRICE: 보험가격지수, COVERAGE: 보장범위지수, CONTRACT_COST: 계약체결비용지수, EXTRA_PREMIUM: 부가보험료지수)';
COMMENT ON COLUMN product_indices.gender IS '성별 (남/여, 성별 구분 없는 지수는 null)';
COMMENT ON COLUMN product_indices.coverage_name IS '담보명 (담보별 지수인 경우, 예: 보장범위지수)';
COMMENT ON COLUMN product_indices.index_value IS '지수 값';
COMMENT ON COLUMN product_indices.base_date IS '기준일';
COMMENT ON COLUMN product_indices.created_at IS '생성일시 (UTC)';

-- 상품 시계열 지표
CREATE TABLE product_metrics (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id     UUID          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    metric_type    VARCHAR(30)   NOT NULL,
    metric_value   DECIMAL(10,4) NOT NULL,
    reference_date DATE          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE product_metrics IS '상품 시계열 지표 (보험료 인상률, 손해율, 보전율 등)';
COMMENT ON COLUMN product_metrics.id IS '지표 고유 ID';
COMMENT ON COLUMN product_metrics.product_id IS '상품 ID (FK → products)';
COMMENT ON COLUMN product_metrics.metric_type IS '지표 유형 (PREMIUM_INCREASE_RATE, LOSS_RATIO, RENEWAL_PREMIUM, RETENTION_RATE, PREMIUM_CHANGE)';
COMMENT ON COLUMN product_metrics.metric_value IS '지표 값';
COMMENT ON COLUMN product_metrics.reference_date IS '기준일 (연도 단위면 해당 연도 1월 1일)';
COMMENT ON COLUMN product_metrics.created_at IS '생성일시 (UTC)';

-- 해약환급금 예시
CREATE TABLE surrender_values (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    elapsed_months  INT           NOT NULL,
    premium_paid    BIGINT        NOT NULL,
    surrender_value BIGINT        NOT NULL,
    surrender_rate  DECIMAL(5,2)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE surrender_values IS '해약환급금 예시 (상품요약서 PDF에서 추출)';
COMMENT ON COLUMN surrender_values.id IS '해약환급금 고유 ID';
COMMENT ON COLUMN surrender_values.product_id IS '상품 ID (FK → products)';
COMMENT ON COLUMN surrender_values.elapsed_months IS '경과 개월 수';
COMMENT ON COLUMN surrender_values.premium_paid IS '납입 보험료 누계 (원)';
COMMENT ON COLUMN surrender_values.surrender_value IS '해약환급금 (원)';
COMMENT ON COLUMN surrender_values.surrender_rate IS '환급률 (%)';
COMMENT ON COLUMN surrender_values.created_at IS '생성일시 (UTC)';
