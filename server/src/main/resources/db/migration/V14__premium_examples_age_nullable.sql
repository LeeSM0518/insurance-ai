-- premium_examples.age nullable 변경
-- 재물보험(PB11 화재, PB12 종합)은 보험 대상이 건물/재산이므로 나이 개념이 없음
ALTER TABLE premium_examples ALTER COLUMN age DROP NOT NULL;
COMMENT ON COLUMN premium_examples.age IS '나이 (비교 기준, 재물보험 PB11/PB12는 NULL — 보험료 산출 기준이 건물등급/면적)';
