import { useState, useRef, useCallback, useEffect } from 'react';
import type { ChatMessage, Source } from '@/types/chat';
import {
  MOCK_SOURCES,
  MOCK_STREAMING_RESPONSE,
  MOCK_STREAMING_SUGGESTED,
  MOCK_MESSAGES,
} from '@/mocks/chat-data';

// 스트리밍 상태 타입
export interface StreamingState {
  phase: 'idle' | 'searching' | 'sources_found' | 'generating' | 'streaming' | 'done' | 'error';
  sources: Source[];
  streamingContent: string;
  suggestedQuestions: string[];
  errorMessage: string | null;
}

export interface UseStreamingChatReturn {
  messages: ChatMessage[];
  streamingState: StreamingState;
  isStreaming: boolean;
  sendMessage: (content: string) => void;
  cancelStreaming: () => void;
}

const INITIAL_STATE: StreamingState = {
  phase: 'idle',
  sources: [],
  streamingContent: '',
  suggestedQuestions: [],
  errorMessage: null,
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

      // Mock SSE 시뮬레이션 시작
      const controller = new AbortController();
      abortRef.current = controller;

      (async () => {
        try {
          // 1. searching (800ms)
          setStreamingState({
            ...INITIAL_STATE,
            phase: 'searching',
          });
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
        } catch (e) {
          if (e instanceof DOMException && e.name === 'AbortError') return;
          setStreamingState((prev) => ({
            ...prev,
            phase: 'error',
            errorMessage: '응답 생성 중 오류가 발생했습니다.',
          }));
        } finally {
          abortRef.current = null;
        }
      })();
    },
    [streamingState.phase],
  );

  const cancelStreaming = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setStreamingState(INITIAL_STATE);
  }, []);

  return {
    messages,
    streamingState,
    isStreaming: streamingState.phase !== 'idle',
    sendMessage,
    cancelStreaming,
  };
}
