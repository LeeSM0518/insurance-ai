import { useState, useEffect } from 'react';
import { axiosInstance } from '@/lib/api';
import { logError } from '@/lib/error-logger';
import type { PolicyArticle } from '@/types/policy';

export interface UsePolicyDetailReturn {
  article: PolicyArticle | null;
  isLoading: boolean;
  error: string | null;
}

// 백엔드 응답 → 프론트엔드 PolicyArticle 변환
function toArticle(raw: Record<string, unknown>): PolicyArticle {
  const company = raw.insuranceCompany as Record<string, unknown>;
  const relatedArticles = (raw.relatedArticles as Record<string, unknown>[]) ?? [];

  return {
    id: String(raw.id),
    insuranceCompany: {
      id: String(company.id),
      name: String(company.name),
      type: company.type as 'LIFE' | 'NON_LIFE',
    },
    productId: String(raw.productId),
    productName: String(raw.productName),
    articleNumber: String(raw.articleNumber ?? ''),
    articleTitle: String(raw.articleTitle ?? ''),
    content: String(raw.content),
    contentType: String(raw.contentType),
    sectionType: String(raw.sectionType ?? ''),
    effectiveDate: String(raw.effectiveDate ?? ''),
    dataSource: (raw.dataSource as PolicyArticle['dataSource']) ?? 'ASSOCIATION',
    dataSourceId: raw.dataSourceId ? String(raw.dataSourceId) : undefined,
    hasPdfSource: Boolean(raw.hasPdfSource),
    linkedPdfDataSourceId: raw.linkedPdfDataSourceId
      ? String(raw.linkedPdfDataSourceId)
      : undefined,
    relatedArticles: relatedArticles.map((a) => ({
      id: String(a.id),
      articleNumber: String(a.articleNumber ?? ''),
      articleTitle: String(a.articleTitle ?? ''),
      contentType: String(a.contentType ?? ''),
    })),
  };
}

export function usePolicyDetail(articleId: string | undefined): UsePolicyDetailReturn {
  const [article, setArticle] = useState<PolicyArticle | null>(null);
  const [isLoading, setIsLoading] = useState(!!articleId);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!articleId) return;

    let cancelled = false;

    axiosInstance
      .get<{ success: boolean; data: Record<string, unknown> }>(
        `/api/v1/policies/${articleId}`,
      )
      .then((res) => {
        if (!cancelled) {
          setArticle(toArticle(res.data.data));
        }
      })
      .catch((e) => {
        logError({
          source: 'hook',
          message: '약관 조회 실패',
          error: e,
          context: { articleId },
        });
        if (!cancelled) {
          const status = (e as { response?: { status: number } }).response?.status;
          setError(status === 404 ? '약관을 찾을 수 없습니다' : '약관 조회 중 오류가 발생했습니다');
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
      setArticle(null);
      setIsLoading(true);
      setError(null);
    };
  }, [articleId]);

  return { article, isLoading, error };
}
