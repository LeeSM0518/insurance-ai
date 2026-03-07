ALTER TABLE data_sources ADD COLUMN extracted_text_path TEXT;

COMMENT ON COLUMN data_sources.extracted_text_path
    IS 'PDF에서 추출된 텍스트 파일 경로 (캐싱용)';
