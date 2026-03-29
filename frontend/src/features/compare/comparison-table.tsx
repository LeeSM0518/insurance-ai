import type { ComparisonResponse, CompanyComparison, ProductComparison } from '@insurance-ai/api-client';

interface ComparisonTableProps {
  data: ComparisonResponse;
}

/** 숫자 문자열에서 숫자만 추출 */
function parseNumericValue(value: string): number | null {
  const num = parseInt(value.replace(/[^0-9]/g, ''), 10);
  return isNaN(num) ? null : num;
}

/** 셀 하이라이트 클래스 결정 */
function getHighlightClass(item: string, value: string | null, allValues: (string | null)[]): string {
  if (!value) return '';

  // 보험료: 최저값 강조
  if (item.includes('보험료')) {
    const numValues = allValues.map((v) => (v ? parseNumericValue(v) : null)).filter((v): v is number => v !== null);
    const current = parseNumericValue(value);
    if (current !== null && numValues.length > 1 && current === Math.min(...numValues)) {
      return 'bg-green-50 border-green-200 dark:bg-green-950/30 dark:border-green-800';
    }
  }

  // 보장금액: 최고값 강조
  if (item.includes('진단비') || item.includes('수술비') || item.includes('입원')) {
    const numValues = allValues.map((v) => (v ? parseNumericValue(v) : null)).filter((v): v is number => v !== null);
    const current = parseNumericValue(value);
    if (current !== null && numValues.length > 1 && current === Math.max(...numValues)) {
      return 'bg-blue-50 border-blue-200 dark:bg-blue-950/30 dark:border-blue-800';
    }
  }

  // 면책기간: "없음"이 유리
  if (item.includes('면책') && value === '없음') {
    return 'bg-emerald-50 dark:bg-emerald-950/30';
  }

  // 갱신: "비갱신"이 유리
  if (item.includes('갱신') && value.includes('비갱신')) {
    return 'bg-emerald-50 dark:bg-emerald-950/30';
  }

  return '';
}

/** 상품에서 특정 비교 항목의 값을 추출 */
function getCoverageValue(product: ProductComparison, itemName: string): string | null {
  const coverage = product.coverages?.find((c) => {
    const normalized = normalizeName(c.name ?? '');
    return normalized === itemName || (c.name ?? '').includes(itemName);
  });
  if (coverage) {
    return coverage.amount ?? coverage.conditions ?? null;
  }
  return null;
}

/** 보장 항목명 정규화 (서버 로직과 동일) */
function normalizeName(name: string): string {
  return name
    .replace(/^\s*(무배당|유배당|\(무\)|\(유\))\s*/, '')
    .replace(/^\s*\(갱신형\)\s*/, '')
    .replace(/^\s*\[\d+세갱신\]\s*/, '')
    .replace(/\s*(특약|보장|담보|\(선택\)|\(필수\))\s*$/, '')
    .trim();
}

/** 단일 보험사-상품 열 정보 */
interface ColumnInfo {
  company: CompanyComparison;
  product: ProductComparison;
}

export function ComparisonTable({ data }: ComparisonTableProps) {
  // 모든 보험사-상품 조합을 열로 펼치기
  const columns: ColumnInfo[] = data.companies?.flatMap((company) =>
    (company.products ?? []).map((product) => ({ company, product })),
  ) ?? [];

  if (columns.length === 0) {
    return (
      <div className="text-center py-8 text-muted-foreground">
        비교할 상품이 없습니다
      </div>
    );
  }

  // 비교 항목 행 구성
  const comparisonItems = data.comparisonItems ?? [];

  // 추가 행: 갱신유형, 보험료, 해약환급금
  const extraRows = [
    { label: '갱신유형', getValue: (col: ColumnInfo) => col.product.renewalType ?? '—' },
    { label: '보험기간', getValue: (col: ColumnInfo) => col.product.insurancePeriod ?? '—' },
    {
      label: '월 보험료',
      getValue: (col: ColumnInfo) => {
        const p = col.product.premiumExample;
        if (!p?.monthlyPremium) return '—';
        return `${p.monthlyPremium.toLocaleString()}원${p.age ? ` (${p.age}세/${p.gender})` : ''}`;
      },
    },
    {
      label: '해약환급금',
      getValue: (col: ColumnInfo) => {
        const s = col.product.surrenderValueSample;
        if (!s?.surrenderValue) return '—';
        return `${s.surrenderValue.toLocaleString()}원 (${s.elapsedMonths}개월, ${s.surrenderRate}%)`;
      },
    },
  ];

  return (
    <div className="overflow-x-auto border border-border rounded-lg">
      <table className="w-full min-w-[600px] text-sm">
        <thead>
          <tr className="bg-muted/50">
            <th className="sticky left-0 z-10 bg-muted/50 text-left px-3 py-2.5 font-medium text-foreground border-b border-r border-border min-w-[140px]">
              비교 항목
            </th>
            {columns.map((col, i) => (
              <th
                key={`${col.company.companyId}-${col.product.productId}-${i}`}
                className="text-left px-3 py-2.5 font-medium text-foreground border-b border-border min-w-[180px]"
              >
                <div className="text-xs text-muted-foreground">{col.company.companyName}</div>
                <div className="truncate">{col.product.productName}</div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {/* 기본 정보 행 */}
          {extraRows.map((row) => {
            const allValues = columns.map((col) => row.getValue(col));
            return (
              <tr key={row.label} className="border-b border-border hover:bg-muted/30">
                <td className="sticky left-0 z-10 bg-background px-3 py-2 font-medium text-foreground border-r border-border">
                  {row.label}
                </td>
                {columns.map((col, i) => {
                  const value = row.getValue(col);
                  const highlight = getHighlightClass(row.label, value === '—' ? null : value, allValues.map((v) => v === '—' ? null : v));
                  return (
                    <td
                      key={`${col.product.productId}-${row.label}-${i}`}
                      className={`px-3 py-2 text-foreground ${highlight} ${value === '—' ? 'text-muted-foreground' : ''}`}
                    >
                      {value}
                    </td>
                  );
                })}
              </tr>
            );
          })}

          {/* 구분선 */}
          {comparisonItems.length > 0 && (
            <tr>
              <td colSpan={columns.length + 1} className="px-3 py-1.5 bg-muted/30 text-xs font-medium text-muted-foreground border-b border-border">
                보장 내용 비교
              </td>
            </tr>
          )}

          {/* 보장 항목 비교 행 */}
          {comparisonItems.map((item) => {
            const allValues = columns.map((col) => getCoverageValue(col.product, item));
            return (
              <tr key={item} className="border-b border-border hover:bg-muted/30">
                <td className="sticky left-0 z-10 bg-background px-3 py-2 font-medium text-foreground border-r border-border">
                  {item}
                </td>
                {columns.map((col, i) => {
                  const value = getCoverageValue(col.product, item);
                  const highlight = getHighlightClass(item, value, allValues);
                  return (
                    <td
                      key={`${col.product.productId}-${item}-${i}`}
                      className={`px-3 py-2 text-foreground ${highlight} ${!value ? 'text-muted-foreground' : ''}`}
                    >
                      {value ?? '—'}
                    </td>
                  );
                })}
              </tr>
            );
          })}

          {/* 면책/감액 요약 */}
          <tr>
            <td colSpan={columns.length + 1} className="px-3 py-1.5 bg-muted/30 text-xs font-medium text-muted-foreground border-b border-border">
              면책/감액 사항
            </td>
          </tr>
          <tr className="border-b border-border">
            <td className="sticky left-0 z-10 bg-background px-3 py-2 font-medium text-foreground border-r border-border">
              면책/감액
            </td>
            {columns.map((col, i) => (
              <td key={`exemption-${col.product.productId}-${i}`} className="px-3 py-2 text-foreground">
                {(col.product.exemptions ?? []).length > 0 ? (
                  <ul className="list-disc list-inside space-y-0.5">
                    {(col.product.exemptions ?? []).slice(0, 3).map((ex, j) => (
                      <li key={j} className="text-xs">
                        {ex.name}{ex.period ? ` (${ex.period})` : ''}
                      </li>
                    ))}
                    {(col.product.exemptions ?? []).length > 3 && (
                      <li className="text-xs text-muted-foreground">외 {(col.product.exemptions ?? []).length - 3}건</li>
                    )}
                  </ul>
                ) : (
                  <span className="text-muted-foreground">—</span>
                )}
              </td>
            ))}
          </tr>
        </tbody>
      </table>
    </div>
  );
}
