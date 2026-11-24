"""
Claude API를 사용한 PDF 구조화 추출 테스트
로컬 오픈소스 모델과 품질 비교 목적
"""

import json
import os
import sys
import time
from pathlib import Path

import anthropic
import fitz  # PyMuPDF

PROMPT_FILE = Path(__file__).parent / "prompts" / "structure_prompt.txt"
OUTPUT_DIR = Path(__file__).parent / "output"

# API 키 로드: ANTHROPIC_API_KEY 환경변수 우선, 없으면 로컬 키 파일
API_KEY_FILE = Path(__file__).parent.parent / ".claude" / "secrets" / "anthropic-api-key"
API_KEY = os.environ.get("ANTHROPIC_API_KEY") or API_KEY_FILE.read_text().strip()


def extract_text_from_pdf(pdf_path: str) -> str:
    """PyMuPDF로 PDF에서 텍스트 추출"""
    doc = fitz.open(pdf_path)
    pages = []
    for i, page in enumerate(doc):
        text = page.get_text()
        if text.strip():
            pages.append(f"=== 페이지 {i + 1} ===\n{text}")
    doc.close()
    return "\n\n".join(pages)


def call_claude_api(prompt: str, model: str = "claude-sonnet-4-20250514") -> dict:
    """Claude API 호출"""
    client = anthropic.Anthropic(api_key=API_KEY)

    print(f"  Claude API 호출 중 (모델: {model})...")
    start = time.time()

    response = client.messages.create(
        model=model,
        max_tokens=8192,
        temperature=0.1,
        messages=[{"role": "user", "content": prompt}],
    )

    elapsed = time.time() - start
    text = response.content[0].text
    input_tokens = response.usage.input_tokens
    output_tokens = response.usage.output_tokens

    print(f"  완료: {elapsed:.1f}초, 입력 {input_tokens} tok, 출력 {output_tokens} tok")

    # 비용 계산 (Sonnet 4: $3/1M input, $15/1M output)
    model_lower = model.lower()
    if "sonnet" in model_lower:
        cost = (input_tokens * 3 + output_tokens * 15) / 1_000_000
    elif "haiku" in model_lower:
        cost = (input_tokens * 0.80 + output_tokens * 4) / 1_000_000
    elif "opus" in model_lower:
        cost = (input_tokens * 15 + output_tokens * 75) / 1_000_000
    else:
        cost = 0
    print(f"  예상 비용: ${cost:.4f}")

    return {"text": text, "input_tokens": input_tokens, "output_tokens": output_tokens, "cost": cost}


def parse_json_from_response(response: str) -> dict:
    """LLM 응답에서 JSON 추출"""
    import re

    # ```json ... ``` 블록 찾기
    if "```json" in response:
        start = response.index("```json") + 7
        end = response.index("```", start)
        json_str = response[start:end].strip()
    elif "```" in response:
        start = response.index("```") + 3
        end = response.index("```", start)
        json_str = response[start:end].strip()
    else:
        start = response.find("{")
        end = response.rfind("}") + 1
        if start >= 0 and end > start:
            json_str = response[start:end]
        else:
            raise ValueError("JSON을 찾을 수 없습니다")

    return json.loads(json_str)


def run_pipeline(pdf_path: str, model: str = "claude-sonnet-4-20250514") -> dict:
    """Claude API 파이프라인 실행"""
    print(f"\n{'='*60}")
    print(f"PDF: {pdf_path}")
    print(f"모델: {model}")
    print(f"{'='*60}")

    # Step 1: 텍스트 추출
    print("\n[Step 1] PyMuPDF 텍스트 추출...")
    start = time.time()
    text = extract_text_from_pdf(pdf_path)
    print(f"  완료: {time.time() - start:.2f}초, {len(text)} chars")

    # Step 2: 프롬프트 구성
    prompt_template = PROMPT_FILE.read_text(encoding="utf-8")
    full_prompt = prompt_template + "\n\n" + text

    # Step 3: Claude API 호출
    print("\n[Step 2] Claude API 구조화 추출...")
    api_result = call_claude_api(full_prompt, model)

    # Step 4: JSON 파싱
    print("\n[Step 3] JSON 파싱...")
    try:
        result = parse_json_from_response(api_result["text"])
        articles = result.get("articles", [])
        sv = result.get("surrender_values", [])
        print(f"  articles: {len(articles)}건")
        print(f"  surrender_values: {len(sv)}건")

        # content_type 분포
        type_counts = {}
        for a in articles:
            ct = a.get("content_type", "UNKNOWN")
            type_counts[ct] = type_counts.get(ct, 0) + 1
        print(f"  content_type 분포: {type_counts}")

        result["_meta"] = {
            "model": model,
            "input_tokens": api_result["input_tokens"],
            "output_tokens": api_result["output_tokens"],
            "cost": api_result["cost"],
        }
        return result
    except (json.JSONDecodeError, ValueError) as e:
        print(f"  JSON 파싱 실패: {e}")
        print(f"  원본 응답:\n{api_result['text'][:1000]}")
        return {"articles": [], "surrender_values": [], "raw_response": api_result["text"]}


def save_result(result: dict, output_name: str):
    """결과 JSON 저장"""
    output_path = OUTPUT_DIR / f"{output_name}_claude.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"\n결과 저장: {output_path}")


if __name__ == "__main__":
    pdf = sys.argv[1] if len(sys.argv) > 1 else None
    model = sys.argv[2] if len(sys.argv) > 2 else "claude-sonnet-4-20250514"

    if not pdf:
        print("사용법: python extract_claude_api.py <pdf_path> [model]")
        print("모델 옵션: claude-sonnet-4-20250514, claude-haiku-4-5-20251001")
        sys.exit(1)

    result = run_pipeline(pdf, model)
    pdf_name = Path(pdf).parent.name
    save_result(result, pdf_name)
