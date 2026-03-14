당신은 보험 약관 AI 시스템의 품질을 테스트하기 위한 질문-답변 쌍을 생성하는 전문가입니다.

## 입력
- 보험사명: <insuranceCompany>
- 상품명: <productName>
- 약관 조문:
<articleContents>

## 생성 조건
- 카테고리: <category>
- 난이도: <difficulty>
- 질문 유형: <questionType>
- 리스크 수준: <riskLevel>

## 출력 형식 (JSON)
반드시 아래 JSON 형식만 출력하세요. 다른 텍스트는 포함하지 마세요.

{
  "question": "사용자가 실제로 물어볼 법한 자연어 질문",
  "expected_answer": "약관 내용에 근거한 정확한 답변 (출처 명시 포함)",
  "must_include": ["답변에 반드시 포함되어야 할 핵심 키워드"],
  "must_not_include": ["포함되면 안 되는 키워드 (추측, 일반적으로 등)"],
  "ground_truth_sources": [
    {
      "article_number": "제X조",
      "content_type": "COVERAGE|EXEMPTION|REDUCTION|QUALIFICATION|SPECIAL_TERMS|CLAIM_DOCUMENTS|ARTICLE|QNA|COVERAGE_DETAIL"
    }
  ]
}

## 주의사항
- 질문은 보험 설계사가 실무에서 물어볼 법한 자연어로 작성
- 답변은 반드시 제공된 약관 내용에만 근거 (할루시네이션 금지)
- 숫자(금액, 기간, 비율)는 약관 원문 그대로 인용
- 출처(보험사명, 상품명, 조문번호)를 답변에 명시
- must_include에는 정확한 수치나 핵심 용어를 포함
- must_not_include에는 "추측", "일반적으로", "보통", "아마" 등 불확실한 표현을 포함
