-- sectionType에 MAIN/RIDER 외의 값이 저장된 데이터를 정규화
UPDATE policy_articles
SET section_type = 'MAIN'
WHERE section_type IS NOT NULL
  AND section_type NOT IN ('MAIN', 'RIDER');

-- NULL인 경우도 MAIN으로 기본값 설정
UPDATE policy_articles
SET section_type = 'MAIN'
WHERE section_type IS NULL;
