import { useCallback, useMemo, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ComparisonForm } from '@/features/compare/comparison-form';
import { ComparisonTable } from '@/features/compare/comparison-table';
import { ComparisonSummary } from '@/features/compare/comparison-summary';
import { useComparison } from '@/hooks/use-comparison';
import type { ComparisonRequest } from '@insurance-ai/api-client';

type PagePhase = 'form' | 'result';

export default function ComparePage() {
  const [pagePhase, setPagePhase] = useState<PagePhase>('form');
  const comparison = useComparison();

  // 카테고리 code → 한글 이름 매핑
  const categoryName = useMemo(() => {
    const code = comparison.data?.category;
    if (!code) return '';
    const found = comparison.categories.find((c) => c.code === code);
    return found?.name ?? code;
  }, [comparison.data?.category, comparison.categories]);

  const handleSubmit = useCallback(
    async (request: ComparisonRequest) => {
      setPagePhase('result');
      await comparison.compare(request);
    },
    [comparison],
  );

  const handleReset = useCallback(() => {
    comparison.reset();
    setPagePhase('form');
  }, [comparison]);

  return (
    <div className="flex-1 px-4 py-6 max-w-7xl mx-auto w-full">
      {pagePhase === 'form' && (
        <>
          <h1 className="text-2xl desktop:text-3xl font-bold text-foreground mb-6">
            보험 상품 비교 분석
          </h1>
          <ComparisonForm
            onSubmit={handleSubmit}
            isLoading={comparison.isLoading}
            categories={comparison.categories}
            categoriesLoading={comparison.categoriesLoading}
            onLoadCategories={comparison.fetchCategories}
          />
        </>
      )}

      {pagePhase === 'result' && (
        <>
          {/* 헤더 */}
          <div className="flex items-center gap-3 mb-6">
            <Button variant="ghost" size="sm" onClick={handleReset}>
              <ArrowLeft className="size-4 mr-1" />
              뒤로
            </Button>
            <h1 className="text-xl font-bold text-foreground">
              {categoryName} 비교 분석
            </h1>
            <div className="flex-1" />
            <Button variant="outline" size="sm" onClick={handleReset}>
              다시 비교
            </Button>
          </div>

          {/* 로딩 상태 */}
          {comparison.isLoading && (
            <div className="flex items-center justify-center py-12">
              <div className="flex items-center gap-2 text-muted-foreground">
                <div className="size-5 border-2 border-muted-foreground/30 border-t-muted-foreground rounded-full animate-spin" />
                <span>상품 데이터 비교 중...</span>
              </div>
            </div>
          )}

          {/* 에러 상태 */}
          {comparison.phase === 'error' && (
            <div className="flex flex-col items-center gap-4 py-12">
              <p className="text-sm text-destructive">{comparison.error}</p>
              <Button variant="outline" onClick={handleReset}>
                돌아가기
              </Button>
            </div>
          )}

          {/* 비교 결과 */}
          {comparison.data && (
            <>
              <ComparisonTable data={comparison.data} />
              <ComparisonSummary
                phase={comparison.summaryPhase}
                content={comparison.summaryContent}
                errorMessage={comparison.summaryError}
                onGenerate={() => comparison.generateSummary()}
                isStreaming={comparison.isSummaryStreaming}
              />
            </>
          )}
        </>
      )}
    </div>
  );
}
