import { useState } from 'react';
import { useNavigate } from 'react-router';
import { SearchBar } from '@/features/search/search-bar';
import { FilterChips } from '@/features/search/filter-chips';
import { RecentSearchChips } from '@/features/search/recent-search-chips';
import { SuggestedSearches } from '@/features/search/suggested-searches';
import { useRecentSearches } from '@/hooks/use-recent-searches';
import { buildSearchParams } from '@/lib/search-params';
import { MOCK_INSURANCE_COMPANIES, MOCK_CATEGORIES, MOCK_SUGGESTIONS } from '@/mocks/search-data';
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

  const handleSearch = (query: string) => {
    addSearch(query);
    const params = buildSearchParams(query, filters);
    navigate(`/search/results?${params}`);
  };

  return (
    <div className="flex-1 px-4 py-6 max-w-2xl mx-auto w-full space-y-6">
      <h1 className="text-xl desktop:text-2xl font-bold text-foreground">
        전 보험사 약관을 AI로 검색하세요
      </h1>

      <SearchBar onSearch={handleSearch} />

      <FilterChips
        filters={filters}
        onFiltersChange={setFilters}
        companies={MOCK_INSURANCE_COMPANIES}
        categories={MOCK_CATEGORIES}
      />

      <RecentSearchChips searches={recentSearches} onSelect={handleSearch} onRemove={removeSearch} />

      <SuggestedSearches suggestions={MOCK_SUGGESTIONS} onSelect={handleSearch} />
    </div>
  );
}
