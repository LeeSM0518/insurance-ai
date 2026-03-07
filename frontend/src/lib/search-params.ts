import type { SearchFilters, ContentType } from '@/types/search';

// URL 쿼리스트링 → SearchFilters 역파싱
export function parseSearchParams(params: URLSearchParams): { query: string; filters: SearchFilters } {
  const query = params.get('q') || '';
  const filters: SearchFilters = {
    insuranceCompanyIds: params.get('companies')?.split(',').filter(Boolean) || [],
    insuranceType: (params.get('type') as SearchFilters['insuranceType']) || null,
    categories: params.get('categories')?.split(',').filter(Boolean) || [],
    contentTypes: (params.get('contentTypes')?.split(',').filter(Boolean) as ContentType[]) || [],
  };
  return { query, filters };
}

export function buildSearchParams(query: string, filters: SearchFilters): URLSearchParams {
  const params = new URLSearchParams();

  if (query) {
    params.set('q', query);
  }

  if (filters.insuranceCompanyIds.length > 0) {
    params.set('companies', filters.insuranceCompanyIds.join(','));
  }

  if (filters.insuranceType) {
    params.set('type', filters.insuranceType);
  }

  if (filters.categories.length > 0) {
    params.set('categories', filters.categories.join(','));
  }

  if (filters.contentTypes.length > 0) {
    params.set('contentTypes', filters.contentTypes.join(','));
  }

  return params;
}
