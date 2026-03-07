-- 벡터 검색 (HNSW)
CREATE INDEX idx_policy_embeddings_vector
ON policy_embeddings USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 한글 키워드 검색 (pg_bigm)
CREATE INDEX idx_policy_articles_content_bigm
ON policy_articles USING gin (content gin_bigm_ops);

CREATE INDEX idx_policy_articles_title_bigm
ON policy_articles USING gin (article_title gin_bigm_ops);

-- 상품 검색
CREATE INDEX idx_products_company ON products(insurance_company_id);
CREATE INDEX idx_products_category ON products(category, subcategory);
CREATE INDEX idx_products_renewal ON products(renewal_type);
CREATE INDEX idx_products_active ON products(is_active);
CREATE INDEX idx_products_company_category
ON products(insurance_company_id, category, is_active);

-- 약관 조문
CREATE INDEX idx_articles_product ON policy_articles(product_id);
CREATE INDEX idx_articles_content_type ON policy_articles(content_type);
CREATE INDEX idx_articles_product_type ON policy_articles(product_id, content_type);

-- 임베딩
CREATE INDEX idx_policy_embeddings_article ON policy_embeddings(policy_article_id);

-- 보험료 예시
CREATE INDEX idx_premium_examples_product ON premium_examples(product_id);

-- 상품 지수
CREATE INDEX idx_product_indices_product ON product_indices(product_id);
CREATE INDEX idx_product_indices_type ON product_indices(index_type);

-- 상품 시계열 지표
CREATE INDEX idx_product_metrics_product ON product_metrics(product_id);

-- 해약환급금
CREATE INDEX idx_surrender_values_product ON surrender_values(product_id);

-- 채팅
CREATE INDEX idx_chat_sessions_user_updated ON chat_sessions(user_id, updated_at DESC);
CREATE INDEX idx_chat_messages_session_created ON chat_messages(chat_session_id, created_at);
CREATE INDEX idx_message_sources_message ON chat_message_sources(chat_message_id);
CREATE INDEX idx_message_sources_article ON chat_message_sources(policy_article_id);
