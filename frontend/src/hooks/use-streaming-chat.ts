import { useState, useRef, useCallback, useEffect } from 'react';
import { axiosInstance } from '@/lib/api';
import { streamChatMessage, streamReply } from '@/lib/chat-sse';
import type { ChatMessage, Source } from '@/types/chat';
import type { DataSource } from '@/types/policy';

const MAX_RETRIES = 3;
const BASE_DELAY_MS = 1000;

// 스트리밍 상태 타입
export interface StreamingState {
  phase: 'idle' | 'searching' | 'sources_found' | 'generating' | 'streaming' | 'done' | 'error';
  sources: Source[];
  streamingContent: string;
  suggestedQuestions: string[];
  errorMessage: string | null;
  retryCount: number;
  isReconnecting: boolean;
}

export interface UseStreamingChatReturn {
  messages: ChatMessage[];
  streamingState: StreamingState;
  isStreaming: boolean;
  sendMessage: (content: string, isInitialMessage?: boolean) => void;
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
  // Ref로 스트리밍 중 누적 데이터 추적 (updater 내 side-effect 방지)
  const streamingDataRef = useRef<{ content: string; sources: Source[] }>({
    content: '',
    sources: [],
  });

  // 세션 변경 시 메시지 이력 API 호출
  useEffect(() => {
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
      .catch(() => {
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
    };
  }, []);

  const runStreamingRef = useRef<(content: string, isInitial: boolean) => void>();

  const runStreaming = useCallback(
    async (content: string, isInitialMessage: boolean) => {
      const controller = new AbortController();
      abortRef.current = controller;

      // 스트리밍 데이터 초기화
      streamingDataRef.current = { content: '', sources: [] };

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
          setStreamingState((prev) => ({
            ...prev,
            phase: 'streaming',
            streamingContent: prev.streamingContent + token,
          }));
        },
        onDone: ({ messageId, suggestedQuestions }: { messageId: string; suggestedQuestions: string[] }) => {
          // Ref에서 누적 데이터를 읽어 메시지 생성 (updater 내 side-effect 방지)
          const { content: streamedContent, sources } = streamingDataRef.current;
          const aiMessage: ChatMessage = {
            id: messageId,
            role: 'ASSISTANT',
            content: streamedContent,
            sources: sources.length > 0 ? sources : undefined,
            suggestedQuestions: suggestedQuestions.length > 0 ? suggestedQuestions : undefined,
            createdAt: new Date().toISOString(),
          };
          setMessages((msgs) => [...msgs, aiMessage]);
          setStreamingState(INITIAL_STATE);
          lastMessageRef.current = null;
          lastIsInitialRef.current = false;
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
          await streamReply(sessionId!, callbacks);
        } else {
          // 기존 세션: /messages 엔드포인트 (USER 메시지 저장 + AI 응답)
          await streamChatMessage({ ...callbacks, sessionId: sessionId!, content });
        }
      } catch (e) {
        if (e instanceof DOMException && e.name === 'AbortError') return;

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
            runStreamingRef.current?.(content, isInitialMessage);
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
    [sessionId],
  );

  // ref를 통해 최신 runStreaming 참조 유지 (순환 의존성 방지)
  runStreamingRef.current = runStreaming;

  const sendMessage = useCallback(
    (content: string, isInitialMessage = false) => {
      if (streamingState.phase !== 'idle') return;

      if (!isInitialMessage) {
        // 사용자 메시지를 UI에 즉시 추가 (낙관적 업데이트)
        const userMessage: ChatMessage = {
          id: `msg-${Date.now()}`,
          role: 'USER',
          content,
          createdAt: new Date().toISOString(),
        };
        setMessages((prev) => [...prev, userMessage]);
      }

      lastMessageRef.current = content;
      lastIsInitialRef.current = isInitialMessage;

      // 스트리밍 상태 초기화
      setStreamingState({
        ...INITIAL_STATE,
        phase: 'searching',
      });

      runStreaming(content, isInitialMessage);
    },
    [streamingState.phase, runStreaming],
  );

  const cancelStreaming = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setStreamingState(INITIAL_STATE);
    lastMessageRef.current = null;
  }, []);

  const retryStreaming = useCallback(() => {
    const lastMessage = lastMessageRef.current;
    if (!lastMessage) return;

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

    runStreaming(lastMessage, lastIsInitialRef.current);
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
