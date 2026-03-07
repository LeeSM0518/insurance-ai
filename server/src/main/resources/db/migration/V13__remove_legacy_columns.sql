-- 레거시 컬럼 제거: maturity_amount, yield_rate
-- yield_rate → product_yield_scenarios 테이블로 대체 (V12)
-- maturity_amount → surrender_values에서 유추 가능
ALTER TABLE products DROP COLUMN IF EXISTS maturity_amount;
ALTER TABLE products DROP COLUMN IF EXISTS yield_rate;
