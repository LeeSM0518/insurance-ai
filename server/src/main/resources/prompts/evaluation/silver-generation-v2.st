당신은 보험설계사 교육 전문가입니다.
아래 보험 약관 원문을 읽고, 보험설계사가 고객 상담 중 실제로 물을 법한 질문과
약관에 근거한 정확한 답변을 생성하세요.

## 보험 정보
- 보험사: <insuranceCompany>
- 상품명: <productName>

## 약관 원문
<articleContents>

## 생성 조건
- 실무 카테고리: <practiceCategory>
- 질문 유형: <questionType>
- 난이도: <difficulty>

## 질문 작성 규칙
1. 보험설계사가 고객 상담 중 자연스럽게 묻는 말투로 작성
2. "제N조에 따르면..." 같은 조문 참조 질문 금지
3. 구체적인 상황을 포함 (예: "고객이 ○○ 상황인데...")
4. 한 질문에 한 가지 핵심 궁금증만 포함

## 답변 작성 규칙
1. 설계사가 고객에게 설명하듯 자연스럽고 명확하게 작성
2. 반드시 약관 원문의 내용만 근거로 사용 (할루시네이션 금지)
3. 답변 말미에 근거 조문을 명시 (예: "약관 제5조에 따르면...")
4. 숫자(금액, 기간, 비율)는 원문 그대로 인용
5. 면책/감액 조건이 있으면 반드시 함께 언급
6. 답변 길이: 최소 3문장, 최대 10문장

## 출력 형식 (JSON)
반드시 아래 JSON 형식만 출력하세요. 다른 텍스트는 포함하지 마세요.

{
  "question": "설계사 관점의 자연스러운 질문",
  "expected_answer": "약관 근거 답변 (출처 조문 명시)",
  "must_include": ["답변에 반드시 포함되어야 할 핵심 키워드 2-4개"],
  "must_not_include": ["추측", "일반적으로", "보통", "아마"],
  "practice_scenario": "이 질문이 나올 수 있는 실제 상담 상황 한 줄 설명",
  "ground_truth_sources": [
    {
      "article_number": "제X조",
      "article_title": "조문 제목",
      "content_type": "COVERAGE|EXEMPTION|REDUCTION|QUALIFICATION|SPECIAL_TERMS|CLAIM_DOCUMENTS|ARTICLE|QNA|COVERAGE_DETAIL"
    }
  ]
}
