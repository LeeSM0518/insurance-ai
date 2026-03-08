#!/usr/bin/env python3
"""
전체 실손의료보험 61개 상품 PDF-DB 일괄 비교 스크립트
PDF 원문 텍스트와 DB policy_articles/surrender_values를 대조하여 정확도를 검증합니다.
"""

import pdfplumber
import psycopg2
import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

DB_URL = "postgresql://insurance:password@localhost:5432/insurance_ai"

# 상품ID → PDF 경로 매핑 (data_sources 테이블에서 가져옴)
def get_product_pdf_mapping(conn):
    """DB의 data_sources에서 상품별 PDF 경로를 가져옴"""
    cur = conn.cursor()
    cur.execute("""
        SELECT p.id, p.name, ic.name as company, ds.file_path
        FROM products p
        JOIN insurance_companies ic ON p.insurance_company_id = ic.id
        LEFT JOIN data_sources ds ON ds.source_type = 'PRODUCT_SUMMARY'
            AND ds.file_path LIKE '%summary.pdf%'
            AND EXISTS (
                SELECT 1 FROM policy_articles pa
                WHERE pa.product_id = p.id AND pa.data_source_id = ds.id
            )
        WHERE p.category IN ('HEALTH', 'NL_HEALTH')
        ORDER BY ic.name, p.name
    """)
    rows = cur.fetchall()
    cur.close()
    return rows

def get_articles(conn, product_id):
    """상품별 policy_articles 조회"""
    cur = conn.cursor()
    cur.execute("""
        SELECT content_type, section_type, content
        FROM policy_articles
        WHERE product_id = %s AND is_active = true
        ORDER BY content_type, section_type
    """, (product_id,))
    rows = cur.fetchall()
    cur.close()
    return rows

def get_surrender_values(conn, product_id):
    """상품별 surrender_values 조회"""
    cur = conn.cursor()
    cur.execute("""
        SELECT elapsed_months, premium_paid, surrender_value, surrender_rate
        FROM surrender_values
        WHERE product_id = %s
        ORDER BY elapsed_months
    """, (product_id,))
    rows = cur.fetchall()
    cur.close()
    return rows

def extract_pdf_text(pdf_path):
    """PDF에서 전체 텍스트 추출"""
    try:
        with pdfplumber.open(pdf_path) as pdf:
            text = ""
            for page in pdf.pages:
                page_text = page.extract_text()
                if page_text:
                    text += page_text + "\n"
            return text
    except Exception as e:
        return f"ERROR: {e}"

def extract_pdf_tables(pdf_path):
    """PDF에서 테이블 추출"""
    tables = []
    try:
        with pdfplumber.open(pdf_path) as pdf:
            for i, page in enumerate(pdf.pages):
                page_tables = page.extract_tables()
                if page_tables:
                    for t in page_tables:
                        tables.append({"page": i+1, "data": t})
    except:
        pass
    return tables

def find_pdf_path(product_name, company_name):
    """상품명과 보험사명으로 PDF 경로 찾기"""
    base = "/Users/sangminlee/me/insurance-ai/server/data/summaries"

    # 전체 PDF 목록에서 검색
    for root, dirs, files in os.walk(base):
        for f in files:
            if f == "summary.pdf":
                dir_name = os.path.basename(root)
                # 보험사명이 디렉토리명에 포함되는지 확인
                if company_simplified(company_name) in dir_name:
                    # 상품명 키워드 매칭
                    if product_name_matches(product_name, dir_name):
                        return os.path.join(root, f)
    return None

def company_simplified(name):
    """보험사명 단순화"""
    mappings = {
        "교보생명": "교보생명",
        "동양생명": "동양생명",
        "삼성생명": "삼성생명",
        "삼성화재": "삼성화재",
        "한화생명": "한화생명",
        "현대해상": "현대해상",
        "흥국생명": "흥국생명",
        "흥국화재": "흥국화재",
        "메리츠화재": "메리츠화재",
        "농협손해보험": "농협손보",
        "롯데손해보험": "롯데손보",
        "미래에셋생명": "미래에셋생명",
        "푸본현대생명": "푸본현대생명",
        "한화손해보험": "한화손보",
        "DB생명": "DB생명",
        "DB손해보험": "DB손보",
        "신한EZ손해보험": "신한EZ손해보험",
        "KB손해보험": "KB손보",
        "NH농협생명": "NH농협생명",
    }
    return mappings.get(name, name)

def product_name_matches(product_name, dir_name):
    """상품명이 디렉토리명에 매칭되는지 확인"""
    # 핵심 키워드 추출
    keywords = []
    # 괄호 안 내용 추출
    parens = re.findall(r'\(([^)]+)\)', product_name)
    keywords.extend(parens)

    # 주요 단어 추출
    for word in ['노후', '간편', '다이렉트', '유병력자', '인터넷', '급여', 'e실손', 'SOL', '이지로운',
                  '수호천사', '프로미라이프', 'letcare', 'let:care', '헤아림', '흥Good',
                  '계약전환용', '2501.5', '2601.6', '2601.10', '2601.13', '2601.20',
                  '2601', '2504', '2107', '1종', '2종', 'CM', 'Hi2601',
                  '26.01', '26.02', 'New안심케어']:
        if word in product_name:
            keywords.append(word)

    # 디렉토리명에서 keyword 2개 이상 매칭
    dir_clean = dir_name.replace('_', ' ')
    match_count = sum(1 for k in keywords if k.replace(':', '') in dir_clean.replace(':', ''))

    return match_count >= 2 or (match_count >= 1 and len(keywords) <= 2)

def normalize_text(text):
    """텍스트 비교를 위한 정규화"""
    if not text:
        return ""
    # 공백, 줄바꿈 정규화
    text = re.sub(r'\s+', ' ', text).strip()
    return text

def check_content_in_pdf(pdf_text, db_content, min_match_ratio=0.5):
    """DB content가 PDF 텍스트에 포함되는지 확인"""
    if not db_content or not pdf_text:
        return False, 0.0

    # DB content에서 핵심 구절 추출 (30자 단위)
    content_normalized = normalize_text(db_content)
    pdf_normalized = normalize_text(pdf_text)

    if len(content_normalized) < 20:
        # 짧은 컨텐츠는 직접 포함 여부 확인
        found = content_normalized in pdf_normalized
        return found, 1.0 if found else 0.0

    # 30자 단위로 슬라이딩 윈도우 매칭
    chunk_size = 30
    chunks = []
    for i in range(0, len(content_normalized) - chunk_size + 1, chunk_size):
        chunks.append(content_normalized[i:i+chunk_size])

    if not chunks:
        chunks = [content_normalized[:min(len(content_normalized), 30)]]

    matched = sum(1 for c in chunks if c in pdf_normalized)
    ratio = matched / len(chunks) if chunks else 0

    return ratio >= min_match_ratio, ratio

def check_surrender_values_in_pdf(pdf_text, sv_rows):
    """해약환급금 수치가 PDF에 있는지 확인"""
    if not sv_rows or not pdf_text:
        return True, []  # 해약환급금이 없으면 통과

    pdf_normalized = normalize_text(pdf_text)
    mismatches = []

    for months, premium, sv_value, sv_rate in sv_rows:
        # 금액 수치 확인 (천원 단위 등)
        if premium:
            premium_str = f"{int(premium):,}"
            premium_str_no_comma = str(int(premium))
            if premium_str not in pdf_normalized and premium_str_no_comma not in pdf_normalized:
                # 만원 단위로도 체크
                if premium >= 10000:
                    man_won = str(int(premium / 10000))
                    if man_won not in pdf_normalized:
                        mismatches.append(f"premium {premium} at {months}개월")

        if sv_value:
            sv_str = f"{int(sv_value):,}"
            sv_str_no_comma = str(int(sv_value))
            if sv_str not in pdf_normalized and sv_str_no_comma not in pdf_normalized:
                mismatches.append(f"surrender_value {sv_value} at {months}개월")

    # 80% 이상 매칭이면 OK
    total = len(sv_rows) * 2  # premium + surrender_value
    mismatch_count = len(mismatches)
    ok = mismatch_count <= total * 0.2

    return ok, mismatches[:5]  # 최대 5개만 리포트

def analyze_product(conn, product_id, product_name, company_name, pdf_path):
    """단일 상품 PDF-DB 비교 분석"""
    result = {
        "product_id": product_id,
        "product_name": product_name,
        "company": company_name,
        "pdf_path": pdf_path,
        "status": "UNKNOWN",
        "article_count": 0,
        "sv_count": 0,
        "content_types": [],
        "issues": [],
        "details": {}
    }

    # PDF 경로 확인
    if not pdf_path or not os.path.exists(pdf_path):
        # 자동 검색 시도
        found = find_pdf_path(product_name, company_name)
        if found:
            pdf_path = found
            result["pdf_path"] = found
        else:
            result["status"] = "PDF_NOT_FOUND"
            result["issues"].append("PDF 파일을 찾을 수 없음")
            return result

    # PDF 텍스트 추출
    pdf_text = extract_pdf_text(pdf_path)
    if pdf_text.startswith("ERROR:"):
        result["status"] = "PDF_READ_ERROR"
        result["issues"].append(pdf_text)
        return result

    # DB 데이터 조회
    articles = get_articles(conn, product_id)
    sv_rows = get_surrender_values(conn, product_id)

    result["article_count"] = len(articles)
    result["sv_count"] = len(sv_rows)

    # content_type 분포
    type_counts = defaultdict(int)
    for ct, st, content in articles:
        type_counts[ct] += 1
    result["content_types"] = dict(type_counts)

    # 1. 핵심 content_type 존재 여부 확인
    expected_types = ['COVERAGE', 'EXEMPTION', 'QUALIFICATION']
    for et in expected_types:
        if et not in type_counts:
            # EXEMPTION은 4세대 실손에서 없을 수 있음 (정상)
            if et == 'EXEMPTION':
                result["details"]["exemption_missing"] = "PDF에 면책 별도 섹션 없을 수 있음 (정상)"
            else:
                result["issues"].append(f"{et} content_type 누락")

    # 2. 각 article content가 PDF에 포함되는지 확인
    content_check_results = []
    for ct, st, content in articles:
        if not content or len(content.strip()) < 10:
            content_check_results.append((ct, st, False, 0.0, "빈 content"))
            continue

        found, ratio = check_content_in_pdf(pdf_text, content)
        content_check_results.append((ct, st, found, ratio, ""))

    matched_articles = sum(1 for _, _, found, _, _ in content_check_results if found)
    total_articles = len(content_check_results)
    match_ratio = matched_articles / total_articles if total_articles > 0 else 0
    result["details"]["article_match_ratio"] = f"{matched_articles}/{total_articles} ({match_ratio:.1%})"

    # 매칭 안 되는 articles 상세
    unmatched = [(ct, st, ratio) for ct, st, found, ratio, _ in content_check_results if not found]
    if unmatched:
        result["details"]["unmatched_articles"] = [
            f"{ct}/{st} (match: {ratio:.0%})" for ct, st, ratio in unmatched[:5]
        ]

    # 3. 해약환급금 수치 확인
    if sv_rows:
        sv_ok, sv_mismatches = check_surrender_values_in_pdf(pdf_text, sv_rows)
        result["details"]["sv_check"] = "OK" if sv_ok else f"불일치 {len(sv_mismatches)}건"
        if sv_mismatches:
            result["details"]["sv_mismatches"] = sv_mismatches
            result["issues"].append(f"해약환급금 수치 불일치 {len(sv_mismatches)}건")

    # 4. 종합 판정
    if match_ratio >= 0.7 and not any("해약환급금" in i for i in result["issues"]):
        result["status"] = "OK"
    elif match_ratio >= 0.5:
        result["status"] = "PARTIAL"
    else:
        result["status"] = "MISMATCH"
        result["issues"].append(f"article 매칭률 {match_ratio:.0%}로 낮음")

    return result

def main():
    conn = psycopg2.connect(DB_URL)

    print("=" * 80)
    print("실손의료보험 61개 상품 PDF-DB 전수 비교")
    print("=" * 80)

    # 전체 상품 + PDF 경로 매핑
    # data_sources에서 file_path를 가져오되, 없으면 자동 검색
    cur = conn.cursor()
    cur.execute("""
        SELECT p.id, p.name, ic.name as company,
            (SELECT ds.file_path FROM data_sources ds
             WHERE ds.source_type = 'PRODUCT_SUMMARY'
             AND EXISTS (SELECT 1 FROM policy_articles pa WHERE pa.product_id = p.id AND pa.data_source_id = ds.id)
             LIMIT 1) as pdf_path
        FROM products p
        JOIN insurance_companies ic ON p.insurance_company_id = ic.id
        WHERE p.category IN ('HEALTH', 'NL_HEALTH')
        ORDER BY ic.name, p.name
    """)
    products = cur.fetchall()
    cur.close()

    results = []
    ok_count = 0
    partial_count = 0
    mismatch_count = 0
    error_count = 0

    for i, (pid, pname, company, pdf_db_path) in enumerate(products):
        print(f"\n[{i+1}/{len(products)}] {company} - {pname[:40]}...", end=" ", flush=True)

        # PDF 경로 변환 (DB 상대경로 → 절대경로)
        pdf_path = None
        if pdf_db_path:
            # server/data/summaries/... 형태일 수 있음
            if pdf_db_path.startswith("server/"):
                pdf_path = os.path.join("/Users/sangminlee/me/insurance-ai", pdf_db_path)
            elif pdf_db_path.startswith("/"):
                pdf_path = pdf_db_path
            else:
                pdf_path = os.path.join("/Users/sangminlee/me/insurance-ai/server/data/summaries", pdf_db_path)

        result = analyze_product(conn, pid, pname, company, pdf_path)
        results.append(result)

        status_icon = {"OK": "✅", "PARTIAL": "⚠️", "MISMATCH": "❌", "PDF_NOT_FOUND": "📄", "PDF_READ_ERROR": "💥"}.get(result["status"], "❓")
        print(f"{status_icon} {result['status']} (articles: {result['article_count']}, sv: {result['sv_count']})")

        if result["status"] == "OK":
            ok_count += 1
        elif result["status"] == "PARTIAL":
            partial_count += 1
        elif result["status"] in ("MISMATCH",):
            mismatch_count += 1
        else:
            error_count += 1

        # 이슈 출력
        if result["issues"]:
            for issue in result["issues"]:
                print(f"    ⚠ {issue}")
        if result["details"].get("unmatched_articles"):
            for ua in result["details"]["unmatched_articles"]:
                print(f"    📋 미매칭: {ua}")

    # 최종 요약
    print("\n" + "=" * 80)
    print("최종 요약")
    print("=" * 80)
    print(f"✅ OK (70%+ 매칭): {ok_count}")
    print(f"⚠️ PARTIAL (50-70% 매칭): {partial_count}")
    print(f"❌ MISMATCH (<50% 매칭): {mismatch_count}")
    print(f"📄 PDF 미발견/오류: {error_count}")
    print(f"총 {len(products)}개 상품")

    # JSON 리포트 저장
    report_path = "/Users/sangminlee/me/insurance-ai/scripts/pdf_db_compare_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2, default=str)
    print(f"\n상세 리포트: {report_path}")

    # 문제 상품 상세 리스트
    problem_products = [r for r in results if r["status"] not in ("OK",)]
    if problem_products:
        print(f"\n{'='*80}")
        print("문제 상품 상세")
        print("=" * 80)
        for r in problem_products:
            print(f"\n{r['company']} - {r['product_name']}")
            print(f"  상태: {r['status']}")
            print(f"  articles: {r['article_count']}, surrender_values: {r['sv_count']}")
            print(f"  content_types: {r['content_types']}")
            if r["issues"]:
                for issue in r["issues"]:
                    print(f"  ⚠ {issue}")
            if r["details"].get("article_match_ratio"):
                print(f"  매칭률: {r['details']['article_match_ratio']}")
            if r["details"].get("unmatched_articles"):
                for ua in r["details"]["unmatched_articles"]:
                    print(f"  미매칭: {ua}")

    conn.close()

if __name__ == "__main__":
    main()
