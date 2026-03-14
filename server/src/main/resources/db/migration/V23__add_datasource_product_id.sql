-- data_sources에 product_id 직접 연결 컬럼 추가
ALTER TABLE data_sources ADD COLUMN product_id UUID REFERENCES products(id);

COMMENT ON COLUMN data_sources.product_id IS '연결된 상품 ID (PRODUCT_SUMMARY PDF의 상품 직접 연결용)';

-- 조회 성능을 위한 인덱스
CREATE INDEX idx_data_sources_product_summary
    ON data_sources (product_id, source_type)
    WHERE source_type = 'PRODUCT_SUMMARY' AND file_path IS NOT NULL;

-- 기존 PRODUCT_SUMMARY data_sources에 product_id 역채움
UPDATE data_sources ds
SET product_id = (
    SELECT DISTINCT pa.product_id
    FROM policy_articles pa
    WHERE pa.data_source_id = ds.id
    LIMIT 1
)
WHERE ds.source_type = 'PRODUCT_SUMMARY'
  AND ds.product_id IS NULL;
