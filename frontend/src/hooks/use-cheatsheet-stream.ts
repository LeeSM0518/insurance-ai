import { useCallback, useRef, useState } from 'react';
import { streamCheatsheet } from '@/lib/cheatsheet-sse';
import type { Source } from '@/types/chat';
import type { CheatsheetRequest } from '@/types/report';

export type CheatsheetPhase = 'idle' | 'searching' | 'sources_found' | 'generating' | 'streaming' | 'done' | 'error';

export interface CheatsheetStreamState {
  phase: CheatsheetPhase;
  sources: Source[];
  streamingContent: string;
  errorMessage: string | null;
}

const BUFFER_INTERVAL = 80; // 토큰 버퍼링 간격 (ms)

export function useCheatsheetStream() {
  const [state, setState] = useState<CheatsheetStreamState>({
    phase: 'idle',
    sources: [],
    streamingContent: '',
    errorMessage: null,
  });

  const abortControllerRef = useRef<AbortController | null>(null);
  const tokenBufferRef = useRef('');
  const bufferTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const flushBuffer = useCallback(() => {
    const buffered = tokenBufferRef.current;
    if (buffered) {
      tokenBufferRef.current = '';
      setState((prev) => ({
        ...prev,
        streamingContent: prev.streamingContent + buffered,
      }));
    }
    bufferTimerRef.current = null;
  }, []);

  const generate = useCallback(
    (request: CheatsheetRequest) => {
      // 이전 스트림 정리
      abortControllerRef.current?.abort();
      if (bufferTimerRef.current) {
        clearTimeout(bufferTimerRef.current);
        bufferTimerRef.current = null;
      }
      tokenBufferRef.current = '';

      const controller = new AbortController();
      abortControllerRef.current = controller;

      setState({
        phase: 'searching',
        sources: [],
        streamingContent: '',
        errorMessage: null,
      });

      streamCheatsheet({
        request,
        signal: controller.signal,
        onSearching: () => {
          setState((prev) => ({ ...prev, phase: 'searching' }));
        },
        onSourcesFound: (sources) => {
          setState((prev) => ({ ...prev, phase: 'sources_found', sources }));
        },
        onGenerating: () => {
          setState((prev) => ({ ...prev, phase: 'generating' }));
        },
        onToken: (content) => {
          tokenBufferRef.current += content;
          // 첫 토큰이면 phase를 streaming으로 변경
          setState((prev) => {
            if (prev.phase !== 'streaming') {
              return { ...prev, phase: 'streaming' };
            }
            return prev;
          });
          // 버퍼 플러시 스케줄링
          if (!bufferTimerRef.current) {
            bufferTimerRef.current = setTimeout(flushBuffer, BUFFER_INTERVAL);
          }
        },
        onDone: () => {
          // 남은 버퍼 플러시
          if (bufferTimerRef.current) {
            clearTimeout(bufferTimerRef.current);
            bufferTimerRef.current = null;
          }
          const remaining = tokenBufferRef.current;
          tokenBufferRef.current = '';
          setState((prev) => ({
            ...prev,
            phase: 'done',
            streamingContent: prev.streamingContent + remaining,
          }));
        },
        onError: (message) => {
          setState((prev) => ({
            ...prev,
            phase: 'error',
            errorMessage: message,
          }));
        },
      }).catch((err) => {
        if (err instanceof Error && err.name === 'AbortError') return;
        setState((prev) => ({
          ...prev,
          phase: 'error',
          errorMessage: err instanceof Error ? err.message : '예기치 않은 오류가 발생했습니다',
        }));
      });
    },
    [flushBuffer],
  );

  const cancel = useCallback(() => {
    abortControllerRef.current?.abort();
    if (bufferTimerRef.current) {
      clearTimeout(bufferTimerRef.current);
      bufferTimerRef.current = null;
    }
    setState((prev) => ({ ...prev, phase: 'idle' }));
  }, []);

  const reset = useCallback(() => {
    abortControllerRef.current?.abort();
    if (bufferTimerRef.current) {
      clearTimeout(bufferTimerRef.current);
      bufferTimerRef.current = null;
    }
    tokenBufferRef.current = '';
    setState({
      phase: 'idle',
      sources: [],
      streamingContent: '',
      errorMessage: null,
    });
  }, []);

  return {
    ...state,
    isStreaming: state.phase !== 'idle' && state.phase !== 'done' && state.phase !== 'error',
    generate,
    cancel,
    reset,
  };
}
