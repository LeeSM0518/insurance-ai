import { useState, useMemo, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { Loader2, SearchX, BarChart3 } from 'lucide-react';
import { SearchBar } from '@/features/search/search-bar';
import { FilterChips } from '@/features/search/filter-chips';
import { SearchResultCount } from '@/features/search/search-result-count';
import { PolicyCard } from '@/features/search/policy-card';
import { EmptyState } from '@/components/ui/empty-state';
import { Button } from '@/components/ui/button';
import { parseSearchParams, buildSearchParams } from '@/lib/search-params';
import { useSearch } from '@/hooks/use-search';
import { useSearchFilters } from '@/hooks/use-search-filters';
import { trackEvent } from '@/lib/analytics';
import type { SearchFilters } from '@/types/search';

export default function SearchResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { query, filters: initialFilters } = useMemo(() => parseSearchParams(searchParams), [searchParams]);
  const [filters, setFilters] = useState<SearchFilters>(initialFilters);
  const { results, totalCount, hasNext, isLoading, error, search, loadMore } = useSearch();
  const { companies, categories } = useSearchFilters();
  const [compareMode, setCompareMode] = useState(false);
  const [selectedCompanyIds, setSelectedCompanyIds] = useState<Set<string>>(new Set());

  const toggleCompareSelection = (companyId: string) => {
    setSelectedCompanyIds((prev) => {
      const next = new Set(prev);
      if (next.has(companyId)) {
        next.delete(companyId);
      } else if (next.size < 5) {
        next.add(companyId);
      }
      return next;
    });
  };

  const handleGoToCompare = () => {
    const ids = Array.from(selectedCompanyIds).join(',');
    trackEvent('navigation', 'search_to_compare');
    navigate(`/compare?companyIds=${ids}`);
  };

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

  return (
    <div className="flex-1 flex flex-col">
      <div className="px-4 pt-4 space-y-3">
        {/* 검색바 */}
        <SearchBar onSearch={handleSearch} defaultValue={query} />

        {/* 필터 칩 */}
        <FilterChips
          filters={filters}
          onFiltersChange={updateFilters}
          companies={companies}
          categories={categories}
          compact
        />

        {/* 결과 카운트 + 비교 모드 토글 */}
        <div className="flex items-center justify-between">
          <SearchResultCount count={totalCount} />
          {results.length > 0 && (
            <Button
              variant={compareMode ? 'default' : 'outline'}
              size="sm"
              onClick={() => {
                setCompareMode((prev) => !prev);
                setSelectedCompanyIds(new Set());
              }}
            >
              <BarChart3 className="size-4 mr-1" />
              비교 모드
            </Button>
          )}
        </div>
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
                <div key={item.id} className="relative">
                  {compareMode && (
                    <button
                      className={`absolute top-3 right-3 z-10 size-6 rounded border-2 flex items-center justify-center transition-colors ${
                        selectedCompanyIds.has(item.insuranceCompany.id)
                          ? 'bg-primary border-primary text-primary-foreground'
                          : 'bg-background border-border hover:border-primary/50'
                      } ${!selectedCompanyIds.has(item.insuranceCompany.id) && selectedCompanyIds.size >= 5 ? 'opacity-40 cursor-not-allowed' : ''}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleCompareSelection(item.insuranceCompany.id);
                      }}
                    >
                      {selectedCompanyIds.has(item.insuranceCompany.id) && (
                        <span className="text-xs font-bold">✓</span>
                      )}
                    </button>
                  )}
                  <PolicyCard item={item} />
                </div>
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

      {/* 비교 모드 FAB */}
      {compareMode && selectedCompanyIds.size >= 2 && (
        <div className="fixed bottom-20 left-1/2 -translate-x-1/2 z-50">
          <Button onClick={handleGoToCompare} className="shadow-lg rounded-full px-6">
            <BarChart3 className="size-4 mr-2" />
            {selectedCompanyIds.size}개 보험사 비교 분석
          </Button>
        </div>
      )}
    </div>
  );
}
