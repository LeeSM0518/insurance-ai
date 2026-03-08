-- insurance_companies 테이블에 aliases 컬럼 추가
ALTER TABLE insurance_companies ADD COLUMN aliases TEXT[] DEFAULT '{}';
COMMENT ON COLUMN insurance_companies.aliases IS '보험사 약칭 목록 (예: 한화손보, KB손보)';

-- 미등록 손보사 INSERT
INSERT INTO insurance_companies (name, type, aliases)
VALUES ('라이나손해보험', 'NON_LIFE', ARRAY['라이나손보']);
INSERT INTO insurance_companies (name, type, aliases)
VALUES ('신한EZ손해보험', 'NON_LIFE', ARRAY['신한EZ손보', '신한EZ손해보험']);

-- 기존 손보사 aliases 업데이트
UPDATE insurance_companies SET aliases = ARRAY['한화손보'] WHERE name = '한화손해보험';
UPDATE insurance_companies SET aliases = ARRAY['KB손보'] WHERE name = 'KB손해보험';
UPDATE insurance_companies SET aliases = ARRAY['DB손보'] WHERE name = 'DB손해보험';
UPDATE insurance_companies SET aliases = ARRAY['롯데손보'] WHERE name = '롯데손해보험';
UPDATE insurance_companies SET aliases = ARRAY['농협손보', 'NH농협손해보험'] WHERE name = '농협손해보험';
UPDATE insurance_companies SET aliases = ARRAY['하나손보'] WHERE name = '하나손해보험';
UPDATE insurance_companies SET aliases = ARRAY['AXA손보'] WHERE name = 'AXA손해보험';
