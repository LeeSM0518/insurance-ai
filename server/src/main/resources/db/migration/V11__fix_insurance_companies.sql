-- 보험사 명칭 수정 (실제 비교공시 데이터 기준)
UPDATE insurance_companies SET name = 'KB라이프생명' WHERE name = 'KB라이프' AND type = 'LIFE';
UPDATE insurance_companies SET name = '메트라이프생명' WHERE name = '메트라이프' AND type = 'LIFE';
UPDATE insurance_companies SET name = '신한라이프생명' WHERE name = '신한라이프' AND type = 'LIFE';
UPDATE insurance_companies SET name = '처브라이프생명' WHERE name = '처브라이프' AND type = 'LIFE';

-- 누락 보험사 추가
INSERT INTO insurance_companies (name, type, association_code) VALUES
('IBK연금보험',         'LIFE', 'ibk-pension'),
('교보라이프플래닛생명', 'LIFE', 'kyobo-planet');

-- 보험사 수 주석 업데이트
COMMENT ON TABLE insurance_companies IS '보험사 목록 (생보 22개사 + 손보 16개사)';
