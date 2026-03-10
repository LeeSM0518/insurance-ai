import { useState, useRef, useCallback, useEffect } from 'react';
import { axiosInstance } from '@/lib/api';
import { streamChatMessage, streamReply } from '@/lib/chat-sse';
import { logError } from '@/lib/error-logger';
import type { ChatMessage, Source } from '@/types/chat';
import type { DataSource } from '@/types/policy';

const MAX_RETRIES = 3;
const BASE_DELAY_MS = 1000;

// 스트리밍 상태 타입
export interface StreamingState {
  phase: 'idle' | 'searching' | 'sources_found' | 'generating' | 'streaming' | 'fading_out' | 'done' | 'error';
  sources: Source[];
  streamingContent: string;
  suggestedQuestions: string[];
  errorMessage: string | null;
  retryCount: number;
  isReconnecting: boolean;
  title: string | null;
}

export interface UseStreamingChatReturn {
  messages: ChatMessage[];
  streamingState: StreamingState;
  isStreaming: boolean;
  sendMessage: (content: string, isInitialMessage?: boolean, overrideSessionId?: string) => void;
  cancelStreaming: () => void;
  retryStreaming: () => void;
}

const INITIAL_STATE: StreamingState = {
  phase: 'idle',
  sources: [],
  streamingContent: '',
  suggestedQuestions: [],
  errorMessage: null,
  retryCount: 0,
  isReconnecting: false,
  title: null,
};

// 백엔드 메시지 응답 → ChatMessage 변환
function toMessage(raw: Record<string, unknown>): ChatMessage {
  const sources = (raw.sources as Record<string, unknown>[]) ?? [];
  return {
    id: String(raw.id),
    role: raw.role as 'USER' | 'ASSISTANT',
    content: String(raw.content),
    sources: sources.length > 0 ? sources.map((s) => ({
      articleId: String(s.articleId ?? ''),
      insuranceCompany: String(s.insuranceCompany ?? ''),
      productId: String(s.productId ?? ''),
      productName: String(s.productName ?? ''),
      contentType: String(s.contentType ?? ''),
      articleNumber: String(s.articleNumber ?? ''),
      articleTitle: String(s.articleTitle ?? ''),
      relevanceScore: Number(s.relevanceScore ?? 0),
      dataSource: (s.dataSource as DataSource) ?? 'ASSOCIATION',
      dataSourceId: s.dataSourceId ? String(s.dataSourceId) : undefined,
    })) : undefined,
    suggestedQuestions: Array.isArray(raw.suggestedQuestions) && (raw.suggestedQuestions as string[]).length > 0
      ? (raw.suggestedQuestions as string[])
      : undefined,
    createdAt: String(raw.createdAt),
  };
}

export function useStreamingChat(sessionId: string | null): UseStreamingChatReturn {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [streamingState, setStreamingState] = useState<StreamingState>(INITIAL_STATE);
  const abortRef = useRef<AbortController | null>(null);
  const lastMessageRef = useRef<string | null>(null);
  const lastIsInitialRef = useRef(false);
  const lastSessionIdRef = useRef<string | null>(null);
  // Ref로 스트리밍 중 누적 데이터 추적 (updater 내 side-effect 방지)
  const streamingDataRef = useRef<{ content: string; sources: Source[] }>({
    content: '',
    sources: [],
  });
  // 토큰 버퍼링: requestAnimationFrame으로 배치 렌더링 (60fps)
  const tokenBufferRef = useRef('');
  const rafIdRef = useRef<number>(0);

  // 세션 변경 시 메시지 이력 API 호출
  useEffect(() => {
    // 현재 스트리밍 중인 세션과 동일한 세션이면 abort 하지 않음
    // (새 세션 생성 직후 streamReply가 이미 시작된 경우)
    // abortRef.current !== null 조건으로 스트리밍이 실제 진행 중일 때만 건너뜀
    // (스트리밍 완료 후 세션 재전환 시 메시지 이력이 정상 로드되도록 보장)
    if (lastSessionIdRef.current === sessionId && abortRef.current !== null) {
      return;
    }

    abortRef.current?.abort();
    setStreamingState(INITIAL_STATE);

    if (!sessionId) {
      setMessages([]);
      return;
    }

    let cancelled = false;
    axiosInstance
      .get<{
        success: boolean;
        data: { sessionId: string; messages: Record<string, unknown>[] };
      }>(`/api/v1/chat/sessions/${sessionId}/messages`)
      .then((res) => {
        if (!cancelled) {
          setMessages(res.data.data.messages.map(toMessage));
        }
      })
      .catch((e) => {
        logError({
          source: 'hook',
          message: '채팅 메시지 이력 로드 실패',
          error: e,
          context: { sessionId },
        });
        if (!cancelled) setMessages([]);
      });

    return () => {
      cancelled = true;
    };
  }, [sessionId]);

  // 언마운트 시 cleanup
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
      if (rafIdRef.current) {
        clearTimeout(rafIdRef.current);
      }
    };
  }, []);

  const runStreamingRef = useRef<((content: string, isInitial: boolean, targetSessionId: string) => void) | undefined>(undefined);

  const runStreaming = useCallback(
    async (content: string, isInitialMessage: boolean, targetSessionId: string) => {
      const controller = new AbortController();
      abortRef.current = controller;

      // 스트리밍 데이터 및 토큰 버퍼 초기화
      streamingDataRef.current = { content: '', sources: [] };
      tokenBufferRef.current = '';
      if (rafIdRef.current) {
        clearTimeout(rafIdRef.current);
        rafIdRef.current = 0;
      }

      // 콜백 정의
      const callbacks = {
        onSearching: () => {
          setStreamingState((prev) => ({
            ...prev,
            phase: 'searching',
            isReconnecting: false,
          }));
        },
        onSourcesFound: (sources: Source[]) => {
          streamingDataRef.current.sources = sources;
          setStreamingState((prev) => ({
            ...prev,
            phase: 'sources_found',
            sources,
          }));
        },
        onGenerating: () => {
          setStreamingState((prev) => ({
            ...prev,
            phase: 'generating',
          }));
        },
        onToken: (token: string) => {
          streamingDataRef.current.content += token;
          tokenBufferRef.current += token;

          // 80ms 간격으로 토큰을 모아 배치 렌더링 (~12fps)
          // react-markdown 파싱 비용을 줄여 부드러운 스트리밍 효과 제공
          if (!rafIdRef.current) {
            rafIdRef.current = window.setTimeout(() => {
              const buffered = tokenBufferRef.current;
              tokenBufferRef.current = '';
              rafIdRef.current = 0;
              setStreamingState((prev) => ({
                ...prev,
                phase: 'streaming',
                streamingContent: prev.streamingContent + buffered,
              }));
            }, 80);
          }
        },
        onDone: ({ messageId, suggestedQuestions, title }: { messageId: string; suggestedQuestions: string[]; title?: string }) => {
          // fade-out phase 시작: 스트리밍 메시지가 서서히 사라진 뒤 최종 메시지로 교체
          setStreamingState((prev) => ({
            ...prev,
            phase: 'fading_out',
            title: title ?? null,
          }));

          const { content: streamedContent, sources } = streamingDataRef.current;
          const aiMessage: ChatMessage = {
            id: messageId,
            role: 'ASSISTANT',
            content: streamedContent,
            sources: sources.length > 0 ? sources : undefined,
            suggestedQuestions: suggestedQuestions.length > 0 ? suggestedQuestions : undefined,
            createdAt: new Date().toISOString(),
          };

          // fade-out 트랜지션 후 최종 메시지로 교체 (300ms)
          setTimeout(() => {
            setMessages((msgs) => [...msgs, aiMessage]);
            setStreamingState(INITIAL_STATE);
            lastMessageRef.current = null;
            lastIsInitialRef.current = false;
          }, 300);
        },
        onError: (errorMessage: string) => {
          setStreamingState((prev) => ({
            ...prev,
            phase: 'error',
            errorMessage,
            streamingContent: '',
          }));
        },
        signal: controller.signal,
      };

      try {
        if (isInitialMessage) {
          // 세션 생성 직후: /reply 엔드포인트 (USER 메시지 중복 저장 방지)
          await streamReply(targetSessionId, callbacks);
        } else {
          // 기존 세션: /messages 엔드포인트 (USER 메시지 저장 + AI 응답)
          await streamChatMessage({ ...callbacks, sessionId: targetSessionId, content });
        }
      } catch (e) {
        if (e instanceof DOMException && e.name === 'AbortError') return;
        logError({
          source: 'hook',
          message: '채팅 스트리밍 에러',
          error: e,
          context: { sessionId: targetSessionId },
        });

        // 에러 발생 시 재연결 시도
        setStreamingState((prev) => {
          const newRetryCount = prev.retryCount + 1;

          if (newRetryCount > MAX_RETRIES) {
            return {
              ...prev,
              phase: 'error' as const,
              errorMessage: '연결이 끊어졌습니다. 다시 시도해주세요.',
              isReconnecting: false,
              streamingContent: '',
            };
          }

          const retryDelay = BASE_DELAY_MS * Math.pow(2, newRetryCount - 1);
          setTimeout(() => {
            runStreamingRef.current?.(content, isInitialMessage, lastSessionIdRef.current!);
          }, retryDelay);

          return {
            ...prev,
            isReconnecting: true,
            retryCount: newRetryCount,
            streamingContent: '',
          };
        });
      } finally {
        abortRef.current = null;
      }
    },
    [], // sessionId를 파라미터로 받으므로 의존성 제거
  );

  // ref를 통해 최신 runStreaming 참조 유지 (순환 의존성 방지)
  runStreamingRef.current = runStreaming;

  const sendMessage = useCallback(
    (content: string, isInitialMessage = false, overrideSessionId?: string) => {
      if (streamingState.phase !== 'idle') return;

      const effectiveSessionId = overrideSessionId ?? sessionId;
      if (!effectiveSessionId) return;

      // 사용자 메시지를 UI에 즉시 추가 (낙관적 업데이트)
      const userMessage: ChatMessage = {
        id: `msg-${Date.now()}`,
        role: 'USER',
        content,
        createdAt: new Date().toISOString(),
      };
      if (isInitialMessage) {
        // 새 세션: useEffect 메시지 이력 로드를 건너뛰므로 낙관적 업데이트
        setMessages([userMessage]);
      } else {
        // 기존 세션: 기존 메시지에 추가
        setMessages((prev) => [...prev, userMessage]);
      }

      lastMessageRef.current = content;
      lastIsInitialRef.current = isInitialMessage;
      lastSessionIdRef.current = effectiveSessionId;

      // 스트리밍 상태 초기화
      setStreamingState({
        ...INITIAL_STATE,
        phase: 'searching',
      });

      runStreaming(content, isInitialMessage, effectiveSessionId);
    },
    [streamingState.phase, sessionId, runStreaming],
  );

  const cancelStreaming = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setStreamingState(INITIAL_STATE);
    lastMessageRef.current = null;
  }, []);

  const retryStreaming = useCallback(() => {
    const lastMessage = lastMessageRef.current;
    const lastSessionId = lastSessionIdRef.current;
    if (!lastMessage || !lastSessionId) return;

    // 부분 응답 삭제 (마지막 ASSISTANT 메시지가 있다면 제거)
    setMessages((prev) => {
      const last = prev[prev.length - 1];
      if (last && last.role === 'ASSISTANT') {
        return prev.slice(0, -1);
      }
      return prev;
    });

    // retryCount 초기화하고 재시도
    setStreamingState({
      ...INITIAL_STATE,
      phase: 'searching',
    });

    runStreaming(lastMessage, lastIsInitialRef.current, lastSessionId);
  }, [runStreaming]);

  return {
    messages,
    streamingState,
    isStreaming: streamingState.phase !== 'idle' && streamingState.phase !== 'error',
    sendMessage,
    cancelStreaming,
    retryStreaming,
  };
}
