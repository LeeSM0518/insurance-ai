import { useEffect, useState } from 'react';
import { getApi } from '@/lib/api';
import type { InsuranceCompany, Category } from '@/types/search';
import type { CategoryInfo, InsuranceCompanyResponse } from '@insurance-ai/api-client';

// API 응답을 프론트엔드 타입으로 변환
function toInsuranceCompany(response: InsuranceCompanyResponse): InsuranceCompany {
  return {
    id: response.id,
    name: response.name,
    type: response.type as InsuranceCompany['type'],
  };
}

function toCategory(info: CategoryInfo): Category {
  return {
    code: info.code,
    name: info.name,
    insuranceType: info.insuranceType as Category['insuranceType'],
    subcategories: info.subcategories?.map((sub) => ({
      code: sub.code,
      name: sub.name,
    })),
  };
}

interface UseSearchFiltersResult {
  companies: InsuranceCompany[];
  categories: Category[];
  suggestions: string[];
  loading: boolean;
  error: string | null;
}

export function useSearchFilters(): UseSearchFiltersResult {
  const [companies, setCompanies] = useState<InsuranceCompany[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const api = getApi();
    let cancelled = false;

    async function fetchAll() {
      const results = await Promise.allSettled([
        api.getInsuranceCompanies(),
        api.getCategories1(),
        api.getSuggestions(),
      ]);

      if (cancelled) return;

      const [companiesResult, categoriesResult, suggestionsResult] = results;

      if (companiesResult.status === 'fulfilled') {
        setCompanies(companiesResult.value.data.data?.map(toInsuranceCompany) ?? []);
      }
      if (categoriesResult.status === 'fulfilled') {
        setCategories(categoriesResult.value.data.data?.map(toCategory) ?? []);
      }
      if (suggestionsResult.status === 'fulfilled') {
        setSuggestions(suggestionsResult.value.data.data ?? []);
      }

      // 전체 실패 시에만 에러 상태 설정
      const allFailed = results.every((r) => r.status === 'rejected');
      if (allFailed) {
        setError('필터 데이터를 불러올 수 없습니다.');
      }

      setLoading(false);
    }

    fetchAll();
    return () => {
      cancelled = true;
    };
  }, []);

  return { companies, categories, suggestions, loading, error };
}
