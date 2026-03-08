"""
Claude API에 PDF를 직접 base64로 전송하여 구조화 추출
Haiku, Sonnet, Opus 3개 모델 비교 테스트
"""

import base64
import json
import sys
import time
from pathlib import Path

import anthropic

PROMPT_FILE = Path(__file__).parent / "prompts" / "structure_prompt.txt"
OUTPUT_DIR = Path(__file__).parent / "output"

# API 키 로드
API_KEY_FILE = Path(__file__).parent.parent / ".claude" / "secrets" / "anthropic-api-key"
API_KEY = API_KEY_FILE.read_text().strip()

# 최신 모델 ID
MODELS = {
    "haiku": "claude-haiku-4-5",
    "sonnet": "claude-sonnet-4-6",
    "opus": "claude-opus-4-6",
}

# 비용 ($/1M tokens)
PRICING = {
    "haiku": {"input": 1.00, "output": 5.00},
    "sonnet": {"input": 3.00, "output": 15.00},
    "opus": {"input": 5.00, "output": 25.00},
}


def call_claude_with_pdf(pdf_path: str, prompt_text: str, model_key: str) -> dict:
    """PDF를 base64로 직접 전송하여 Claude API 호출"""
    client = anthropic.Anthropic(api_key=API_KEY)
    model_id = MODELS[model_key]
    pricing = PRICING[model_key]

    # PDF를 base64로 인코딩
    with open(pdf_path, "rb") as f:
        pdf_data = base64.standard_b64encode(f.read()).decode("utf-8")

    print(f"  Claude API 호출 중 (모델: {model_id}, PDF 직접 전송)...")
    start = time.time()

    # 스트리밍 사용 (max_tokens가 클 때 타임아웃 방지)
    with client.messages.stream(
        model=model_id,
        max_tokens=32768,
        temperature=0.1,
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "document",
                        "source": {
                            "type": "base64",
                            "media_type": "application/pdf",
                            "data": pdf_data,
                        },
                    },
                    {
                        "type": "text",
                        "text": prompt_text,
                    },
                ],
            }
        ],
    ) as stream:
        response = stream.get_final_message()

    elapsed = time.time() - start
    text = response.content[0].text
    input_tokens = response.usage.input_tokens
    output_tokens = response.usage.output_tokens

    cost = (input_tokens * pricing["input"] + output_tokens * pricing["output"]) / 1_000_000
    print(f"  완료: {elapsed:.1f}초, 입력 {input_tokens} tok, 출력 {output_tokens} tok")
    print(f"  예상 비용: ${cost:.4f}")

    return {
        "text": text,
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "cost": cost,
        "elapsed": elapsed,
    }


def parse_json_from_response(response: str) -> dict:
    """LLM 응답에서 JSON 추출"""
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


def run_test(pdf_path: str, model_key: str) -> dict:
    """단일 모델 테스트 실행"""
    model_id = MODELS[model_key]
    print(f"\n{'='*60}")
    print(f"모델: {model_id} (PDF 직접 전송)")
    print(f"PDF: {pdf_path}")
    print(f"{'='*60}")

    # 프롬프트 로드
    prompt_text = PROMPT_FILE.read_text(encoding="utf-8")

    # API 호출
    api_result = call_claude_with_pdf(pdf_path, prompt_text, model_key)

    # JSON 파싱
    print("\n  JSON 파싱...")
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
            "model": model_id,
            "method": "pdf_direct_base64",
            "input_tokens": api_result["input_tokens"],
            "output_tokens": api_result["output_tokens"],
            "cost": api_result["cost"],
            "elapsed": api_result["elapsed"],
        }
        return result
    except (json.JSONDecodeError, ValueError) as e:
        print(f"  JSON 파싱 실패: {e}")
        print(f"  원본 응답:\n{api_result['text'][:1000]}")
        return {
            "articles": [],
            "surrender_values": [],
            "raw_response": api_result["text"],
            "_meta": {
                "model": model_id,
                "method": "pdf_direct_base64",
                "input_tokens": api_result["input_tokens"],
                "output_tokens": api_result["output_tokens"],
                "cost": api_result["cost"],
                "elapsed": api_result["elapsed"],
                "parse_error": str(e),
            },
        }


def save_result(result: dict, output_name: str, model_key: str):
    """결과 JSON 저장"""
    output_path = OUTPUT_DIR / f"{output_name}_pdf_direct_{model_key}.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"  결과 저장: {output_path}")


def print_summary(results: dict):
    """전체 결과 요약 출력"""
    print(f"\n\n{'='*70}")
    print("📊 PDF 직접 전송 결과 비교 요약")
    print(f"{'='*70}")

    header = f"{'모델':<25} {'articles':>8} {'sv':>5} {'입력tok':>8} {'출력tok':>8} {'비용($)':>8} {'시간(s)':>8}"
    print(header)
    print("-" * 70)

    for model_key, result in results.items():
        meta = result.get("_meta", {})
        articles = len(result.get("articles", []))
        sv = len(result.get("surrender_values", []))
        inp = meta.get("input_tokens", 0)
        out = meta.get("output_tokens", 0)
        cost = meta.get("cost", 0)
        elapsed = meta.get("elapsed", 0)
        model_name = meta.get("model", model_key)

        print(f"{model_name:<25} {articles:>8} {sv:>5} {inp:>8} {out:>8} {cost:>8.4f} {elapsed:>8.1f}")

    # content_type 분포 비교
    print(f"\n{'content_type 분포 비교':}")
    for model_key, result in results.items():
        model_name = result.get("_meta", {}).get("model", model_key)
        type_counts = {}
        for a in result.get("articles", []):
            ct = a.get("content_type", "UNKNOWN")
            type_counts[ct] = type_counts.get(ct, 0) + 1
        print(f"  {model_name}: {type_counts}")


if __name__ == "__main__":
    pdf = sys.argv[1] if len(sys.argv) > 1 else None
    target_models = sys.argv[2].split(",") if len(sys.argv) > 2 else ["haiku", "sonnet", "opus"]

    if not pdf:
        print("사용법: python extract_pdf_direct.py <pdf_path> [models]")
        print("모델 옵션: haiku,sonnet,opus (쉼표 구분, 기본: 전체)")
        print("예시: python extract_pdf_direct.py path/to/summary.pdf haiku,sonnet")
        sys.exit(1)

    pdf_name = Path(pdf).parent.name
    results = {}

    for model_key in target_models:
        model_key = model_key.strip()
        if model_key not in MODELS:
            print(f"알 수 없는 모델: {model_key} (haiku, sonnet, opus 중 선택)")
            continue
        result = run_test(pdf, model_key)
        results[model_key] = result
        save_result(result, pdf_name, model_key)

    if len(results) > 1:
        print_summary(results)
