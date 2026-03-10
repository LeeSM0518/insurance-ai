import { useState } from 'react';
import { useNavigate } from 'react-router';
import { SearchBar } from '@/features/search/search-bar';
import { FilterChips } from '@/features/search/filter-chips';
import { RecentSearchChips } from '@/features/search/recent-search-chips';
import { SuggestedSearches } from '@/features/search/suggested-searches';
import { useRecentSearches } from '@/hooks/use-recent-searches';
import { useSearchFilters } from '@/hooks/use-search-filters';
import { buildSearchParams } from '@/lib/search-params';
import { trackEvent } from '@/lib/analytics';
import type { SearchFilters } from '@/types/search';

const INITIAL_FILTERS: SearchFilters = {
  insuranceCompanyIds: [],
  insuranceType: null,
  categories: [],
  contentTypes: [],
};

export default function SearchPage() {
  const navigate = useNavigate();
  const { recentSearches, addSearch, removeSearch } = useRecentSearches();
  const [filters, setFilters] = useState<SearchFilters>(INITIAL_FILTERS);
  const { companies, categories, suggestions, loading } = useSearchFilters();

  const handleSearch = (query: string) => {
    trackEvent('search', 'execute', query);
    addSearch(query);
    const params = buildSearchParams(query, filters);
    navigate(`/search/results?${params}`);
  };

  const handleRecentSelect = (query: string) => {
    trackEvent('search', 'recent_click', query);
    handleSearch(query);
  };

  const handleSuggestionSelect = (query: string) => {
    trackEvent('search', 'suggested_click', query);
    handleSearch(query);
  };

  return (
    <div className="flex-1 px-4 py-6 max-w-2xl mx-auto w-full space-y-6">
      <h1 className="text-2xl desktop:text-3xl font-bold text-foreground">
        전 보험사 약관을 AI로 검색하세요
      </h1>

      <SearchBar onSearch={handleSearch} />

      {loading ? (
        <div className="space-y-3 animate-pulse">
          <div className="h-4 w-16 bg-muted rounded" />
          <div className="flex gap-1.5">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-7 w-16 bg-muted rounded-full" />
            ))}
          </div>
        </div>
      ) : (
        <FilterChips
          filters={filters}
          onFiltersChange={setFilters}
          companies={companies}
          categories={categories}
        />
      )}

      <RecentSearchChips searches={recentSearches} onSelect={handleRecentSelect} onRemove={removeSearch} />

      <SuggestedSearches suggestions={suggestions} onSelect={handleSuggestionSelect} />
    </div>
  );
}
