import type { SearchFilters } from '@/types/search';

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
