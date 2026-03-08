import { useState, useRef, useCallback, useEffect } from 'react';
import type { ChatMessage, Source } from '@/types/chat';
import {
  MOCK_SOURCES,
  MOCK_STREAMING_RESPONSE,
  MOCK_STREAMING_SUGGESTED,
  MOCK_MESSAGES,
} from '@/mocks/chat-data';

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
  sendMessage: (content: string) => void;
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

function delay(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener('abort', () => {
      clearTimeout(timer);
      reject(new DOMException('Aborted', 'AbortError'));
    });
  });
}

export function useStreamingChat(sessionId: string | null): UseStreamingChatReturn {
  // 세션의 기존 메시지를 초기값으로 설정
  const [messages, setMessages] = useState<ChatMessage[]>(() =>
    sessionId ? MOCK_MESSAGES[sessionId] || [] : [],
  );
  const [streamingState, setStreamingState] = useState<StreamingState>(INITIAL_STATE);
  const abortRef = useRef<AbortController | null>(null);
  const lastMessageRef = useRef<string | null>(null);

  // 세션 변경 시 메시지 초기화
  useEffect(() => {
    setMessages(sessionId ? MOCK_MESSAGES[sessionId] || [] : []);
    // 스트리밍 중이면 취소
    abortRef.current?.abort();
    setStreamingState(INITIAL_STATE);
  }, [sessionId]);

  // 언마운트 시 cleanup
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const runStreamingRef = useRef<(content: string) => void>();

  const runStreaming = useCallback(
    async (content: string) => {
      const controller = new AbortController();
      abortRef.current = controller;

      try {
        // 1. searching (800ms)
        setStreamingState((prev) => ({
          ...prev,
          phase: 'searching',
          isReconnecting: false,
        }));
        await delay(800, controller.signal);

        // 2. sources_found
        setStreamingState((prev) => ({
          ...prev,
          phase: 'sources_found',
          sources: MOCK_SOURCES,
        }));
        await delay(500, controller.signal);

        // 3. generating (600ms)
        setStreamingState((prev) => ({
          ...prev,
          phase: 'generating',
        }));
        await delay(600, controller.signal);

        // 4. streaming (문자 단위로 20ms 간격)
        setStreamingState((prev) => ({
          ...prev,
          phase: 'streaming',
          streamingContent: '',
        }));
        for (const char of MOCK_STREAMING_RESPONSE) {
          if (controller.signal.aborted) return;
          setStreamingState((prev) => ({
            ...prev,
            streamingContent: prev.streamingContent + char,
          }));
          await delay(20, controller.signal);
        }

        // 5. done
        const aiMessage: ChatMessage = {
          id: `msg-${Date.now()}`,
          role: 'ASSISTANT',
          content: MOCK_STREAMING_RESPONSE,
          sources: MOCK_SOURCES,
          suggestedQuestions: MOCK_STREAMING_SUGGESTED,
          createdAt: new Date().toISOString(),
        };
        setMessages((prev) => [...prev, aiMessage]);
        setStreamingState(INITIAL_STATE);
        lastMessageRef.current = null;
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
            runStreamingRef.current?.(content);
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
    [],
  );

  // ref를 통해 최신 runStreaming 참조 유지 (순환 의존성 방지)
  runStreamingRef.current = runStreaming;

  const sendMessage = useCallback(
    (content: string) => {
      if (streamingState.phase !== 'idle') return;

      // 사용자 메시지 추가
      const userMessage: ChatMessage = {
        id: `msg-${Date.now()}`,
        role: 'USER',
        content,
        createdAt: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, userMessage]);
      lastMessageRef.current = content;

      // 스트리밍 상태 초기화
      setStreamingState({
        ...INITIAL_STATE,
        phase: 'searching',
      });

      runStreaming(content);
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

    runStreaming(lastMessage);
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
