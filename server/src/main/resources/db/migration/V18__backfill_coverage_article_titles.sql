-- COVERAGE 타입 article_title 백필
-- content 필드의 [담보명] 패턴에서 제목을 추출하여 article_title에 저장

-- 1단계: 단순 패턴 (중첩 대괄호 없는 경우)
UPDATE policy_articles
SET article_title = substring(content FROM '^\[([^\[\]]+)\]')
WHERE content_type = 'COVERAGE'
  AND article_title IS NULL
  AND content ~ '^\[[^\[\]]+\]';

-- 2단계: 중첩 대괄호 패턴 (예: [납입면제특약W 무배당 [암 납입면제형]])
-- 가장 바깥 대괄호 안의 전체 텍스트를 추출
UPDATE policy_articles
SET article_title = substring(content FROM '^\[(.+?)\] 지급사유:')
WHERE content_type = 'COVERAGE'
  AND article_title IS NULL
  AND content ~ '^\[.+\] 지급사유:';
