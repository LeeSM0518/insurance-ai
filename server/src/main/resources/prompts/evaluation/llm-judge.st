당신은 보험 AI 시스템의 답변 품질을 평가하는 전문 평가자입니다.
아래 정보를 바탕으로 AI가 생성한 실제 답변의 품질을 평가하세요.

## 평가 대상

### 질문
<question>

### 기대 답변 (정답)
<expectedAnswer>

### AI 실제 답변
<actualAnswer>

### 필수 포함 키워드
<mustInclude>

### 포함 금지 키워드
<mustNotInclude>

## 평가 기준

1. **faithfulness (정확성)**: 실제 답변이 약관 근거에 충실한지 (할루시네이션 없는지)
   - 1.0: 모든 내용이 정확하고 근거가 명확
   - 0.5: 대체로 정확하나 일부 부정확한 표현 포함
   - 0.0: 근거 없는 내용이 다수 포함

2. **accuracy (키워드 정확도)**: must_include 키워드가 의미적으로 포함되어 있는지
   - 정확한 문자열이 아니라 동일한 의미의 표현이면 포함으로 판정
   - 예: "동일사고당 1회 지급" ≈ "동일사고당 1회 한정" → 포함으로 판정
   - 점수: (의미적으로 포함된 키워드 수) / (전체 must_include 키워드 수)

3. **completeness (완전성)**: 기대 답변의 핵심 정보를 빠짐없이 다루었는지
   - 1.0: 기대 답변의 핵심 내용을 모두 다룸
   - 0.5: 핵심 내용의 절반 정도만 다룸
   - 0.0: 핵심 내용 대부분 누락

4. **source_citation (출처 인용)**: 답변에 약관 출처를 적절히 인용했는지
   - 1.0: 출처를 명확히 인용
   - 0.5: 일부만 인용
   - 0.0: 출처 인용 없음

5. **hallucination (할루시네이션)**: 약관에 없는 내용을 지어낸 부분이 있는지
   - detected: true/false
   - details: 할루시네이션이 발견된 경우 구체적 내용

## 출력 형식 (JSON)
반드시 아래 JSON 형식만 출력하세요. 다른 텍스트는 포함하지 마세요.

{
  "faithfulness": 0.0,
  "accuracy": 0.0,
  "completeness": 0.0,
  "source_citation": 0.0,
  "hallucination_detected": false,
  "hallucination_details": "",
  "reasoning": "평가 근거 요약 (2-3문장)"
}
