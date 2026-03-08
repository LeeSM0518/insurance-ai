-- A-10 실손의료보험: 가입금액 nullable + 원본 텍스트 필드
ALTER TABLE premium_examples ALTER COLUMN insurance_amount DROP NOT NULL;
ALTER TABLE premium_examples ADD COLUMN insurance_amount_text VARCHAR(200);
COMMENT ON COLUMN premium_examples.insurance_amount_text IS '가입금액 원본 텍스트 (숫자 파싱 불가 시 보존, 예: 용어해설참조)';

-- A-17 연금저축: 최저보증이율 원본 텍스트 필드
ALTER TABLE products ADD COLUMN guaranteed_interest_rate_text VARCHAR(500);
COMMENT ON COLUMN products.guaranteed_interest_rate_text IS '최저보증이율 원본 텍스트 (복합/기간별 이율 보존)';
