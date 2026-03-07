-- 상품 특수 속성 (key-value)
CREATE TABLE product_features (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    key        VARCHAR(100) NOT NULL,
    value      TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_features_product ON product_features(product_id);
CREATE INDEX idx_product_features_key ON product_features(key);

COMMENT ON TABLE product_features IS '상품 특수 속성 (카테고리별 선택적 속성을 key-value로 저장)';
COMMENT ON COLUMN product_features.id IS '속성 고유 ID';
COMMENT ON COLUMN product_features.product_id IS '상품 ID (FK → products)';
COMMENT ON COLUMN product_features.key IS '속성명 (예: 경도치매보장여부, 최저보증사망, 상품특징)';
COMMENT ON COLUMN product_features.value IS '속성값';
COMMENT ON COLUMN product_features.created_at IS '생성일시 (UTC)';

-- 수익률 시나리오 (저축성/변액)
CREATE TABLE product_yield_scenarios (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    scenario_type   VARCHAR(50)   NOT NULL,
    scenario_label  VARCHAR(100)  NOT NULL,
    yield_rate      DECIMAL(8,4)  NOT NULL,
    gender          VARCHAR(10),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_yield_scenarios_product ON product_yield_scenarios(product_id);

COMMENT ON TABLE product_yield_scenarios IS '수익률 시나리오 (저축성/변액 이율별 예상수익률)';
COMMENT ON COLUMN product_yield_scenarios.id IS '시나리오 고유 ID';
COMMENT ON COLUMN product_yield_scenarios.product_id IS '상품 ID (FK → products)';
COMMENT ON COLUMN product_yield_scenarios.scenario_type IS '시나리오 유형 (INTEREST_RATE: 이율별, INVESTMENT: 투자수익률별)';
COMMENT ON COLUMN product_yield_scenarios.scenario_label IS '시나리오 라벨 (예: 최저보증, 현재공시, 평균공시, -1%, 평균, 평균x1.5)';
COMMENT ON COLUMN product_yield_scenarios.yield_rate IS '예상수익률 (%)';
COMMENT ON COLUMN product_yield_scenarios.gender IS '성별 (남/여, null이면 공통)';
COMMENT ON COLUMN product_yield_scenarios.created_at IS '생성일시 (UTC)';

-- products 테이블에 추가 컬럼
ALTER TABLE products ADD COLUMN expense_ratio DECIMAL(5,2);
ALTER TABLE products ADD COLUMN disclosure_url TEXT;

COMMENT ON COLUMN products.expense_ratio IS '사업비율 (%, 저축성 보험)';
COMMENT ON COLUMN products.disclosure_url IS '상품요약서/공시 URL';
