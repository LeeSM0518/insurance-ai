#!/usr/bin/env python3
"""
전체 실손의료보험 61개 상품 PDF-DB 일괄 비교 스크립트 v2
- PDF 출처(PRODUCT_SUMMARY) articles만 비교
- 핵심 키워드 기반 매칭으로 false negative 최소화
- 해약환급금 수치 정확성 검증
"""

import pdfplumber
import psycopg2
import json
import os
import re
import sys
from collections import defaultdict

DB_URL = "postgresql://insurance:password@localhost:5432/insurance_ai"

def get_pdf_articles(conn, product_id):
    """PDF 출처 articles만 조회"""
    cur = conn.cursor()
    cur.execute("""
        SELECT pa.content_type, pa.section_type, pa.content, ds.file_path
        FROM policy_articles pa
        JOIN data_sources ds ON pa.data_source_id = ds.id
        WHERE pa.product_id = %s
        AND pa.is_active = true
        AND ds.source_type = 'PRODUCT_SUMMARY'
        ORDER BY pa.content_type, pa.section_type
    """, (product_id,))
    rows = cur.fetchall()
    cur.close()
    return rows

def get_association_articles(conn, product_id):
    """비교공시 출처 articles 조회"""
    cur = conn.cursor()
    cur.execute("""
        SELECT pa.content_type, pa.section_type, pa.content
        FROM policy_articles pa
        JOIN data_sources ds ON pa.data_source_id = ds.id
        WHERE pa.product_id = %s
        AND pa.is_active = true
        AND ds.source_type = 'ASSOCIATION'
        ORDER BY pa.content_type, pa.section_type
    """, (product_id,))
    rows = cur.fetchall()
    cur.close()
    return rows

def get_surrender_values(conn, product_id):
    """surrender_values 조회"""
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
    """PDF 전체 텍스트 추출"""
    try:
        with pdfplumber.open(pdf_path) as pdf:
            texts = []
            for page in pdf.pages:
                t = page.extract_text()
                if t:
                    texts.append(t)
            return "\n".join(texts)
    except Exception as e:
        return None

def normalize_for_match(text):
    """매칭용 정규화: 공백/줄바꿈 제거"""
    if not text:
        return ""
    return re.sub(r'\s+', '', text)

def extract_key_phrases(content, phrase_len=20):
    """content에서 핵심 구절 추출 (정규화 후 phrase_len 단위)"""
    norm = normalize_for_match(content)
    if len(norm) < phrase_len:
        return [norm] if norm else []

    phrases = []
    step = max(phrase_len, len(norm) // 10)  # 최소 10개 샘플
    for i in range(0, len(norm) - phrase_len + 1, step):
        phrases.append(norm[i:i+phrase_len])

    # 마지막 구절도 포함
    if norm[-phrase_len:] not in phrases:
        phrases.append(norm[-phrase_len:])

    return phrases

def match_content_to_pdf(content, pdf_norm, phrase_len=20):
    """DB content가 PDF에 포함되는지 핵심 구절 매칭으로 확인"""
    if not content or not pdf_norm:
        return 0.0

    phrases = extract_key_phrases(content, phrase_len)
    if not phrases:
        return 0.0

    matched = sum(1 for p in phrases if p in pdf_norm)
    return matched / len(phrases)

def check_sv_numbers(pdf_text, sv_rows):
    """해약환급금 수치가 PDF에 있는지 확인"""
    if not sv_rows:
        return True, 0, 0

    pdf_norm = normalize_for_match(pdf_text)
    total_checks = 0
    matched_checks = 0

    for months, premium, sv_value, sv_rate in sv_rows:
        # 납입보험료 확인
        if premium and premium > 0:
            total_checks += 1
            p_str = str(int(premium))
            p_comma = f"{int(premium):,}"
            if p_str in pdf_norm or p_comma.replace(",", "") in pdf_norm:
                matched_checks += 1

        # 환급금 확인
        if sv_value and sv_value > 0:
            total_checks += 1
            sv_str = str(int(sv_value))
            if sv_str in pdf_norm:
                matched_checks += 1

        # 환급률 확인
        if sv_rate and sv_rate > 0:
            total_checks += 1
            # 소수점 있는 경우
            rate_str = f"{float(sv_rate):.1f}"
            rate_str2 = f"{float(sv_rate):.0f}"
            if rate_str in pdf_norm or rate_str2 in pdf_norm:
                matched_checks += 1

    return total_checks > 0 and matched_checks / total_checks >= 0.6, matched_checks, total_checks

def main():
    conn = psycopg2.connect(DB_URL)

    # 전체 실손 상품 조회
    cur = conn.cursor()
    cur.execute("""
        SELECT p.id, p.name, ic.name as company
        FROM products p
        JOIN insurance_companies ic ON p.insurance_company_id = ic.id
        WHERE p.category IN ('HEALTH', 'NL_HEALTH')
        ORDER BY ic.name, p.name
    """)
    products = cur.fetchall()
    cur.close()

    print("=" * 90)
    print("실손의료보험 61개 상품 PDF-DB 전수 비교 (v2 - PDF 출처만)")
    print("=" * 90)

    results = []
    summary = {"OK": 0, "MINOR": 0, "ISSUE": 0, "NO_PDF_ARTICLES": 0}

    for i, (pid, pname, company) in enumerate(products):
        # PDF 출처 articles
        pdf_articles = get_pdf_articles(conn, pid)
        assoc_articles = get_association_articles(conn, pid)
        sv_rows = get_surrender_values(conn, pid)

        result = {
            "idx": i+1,
            "company": company,
            "name": pname,
            "pdf_articles": len(pdf_articles),
            "assoc_articles": len(assoc_articles),
            "sv_count": len(sv_rows),
            "status": "OK",
            "issues": [],
            "content_types": defaultdict(int),
            "match_details": []
        }

        if not pdf_articles:
            result["status"] = "NO_PDF_ARTICLES"
            result["issues"].append("PDF 출처 articles 없음")
            results.append(result)
            summary["NO_PDF_ARTICLES"] += 1
            print(f"[{i+1:2d}/61] {company:8s} {pname[:45]:45s} 📄 PDF articles 없음 (assoc: {len(assoc_articles)})")
            continue

        # PDF 경로 (첫 번째 article의 data_source file_path)
        pdf_path = pdf_articles[0][3]
        if pdf_path and not pdf_path.startswith("/"):
            pdf_path = os.path.join("/Users/sangminlee/me/insurance-ai", pdf_path)

        pdf_text = extract_pdf_text(pdf_path) if pdf_path else None
        if not pdf_text:
            result["status"] = "ISSUE"
            result["issues"].append(f"PDF 읽기 실패: {pdf_path}")
            results.append(result)
            summary["ISSUE"] += 1
            print(f"[{i+1:2d}/61] {company:8s} {pname[:45]:45s} ❌ PDF 읽기 실패")
            continue

        pdf_norm = normalize_for_match(pdf_text)

        # content_type 분포
        for ct, st, content, fp in pdf_articles:
            result["content_types"][ct] += 1

        # 각 PDF article의 매칭률 계산
        total_ratio = 0
        article_results = []
        for ct, st, content, fp in pdf_articles:
            ratio = match_content_to_pdf(content, pdf_norm)
            total_ratio += ratio
            article_results.append((ct, st, ratio, len(content or "")))
            if ratio < 0.5:
                result["match_details"].append(f"{ct}/{st} ({ratio:.0%}, {len(content or '')}자)")

        avg_ratio = total_ratio / len(pdf_articles) if pdf_articles else 0
        high_match = sum(1 for _, _, r, _ in article_results if r >= 0.5)
        low_match = sum(1 for _, _, r, _ in article_results if r < 0.3)

        # 해약환급금 검증
        sv_ok, sv_matched, sv_total = check_sv_numbers(pdf_text, sv_rows)
        sv_info = f"sv:{sv_matched}/{sv_total}" if sv_total > 0 else "sv:N/A"

        # 종합 판정
        if avg_ratio >= 0.6 and low_match <= len(pdf_articles) * 0.2:
            result["status"] = "OK"
            summary["OK"] += 1
        elif avg_ratio >= 0.4 or (high_match >= len(pdf_articles) * 0.5):
            result["status"] = "MINOR"
            summary["MINOR"] += 1
            if low_match > 0:
                result["issues"].append(f"저매칭 articles {low_match}건")
        else:
            result["status"] = "ISSUE"
            summary["ISSUE"] += 1
            result["issues"].append(f"평균 매칭률 {avg_ratio:.0%}로 낮음")

        if not sv_ok and sv_total > 0:
            result["issues"].append(f"해약환급금 수치 불일치 ({sv_matched}/{sv_total})")

        # 출력
        icon = {"OK": "✅", "MINOR": "⚠️", "ISSUE": "❌", "NO_PDF_ARTICLES": "📄"}.get(result["status"], "?")
        types_str = " ".join(f"{k}:{v}" for k, v in sorted(result["content_types"].items()))
        issues_str = " | ".join(result["issues"]) if result["issues"] else ""

        print(f"[{i+1:2d}/61] {icon} {company:8s} {pname[:40]:40s} "
              f"pdf:{len(pdf_articles):2d} avg:{avg_ratio:.0%} hi:{high_match} lo:{low_match} {sv_info}"
              f"{' | '+issues_str if issues_str else ''}")

        # 저매칭 상세 (처음 3개만)
        if result["match_details"] and result["status"] != "OK":
            for detail in result["match_details"][:3]:
                print(f"      📋 {detail}")

        results.append(result)

    # 최종 요약
    print("\n" + "=" * 90)
    print("최종 요약")
    print("=" * 90)
    print(f"✅ OK (정상): {summary['OK']}")
    print(f"⚠️ MINOR (경미한 차이): {summary['MINOR']}")
    print(f"❌ ISSUE (확인 필요): {summary['ISSUE']}")
    print(f"📄 PDF articles 없음: {summary['NO_PDF_ARTICLES']}")
    print(f"총 {len(products)}개 상품")

    # ISSUE 상품 상세
    issue_products = [r for r in results if r["status"] == "ISSUE"]
    if issue_products:
        print(f"\n--- ❌ ISSUE 상품 상세 ({len(issue_products)}건) ---")
        for r in issue_products:
            print(f"  {r['company']} - {r['name']}")
            for issue in r["issues"]:
                print(f"    ⚠ {issue}")
            for detail in r.get("match_details", [])[:3]:
                print(f"    📋 {detail}")

    # MINOR 상품 상세
    minor_products = [r for r in results if r["status"] == "MINOR"]
    if minor_products:
        print(f"\n--- ⚠️ MINOR 상품 상세 ({len(minor_products)}건) ---")
        for r in minor_products:
            issues_str = " | ".join(r["issues"]) if r["issues"] else ""
            print(f"  {r['company']} - {r['name'][:50]} | {issues_str}")

    # JSON 저장
    report_path = "/Users/sangminlee/me/insurance-ai/scripts/pdf_db_compare_report_v2.json"
    with open(report_path, "w", encoding="utf-8") as f:
        # defaultdict를 dict로 변환
        for r in results:
            r["content_types"] = dict(r["content_types"])
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n상세 리포트: {report_path}")

    conn.close()

if __name__ == "__main__":
    main()
