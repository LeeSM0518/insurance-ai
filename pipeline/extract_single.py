"""
단일 PDF 파이프라인 테스트 스크립트
PyMuPDF로 텍스트 추출 → Ollama LLM으로 구조화 추출
"""

import json
import sys
import time
from pathlib import Path

import fitz  # PyMuPDF
import requests

# 설정
OLLAMA_URL = "http://localhost:11434/api/generate"
OLLAMA_MODEL = "llama3.1:latest"  # 기본 모델 (변경 가능)
PROMPT_FILE = Path(__file__).parent / "prompts" / "structure_prompt.txt"
OUTPUT_DIR = Path(__file__).parent / "output"


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


def call_ollama(prompt: str, model: str = OLLAMA_MODEL) -> str:
    """Ollama API 호출"""
    payload = {
        "model": model,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": 0.1,  # 낮은 온도로 일관성 확보
            "num_predict": 8192,  # 최대 출력 토큰
        },
    }

    print(f"  Ollama 호출 중 (모델: {model})...")
    start = time.time()
    resp = requests.post(OLLAMA_URL, json=payload, timeout=600)
    resp.raise_for_status()
    elapsed = time.time() - start

    result = resp.json()
    response_text = result.get("response", "")

    # 토큰 통계
    eval_count = result.get("eval_count", 0)
    eval_duration = result.get("eval_duration", 0)
    prompt_eval_count = result.get("prompt_eval_count", 0)
    tok_per_sec = eval_count / (eval_duration / 1e9) if eval_duration > 0 else 0

    print(f"  완료: {elapsed:.1f}초, 입력 {prompt_eval_count} tok, 출력 {eval_count} tok, {tok_per_sec:.1f} tok/s")

    return response_text


def parse_json_from_response(response: str) -> dict:
    """LLM 응답에서 JSON 추출"""
    import re

    # deepseek-r1의 <think>...</think> 블록 제거
    response = re.sub(r"<think>.*?</think>", "", response, flags=re.DOTALL).strip()

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
        # JSON 객체 직접 찾기
        start = response.find("{")
        end = response.rfind("}") + 1
        if start >= 0 and end > start:
            json_str = response[start:end]
        else:
            raise ValueError("JSON을 찾을 수 없습니다")

    try:
        return json.loads(json_str)
    except json.JSONDecodeError:
        # content 내부의 이스케이프 안 된 큰따옴표 수정 시도
        # "content": "...텍스트 "내부 따옴표" 텍스트..." → 수정
        import re
        # JSON 키-값 구조 밖의 큰따옴표를 이스케이프 처리
        fixed = re.sub(
            r'(?<=[가-힣a-zA-Z0-9])"(?=[가-힣a-zA-Z0-9])',
            '\\"',
            json_str,
        )
        try:
            return json.loads(fixed)
        except json.JSONDecodeError:
            pass
        # 최후 수단: raw 텍스트에서 직접 파싱
        raise


def run_pipeline(pdf_path: str, model: str = OLLAMA_MODEL) -> dict:
    """단일 PDF 파이프라인 실행"""
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

    # Step 3: LLM 구조화 추출
    print("\n[Step 2] Ollama LLM 구조화 추출...")
    response = call_ollama(full_prompt, model)

    # Step 4: JSON 파싱
    print("\n[Step 3] JSON 파싱...")
    try:
        result = parse_json_from_response(response)
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

        return result
    except (json.JSONDecodeError, ValueError) as e:
        print(f"  JSON 파싱 실패: {e}")
        print(f"  원본 응답:\n{response[:1000]}")
        return {"articles": [], "surrender_values": [], "raw_response": response}


def save_result(result: dict, output_name: str):
    """결과 JSON 저장"""
    output_path = OUTPUT_DIR / f"{output_name}.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"\n결과 저장: {output_path}")


if __name__ == "__main__":
    pdf = sys.argv[1] if len(sys.argv) > 1 else None
    model = sys.argv[2] if len(sys.argv) > 2 else OLLAMA_MODEL

    if not pdf:
        print("사용법: python extract_single.py <pdf_path> [model_name]")
        print("예시: python extract_single.py path/to/summary.pdf llama3.1:latest")
        sys.exit(1)

    result = run_pipeline(pdf, model)

    # 파일명 생성
    pdf_name = Path(pdf).parent.name  # 상품 디렉토리명
    save_result(result, pdf_name)
