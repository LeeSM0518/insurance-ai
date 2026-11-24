# Insurance AI

보험 설계사가 여러 보험사 약관을 한곳에서 검색하고, 근거 조문이 출처로 붙은 AI 답을 받는 웹 서비스입니다.

## 만든 이유

- 보험사가 만든 AI 는 자사 약관만 다루는데, 여러 보험사 상품을 파는 설계사는 보험사를 넘나들며 약관을 비교해야 합니다.
- 협회 공시실은 약관 PDF 를 내려받게만 하고 내용을 검색할 수 없습니다.

지인 보험 설계사에게서 "보험 약관에 대해 답해 주는 AI 가 있으면 좋겠다"는 이야기를 듣고 시작했습니다.

## 상태

- 2026-03 혼자 개발. 첫 커밋 3일 뒤(2026-03-10) 운영 배포, 보험 설계사 4명이 사용
- 지금은 운영하지 않습니다

## 구성

| 폴더 | 내용 |
|---|---|
| `server/` | Kotlin, Spring Boot(WebFlux, R2DBC), Spring AI, jOOQ, Flyway. 검색, 채팅, 약관 적재, 평가 |
| `frontend/` | React, TypeScript, Vite, Tailwind CSS |
| `api-client/` | 서버 OpenAPI 로 만든 TypeScript 클라이언트 |
| `pipeline/` | 약관 PDF 구조화 추출 실험 스크립트(Python). `ANTHROPIC_API_KEY` 환경변수 필요 |
| `db/` | PostgreSQL 과 pgvector 초기화 |

## 검색 구조

- 약관을 조문 단위로 적재하고, 스키마, 범위, 일관성, 이상치 네 단계로 검증합니다
- 키워드 검색과 벡터 검색(pgvector)의 점수를 0 ~ 1 로 맞춰 가중 결합하고, LLM Reranking 으로 근거 조문을 고릅니다
- 검색과 답변을 평가 데이터셋과 LLM Judge 로 따로 재서 약한 단계를 찾습니다

## 로컬 실행

```bash
cp .env.example .env   # ANTHROPIC_API_KEY, OPENAI_API_KEY, JWT 비밀값을 채운다
docker compose up -d   # PostgreSQL + pgvector
cd server && ./gradlew bootRun
cd frontend && npm install && npm run dev
```

## 공개 범위

기획 문서, 작업 기록, 운영 서버 정보, 수집한 약관 데이터는 이 저장소에 포함하지 않았습니다.

## 라이선스

All rights reserved. 포트폴리오로 공개한 저장소이며, 코드의 사용·복제·배포를 허락하지 않습니다.
