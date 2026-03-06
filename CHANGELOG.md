# CHANGELOG

## 기획 문서

| 문서 | 상태 | 비고 |
|------|------|------|
| `docs/planning/README.md` | ✅ 완료 | 프로젝트 개요, MVP 범위, 로드맵 |
| `docs/planning/01-data-pipeline.md` | ✅ 완료 | 데이터 수집, 파싱, 임베딩, DB 스키마 |
| `docs/planning/02-search-chat.md` | ✅ 완료 | 검색, AI 질의응답, API 설계 |
| `docs/planning/03-screens-ui.md` | ✅ 완료 | 화면 구성, 와이어프레임 |

## 구현 현황

### 인프라

| 항목 | 상태 |
|------|------|
| 프로젝트 구조 (.claude 설정) | ✅ 완료 |
| Docker Compose (PostgreSQL + pgvector) | ❌ 미구현 |

### 백엔드

| 항목 | 상태 |
|------|------|
| Spring Boot 프로젝트 초기 설정 | ❌ 미구현 |
| DB 스키마 (Flyway 마이그레이션) | ❌ 미구현 |
| Kakao 소셜 로그인 | ❌ 미구현 |
| 협회 비교공시 크롤러 (생보) | ❌ 미구현 |
| 협회 비교공시 크롤러 (손보) | ❌ 미구현 |
| 데이터 정규화 + 임베딩 파이프라인 | ❌ 미구현 |
| 약관 검색 API | ❌ 미구현 |
| AI 질의응답 API (RAG + SSE) | ❌ 미구현 |
| 채팅 세션/메시지 관리 API | ❌ 미구현 |

### 프론트엔드

| 항목 | 상태 |
|------|------|
| React + Vite 프로젝트 초기 설정 | ❌ 미구현 |
| 로그인 화면 | ❌ 미구현 |
| 메인/검색 화면 | ❌ 미구현 |
| 검색 결과 화면 | ❌ 미구현 |
| AI 채팅 화면 | ❌ 미구현 |
| 약관 상세 화면 | ❌ 미구현 |

## 변경 이력

### 2026-03-06

- 기획서 4개 문서 작성 완료
  - README.md: 프로젝트 개요 및 MVP 범위 확정
  - 01-data-pipeline.md: 2-Layer 데이터 전략, DB 스키마 설계
  - 02-search-chat.md: 하이브리드 검색, RAG, API 설계
  - 03-screens-ui.md: 5개 화면 구성 및 와이어프레임
