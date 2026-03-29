import { useCallback, useRef, useState } from 'react';
import { getComparisonApi } from '@/lib/api';
import { streamComparisonSummary } from '@/lib/comparison-sse';
import type {
  ComparisonRequest,
  ComparisonResponse,
  ComparisonCategoryInfo,
} from '@insurance-ai/api-client';

export type ComparisonPhase = 'idle' | 'loading' | 'result' | 'error';
export type SummaryPhase = 'idle' | 'generating' | 'streaming' | 'done' | 'error';

interface ComparisonState {
  phase: ComparisonPhase;
  data: ComparisonResponse | null;
  error: string | null;
  summaryPhase: SummaryPhase;
  summaryContent: string;
  summaryError: string | null;
}

const BUFFER_INTERVAL = 80;

export function useComparison() {
  const [state, setState] = useState<ComparisonState>({
    phase: 'idle',
    data: null,
    error: null,
    summaryPhase: 'idle',
    summaryContent: '',
    summaryError: null,
  });

  const [categories, setCategories] = useState<ComparisonCategoryInfo[]>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(false);

  const abortControllerRef = useRef<AbortController | null>(null);
  const tokenBufferRef = useRef('');
  const bufferTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastRequestRef = useRef<ComparisonRequest | null>(null);

  const flushBuffer = useCallback(() => {
    const buffered = tokenBufferRef.current;
    if (buffered) {
      tokenBufferRef.current = '';
      setState((prev) => ({
        ...prev,
        summaryContent: prev.summaryContent + buffered,
      }));
    }
    bufferTimerRef.current = null;
  }, []);

  /** 비교 데이터 조회 */
  const compare = useCallback(async (request: ComparisonRequest) => {
    lastRequestRef.current = request;
    setState((prev) => ({
      ...prev,
      phase: 'loading',
      data: null,
      error: null,
      summaryPhase: 'idle',
      summaryContent: '',
      summaryError: null,
    }));

    try {
      const api = getComparisonApi();
      const response = await api.compareProducts({ comparisonRequest: request });
      const data = response.data.data;
      if (!data) throw new Error('비교 데이터가 비어 있습니다');

      setState((prev) => ({
        ...prev,
        phase: 'result',
        data,
      }));
    } catch (err) {
      setState((prev) => ({
        ...prev,
        phase: 'error',
        error: err instanceof Error ? err.message : '비교 조회에 실패했습니다',
      }));
    }
  }, []);

  /** AI 요약 생성 (SSE 스트리밍) */
  const generateSummary = useCallback(
    (request?: ComparisonRequest) => {
      const req = request ?? lastRequestRef.current;
      if (!req) return;

      abortControllerRef.current?.abort();
      if (bufferTimerRef.current) {
        clearTimeout(bufferTimerRef.current);
        bufferTimerRef.current = null;
      }
      tokenBufferRef.current = '';

      const controller = new AbortController();
      abortControllerRef.current = controller;

      setState((prev) => ({
        ...prev,
        summaryPhase: 'generating',
        summaryContent: '',
        summaryError: null,
      }));

      streamComparisonSummary({
        request: req,
        signal: controller.signal,
        onGenerating: () => {
          setState((prev) => ({ ...prev, summaryPhase: 'generating' }));
        },
        onToken: (content) => {
          tokenBufferRef.current += content;
          setState((prev) => {
            if (prev.summaryPhase !== 'streaming') {
              return { ...prev, summaryPhase: 'streaming' };
            }
            return prev;
          });
          if (!bufferTimerRef.current) {
            bufferTimerRef.current = setTimeout(flushBuffer, BUFFER_INTERVAL);
          }
        },
        onDone: () => {
          if (bufferTimerRef.current) {
            clearTimeout(bufferTimerRef.current);
            bufferTimerRef.current = null;
          }
          const remaining = tokenBufferRef.current;
          tokenBufferRef.current = '';
          setState((prev) => ({
            ...prev,
            summaryPhase: 'done',
            summaryContent: prev.summaryContent + remaining,
          }));
        },
        onError: (message) => {
          setState((prev) => ({
            ...prev,
            summaryPhase: 'error',
            summaryError: message,
          }));
        },
      }).catch((err) => {
        if (err instanceof Error && err.name === 'AbortError') return;
        setState((prev) => ({
          ...prev,
          summaryPhase: 'error',
          summaryError: err instanceof Error ? err.message : '요약 생성에 실패했습니다',
        }));
      });
    },
    [flushBuffer],
  );

  /** 카테고리 목록 조회 */
  const fetchCategories = useCallback(async () => {
    setCategoriesLoading(true);
    try {
      const api = getComparisonApi();
      const response = await api.getCategories();
      setCategories(response.data.data ?? []);
    } catch {
      // 카테고리 조회 실패 시 무시
    } finally {
      setCategoriesLoading(false);
    }
  }, []);

  /** 초기화 */
  const reset = useCallback(() => {
    abortControllerRef.current?.abort();
    if (bufferTimerRef.current) {
      clearTimeout(bufferTimerRef.current);
      bufferTimerRef.current = null;
    }
    tokenBufferRef.current = '';
    lastRequestRef.current = null;
    setState({
      phase: 'idle',
      data: null,
      error: null,
      summaryPhase: 'idle',
      summaryContent: '',
      summaryError: null,
    });
  }, []);

  return {
    ...state,
    categories,
    categoriesLoading,
    isLoading: state.phase === 'loading',
    isSummaryStreaming: state.summaryPhase === 'generating' || state.summaryPhase === 'streaming',
    compare,
    generateSummary,
    fetchCategories,
    reset,
  };
}
