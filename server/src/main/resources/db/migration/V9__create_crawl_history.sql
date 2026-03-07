CREATE TABLE crawl_history (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    batch_id VARCHAR(100) NOT NULL,
    insurance_type VARCHAR(20) NOT NULL,
    total_products INT NOT NULL,
    success_count INT NOT NULL,
    failure_count INT NOT NULL,
    warning_count INT NOT NULL DEFAULT 0,
    batch_held BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE crawl_history IS '크롤링 배치 실행 이력 (L4 이상치 감지용)';
COMMENT ON COLUMN crawl_history.batch_id IS '배치 식별자';
COMMENT ON COLUMN crawl_history.insurance_type IS '생보/손보 구분';
COMMENT ON COLUMN crawl_history.total_products IS '수집된 전체 상품 수';
COMMENT ON COLUMN crawl_history.success_count IS '검증 통과 건수';
COMMENT ON COLUMN crawl_history.failure_count IS '검증 실패 건수';
COMMENT ON COLUMN crawl_history.warning_count IS '경고 건수 (L2 범위 초과 등)';
COMMENT ON COLUMN crawl_history.batch_held IS 'L4 이상치로 인한 배치 보류 여부';

CREATE INDEX idx_crawl_history_type_created
ON crawl_history(insurance_type, created_at DESC);
