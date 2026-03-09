import { useState, useCallback, useRef } from 'react';
import { axiosInstance } from '@/lib/api';
import { logError } from '@/lib/error-logger';
import type { SearchResultItem, SearchResponse } from '@/types/policy';
import type { SearchFilters } from '@/types/search';
import { toast } from 'sonner';

export interface UseSearchReturn {
  results: SearchResultItem[];
  totalCount: number;
  hasNext: boolean;
  isLoading: boolean;
  error: string | null;
  search: (query: string, filters: SearchFilters) => Promise<void>;
  loadMore: () => Promise<void>;
}

const PAGE_SIZE = 20;

export function useSearch(): UseSearchReturn {
  const [results, setResults] = useState<SearchResultItem[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pageRef = useRef(0);
  const queryRef = useRef('');
  const filtersRef = useRef<SearchFilters | null>(null);

  const search = useCallback(async (query: string, filters: SearchFilters) => {
    if (!query.trim()) return;

    queryRef.current = query;
    filtersRef.current = filters;
    pageRef.current = 0;
    setIsLoading(true);
    setError(null);

    try {
      const response = await axiosInstance.post<{
        success: boolean;
        data: SearchResponse;
      }>('/api/v1/search', {
        query,
        filters: {
          companyIds: filters.insuranceCompanyIds.length > 0 ? filters.insuranceCompanyIds : undefined,
          insuranceType: filters.insuranceType ?? undefined,
          categories: filters.categories.length > 0 ? filters.categories : undefined,
          contentTypes: filters.contentTypes.length > 0 ? filters.contentTypes : undefined,
        },
        page: 0,
        size: PAGE_SIZE,
      });

      const data = response.data.data;
      setResults(data.results);
      setTotalCount(data.totalCount);
      setHasNext(data.hasNext);
    } catch (e) {
      logError({
        source: 'hook',
        message: '검색 API 에러',
        error: e,
        context: { query },
      });
      const err = e as { response?: { status: number } };
      if (err.response?.status === 429) {
        toast.error('잠시 후 다시 시도해주세요');
      }
      setError('검색 중 오류가 발생했습니다');
      setResults([]);
      setTotalCount(0);
      setHasNext(false);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadMore = useCallback(async () => {
    if (!queryRef.current || !hasNext || isLoading) return;

    const nextPage = pageRef.current + 1;
    setIsLoading(true);

    try {
      const filters = filtersRef.current;
      const response = await axiosInstance.post<{
        success: boolean;
        data: SearchResponse;
      }>('/api/v1/search', {
        query: queryRef.current,
        filters: filters ? {
          companyIds: filters.insuranceCompanyIds.length > 0 ? filters.insuranceCompanyIds : undefined,
          insuranceType: filters.insuranceType ?? undefined,
          categories: filters.categories.length > 0 ? filters.categories : undefined,
          contentTypes: filters.contentTypes.length > 0 ? filters.contentTypes : undefined,
        } : undefined,
        page: nextPage,
        size: PAGE_SIZE,
      });

      const data = response.data.data;
      pageRef.current = nextPage;
      setResults((prev) => [...prev, ...data.results]);
      setTotalCount(data.totalCount);
      setHasNext(data.hasNext);
    } catch (e) {
      logError({
        source: 'hook',
        message: '검색 추가 결과 로드 실패',
        error: e,
        context: { query: queryRef.current, page: pageRef.current + 1 },
      });
      toast.error('추가 결과를 불러오는데 실패했습니다');
    } finally {
      setIsLoading(false);
    }
  }, [hasNext, isLoading]);

  return { results, totalCount, hasNext, isLoading, error, search, loadMore };
}
