import { useCallback, useEffect, useSyncExternalStore } from 'react';
import { axiosInstance } from '@/lib/api';
import type { SurrenderValue, PremiumExample, PriceIndex } from '@/types/policy';

export interface UseSupplementaryDataReturn {
  surrenderValues: SurrenderValue[] | null;
  premiumExamples: PremiumExample[] | null;
  priceIndices: PriceIndex[] | null;
  isLoading: boolean;
}

// 캐시 기반 보충 데이터 스토어
interface CacheEntry {
  surrenderValues: SurrenderValue[] | null;
  premiumExamples: PremiumExample[] | null;
  priceIndices: PriceIndex[] | null;
  isLoading: boolean;
}

// 안정적인 참조를 위한 상수 객체들
const EMPTY_ENTRY: CacheEntry = { surrenderValues: null, premiumExamples: null, priceIndices: null, isLoading: false };
const LOADING_ENTRY: CacheEntry = { surrenderValues: null, premiumExamples: null, priceIndices: null, isLoading: true };

const cache = new Map<string, CacheEntry>();
const listeners = new Set<() => void>();

function emitChange() {
  listeners.forEach((listener) => listener());
}

function getEntry(productId: string | null): CacheEntry {
  if (!productId) return EMPTY_ENTRY;
  return cache.get(productId) ?? LOADING_ENTRY;
}

async function fetchSupplementaryData(productId: string): Promise<void> {
  if (cache.has(productId)) return;

  cache.set(productId, LOADING_ENTRY);
  emitChange();

  const [svResult, peResult, piResult] = await Promise.allSettled([
    axiosInstance.get<{ data: { values: SurrenderValue[] } }>(
      `/api/v1/products/${productId}/surrender-values`,
    ),
    axiosInstance.get<{ data: { examples: PremiumExample[] } }>(
      `/api/v1/products/${productId}/premium-examples`,
    ),
    axiosInstance.get<{ data: { indices: PriceIndex[] } }>(
      `/api/v1/products/${productId}/indices`,
    ),
  ]);

  const entry: CacheEntry = {
    surrenderValues:
      svResult.status === 'fulfilled' ? svResult.value.data.data?.values ?? null : null,
    premiumExamples:
      peResult.status === 'fulfilled' ? peResult.value.data.data?.examples ?? null : null,
    priceIndices:
      piResult.status === 'fulfilled' ? piResult.value.data.data?.indices ?? null : null,
    isLoading: false,
  };
  cache.set(productId, entry);
  emitChange();
}

function subscribe(callback: () => void) {
  listeners.add(callback);
  return () => listeners.delete(callback);
}

export function useSupplementaryData(productId: string | null): UseSupplementaryDataReturn {
  const getSnapshot = useCallback(() => getEntry(productId), [productId]);

  const data = useSyncExternalStore(subscribe, getSnapshot);

  // productId가 존재하고 캐시에 없으면 fetch 트리거
  useEffect(() => {
    if (productId && !cache.has(productId)) {
      fetchSupplementaryData(productId);
    }
  }, [productId]);

  return data;
}
