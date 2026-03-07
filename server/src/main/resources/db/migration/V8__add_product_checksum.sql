ALTER TABLE products ADD COLUMN checksum VARCHAR(64);
COMMENT ON COLUMN products.checksum IS '상품 데이터 변경 감지용 SHA-256 해시';
