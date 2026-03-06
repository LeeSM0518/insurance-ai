"""
상품요약서 PDF Vision API 추출 테스트
- 미래에셋 (텍스트 추출 불가 PDF) vs AXA (텍스트 추출 가능 PDF)
- Claude API로 구조화된 데이터 추출 테스트
"""
import anthropic
import base64
import json
import time

client = anthropic.Anthropic()

EXTRACTION_PROMPT = """이 보험 상품요약서 PDF 페이지에서 다음 정보를 JSON 형태로 추출해주세요.
해당 페이지에 없는 항목은 null로 표시하세요.

추출할 항목:
1. product_name: 상품명
2. insurance_company: 보험사명
3. q_and_a: 문답식 해설 내용 (배열, 각 항목은 {question, answer})
4. entry_qualification: 가입자격 정보
   - min_age, max_age: 최소/최대 가입나이
   - insurance_period: 보험기간
   - payment_period: 납입기간
5. coverage: 보장내용 (배열, 각 항목은 {name, category, trigger, amount, exemption_period, reduction_period})
6. exemptions: 면책사항/감액기간 (배열)
7. premiums: 보험료 산출기초
8. surrender_value: 해약환급금 정보
9. price_index: 보험가격지수

JSON만 출력하세요. 설명 없이 순수 JSON만 반환하세요."""


def extract_from_images(image_paths, label):
    """이미지 파일들로부터 Claude Vision API로 추출"""
    content = []
    for path in image_paths:
        with open(path, "rb") as f:
            img_data = base64.standard_b64encode(f.read()).decode("utf-8")
        content.append({
            "type": "image",
            "source": {"type": "base64", "media_type": "image/png", "data": img_data}
        })
    content.append({"type": "text", "text": EXTRACTION_PROMPT})

    start = time.time()
    response = client.messages.create(
        model="claude-sonnet-4-20250514",
        max_tokens=4096,
        messages=[{"role": "user", "content": content}]
    )
    elapsed = time.time() - start

    print(f"\n{'='*60}")
    print(f"[{label}] Vision API 추출 결과")
    print(f"{'='*60}")
    print(f"소요 시간: {elapsed:.1f}초")
    print(f"입력 토큰: {response.usage.input_tokens}")
    print(f"출력 토큰: {response.usage.output_tokens}")
    print(f"\n추출 결과:")
    print(response.content[0].text)
    return response


def extract_from_pdf(pdf_path, label):
    """PDF 파일을 직접 Claude API에 전달"""
    with open(pdf_path, "rb") as f:
        pdf_data = base64.standard_b64encode(f.read()).decode("utf-8")

    start = time.time()
    response = client.messages.create(
        model="claude-sonnet-4-20250514",
        max_tokens=4096,
        messages=[{"role": "user", "content": [
            {
                "type": "document",
                "source": {"type": "base64", "media_type": "application/pdf", "data": pdf_data}
            },
            {"type": "text", "text": EXTRACTION_PROMPT}
        ]}]
    )
    elapsed = time.time() - start

    print(f"\n{'='*60}")
    print(f"[{label}] PDF 직접 입력 추출 결과")
    print(f"{'='*60}")
    print(f"소요 시간: {elapsed:.1f}초")
    print(f"입력 토큰: {response.usage.input_tokens}")
    print(f"출력 토큰: {response.usage.output_tokens}")
    print(f"\n추출 결과:")
    print(response.content[0].text)
    return response


if __name__ == "__main__":
    base = "/Users/sangminlee/me/insurance-ai/.playwright-mcp"

    # 테스트 1: 미래에셋 (텍스트 추출 불가) - 이미지 방식
    print("\n" + "=" * 60)
    print("테스트 1: 미래에셋 - 이미지 변환 -> Vision API")
    print("=" * 60)
    extract_from_images(
        [f"{base}/mirae_page_1.png", f"{base}/mirae_page_2.png"],
        "미래에셋-이미지"
    )

    # 테스트 2: AXA (텍스트 추출 가능) - 이미지 방식
    print("\n" + "=" * 60)
    print("테스트 2: AXA - 이미지 변환 -> Vision API")
    print("=" * 60)
    extract_from_images(
        [f"{base}/axa_page_1.png"],
        "AXA-이미지"
    )

    # 테스트 3: AXA - PDF 직접 입력
    print("\n" + "=" * 60)
    print("테스트 3: AXA - PDF 직접 입력")
    print("=" * 60)
    extract_from_pdf(
        f"{base}/-무-AXA간편집중암보장보험-갱신형-2601-상품요약서.pdf",
        "AXA-PDF직접"
    )
