import { useState, useMemo, useEffect } from 'react';
import { useSearchParams } from 'react-router';
import { Loader2, SearchX } from 'lucide-react';
import { SearchBar } from '@/features/search/search-bar';
import { ActiveFilters } from '@/features/search/active-filters';
import { SearchResultCount } from '@/features/search/search-result-count';
import { PolicyCard } from '@/features/search/policy-card';
import { EmptyState } from '@/components/ui/empty-state';
import { Button } from '@/components/ui/button';
import { parseSearchParams, buildSearchParams } from '@/lib/search-params';
import { useSearch } from '@/hooks/use-search';
import { useSearchFilters } from '@/hooks/use-search-filters';
import { trackEvent } from '@/lib/analytics';
import type { SearchFilters, ContentType } from '@/types/search';

export default function SearchResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { query, filters: initialFilters } = useMemo(() => parseSearchParams(searchParams), [searchParams]);
  const [filters, setFilters] = useState<SearchFilters>(initialFilters);
  const { results, totalCount, hasNext, isLoading, error, search, loadMore } = useSearch();
  const { companies } = useSearchFilters();

  // query 또는 filters 변경 시 검색 실행
  useEffect(() => {
    if (query) {
      search(query, filters);
    }
  }, [query, filters, search]);

  // 검색 실행 (URL 업데이트)
  const handleSearch = (newQuery: string) => {
    trackEvent('search', 'execute', newQuery);
    setSearchParams(buildSearchParams(newQuery, filters));
  };

  // 필터 변경 시 URL 업데이트
  const updateFilters = (newFilters: SearchFilters) => {
    setFilters(newFilters);
    setSearchParams(buildSearchParams(query, newFilters));
  };

  // 필터 제거 핸들러
  const removeCompany = (id: string) => {
    updateFilters({ ...filters, insuranceCompanyIds: filters.insuranceCompanyIds.filter((c) => c !== id) });
  };
  const removeInsuranceType = () => {
    updateFilters({ ...filters, insuranceType: null, categories: [] });
  };
  const removeCategory = (code: string) => {
    updateFilters({ ...filters, categories: filters.categories.filter((c) => c !== code) });
  };
  const removeContentType = (type: ContentType) => {
    updateFilters({ ...filters, contentTypes: filters.contentTypes.filter((t) => t !== type) });
  };

  return (
    <div className="flex-1 flex flex-col">
      <div className="px-4 pt-4 space-y-3">
        {/* 검색바 */}
        <SearchBar onSearch={handleSearch} defaultValue={query} />

        {/* 적용 필터 칩 */}
        <ActiveFilters
          filters={filters}
          companies={companies}
          onRemoveCompany={removeCompany}
          onRemoveInsuranceType={removeInsuranceType}
          onRemoveCategory={removeCategory}
          onRemoveContentType={removeContentType}
        />

        {/* 결과 카운트 */}
        <SearchResultCount count={totalCount} />
      </div>

      {/* 결과 리스트 */}
      <div className="flex-1 px-4 py-3">
        {isLoading && results.length === 0 ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="size-6 animate-spin text-muted-foreground" />
          </div>
        ) : error ? (
          <EmptyState
            icon={SearchX}
            title="검색 중 오류가 발생했습니다"
            description="잠시 후 다시 시도해주세요"
          />
        ) : results.length > 0 ? (
          <>
            <div className="grid grid-cols-1 desktop:grid-cols-2 gap-3">
              {results.map((item) => (
                <PolicyCard key={item.id} item={item} />
              ))}
            </div>
            {/* 더 보기 버튼 */}
            {hasNext && (
              <div className="flex justify-center py-4">
                <Button variant="outline" onClick={() => { trackEvent('search', 'load_more'); loadMore(); }} disabled={isLoading}>
                  {isLoading ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    '더 보기'
                  )}
                </Button>
              </div>
            )}
          </>
        ) : query ? (
          <EmptyState icon={SearchX} title="검색 결과가 없습니다" description="다른 검색어로 시도해보세요" />
        ) : null}
      </div>

    </div>
  );
}
