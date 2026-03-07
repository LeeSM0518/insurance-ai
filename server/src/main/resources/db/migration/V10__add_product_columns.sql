-- products 테이블 컬럼 추가
ALTER TABLE products ADD COLUMN surrender_type VARCHAR(50);
ALTER TABLE products ADD COLUMN product_type VARCHAR(50);
ALTER TABLE products ADD COLUMN sales_start_date DATE;
ALTER TABLE products ADD COLUMN is_universal BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE products ADD COLUMN notes TEXT;

COMMENT ON COLUMN products.surrender_type IS '해약환급금 유형 (일반/순수보장/무해지저해지/만기환급)';
COMMENT ON COLUMN products.product_type IS '상품 세부유형 (기본형/체증형/체감형 등)';
COMMENT ON COLUMN products.sales_start_date IS '판매개시일';
COMMENT ON COLUMN products.is_universal IS '유니버셜 여부';
COMMENT ON COLUMN products.notes IS '특이사항 원문 (비교 기준 조건 포함)';

-- surrender_values 테이블 컬럼 추가
ALTER TABLE surrender_values ADD COLUMN gender VARCHAR(10);
ALTER TABLE surrender_values ADD COLUMN policyholder_reserve BIGINT;

COMMENT ON COLUMN surrender_values.gender IS '성별 (남/여, 저축성 해약환급금 성별 분리)';
COMMENT ON COLUMN surrender_values.policyholder_reserve IS '계약자적립액 (원)';

-- surrender_values 기존 NOT NULL 제약 완화 (비교공시에서 일부 값 없을 수 있음)
ALTER TABLE surrender_values ALTER COLUMN premium_paid DROP NOT NULL;
ALTER TABLE surrender_values ALTER COLUMN surrender_rate DROP NOT NULL;

-- insurance_companies 테이블 컬럼 추가
ALTER TABLE insurance_companies ADD COLUMN phone VARCHAR(20);

COMMENT ON COLUMN insurance_companies.phone IS '보험사 대표번호';
