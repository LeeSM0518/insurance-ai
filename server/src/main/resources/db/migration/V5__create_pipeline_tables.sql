-- 크롤링 스테이징 (WAP 패턴: Write → Audit → Publish)
CREATE TABLE crawl_staging (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id          VARCHAR(50) NOT NULL,
    entity_type       VARCHAR(30) NOT NULL,
    entity_data       JSONB       NOT NULL,
    validation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    validation_errors TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_crawl_staging_batch ON crawl_staging(batch_id);
CREATE INDEX idx_crawl_staging_status ON crawl_staging(validation_status);

COMMENT ON TABLE crawl_staging IS '크롤링 스테이징 영역 (WAP 패턴: 검증 후 본 테이블 반영)';
COMMENT ON COLUMN crawl_staging.id IS '스테이징 레코드 고유 ID';
COMMENT ON COLUMN crawl_staging.batch_id IS '배치 실행 ID (같은 크롤링 세션의 데이터 그룹핑)';
COMMENT ON COLUMN crawl_staging.entity_type IS '엔티티 유형 (PRODUCT, ARTICLE, PREMIUM, INDEX, METRIC, SURRENDER_VALUE)';
COMMENT ON COLUMN crawl_staging.entity_data IS '원본 크롤링 데이터 (JSON)';
COMMENT ON COLUMN crawl_staging.validation_status IS '검증 상태 (PENDING: 대기, PASSED: 통과, FAILED: 실패)';
COMMENT ON COLUMN crawl_staging.validation_errors IS '검증 실패 사유 (L1~L4 검증 결과)';
COMMENT ON COLUMN crawl_staging.created_at IS '생성일시 (UTC)';
