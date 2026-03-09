import { useState, useEffect, useCallback } from 'react';
import { axiosInstance } from '@/lib/api';
import { logError } from '@/lib/error-logger';
import type { ChatSession } from '@/types/chat';

export interface UseChatSessionsReturn {
  sessions: ChatSession[];
  isLoading: boolean;
  createSession: (initialMessage: string) => Promise<string>;
  deleteSession: (sessionId: string) => Promise<void>;
  refreshSessions: () => Promise<void>;
}

// 백엔드 응답 → ChatSession 변환
function toSession(raw: Record<string, unknown>): ChatSession {
  return {
    id: String(raw.id),
    title: String(raw.title ?? '새 대화'),
    lastMessagePreview: String(raw.lastMessagePreview ?? ''),
    createdAt: String(raw.createdAt),
    updatedAt: String(raw.updatedAt),
  };
}

export function useChatSessions(): UseChatSessionsReturn {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const fetchSessions = useCallback(async () => {
    setIsLoading(true);
    try {
      const response = await axiosInstance.get<{
        success: boolean;
        data: Record<string, unknown>[];
      }>('/api/v1/chat/sessions');
      setSessions(response.data.data.map(toSession));
    } catch (e) {
      logError({
        source: 'hook',
        message: '세션 목록 로드 실패',
        error: e,
      });
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSessions();
  }, [fetchSessions]);

  const createSession = useCallback(async (initialMessage: string): Promise<string> => {
    const response = await axiosInstance.post<{
      success: boolean;
      data: Record<string, unknown>;
    }>('/api/v1/chat/sessions', { initialMessage });

    const newSession = toSession(response.data.data);
    setSessions((prev) => [newSession, ...prev]);
    return newSession.id;
  }, []);

  const deleteSession = useCallback(async (sessionId: string): Promise<void> => {
    await axiosInstance.delete(`/api/v1/chat/sessions/${sessionId}`);
    setSessions((prev) => prev.filter((s) => s.id !== sessionId));
  }, []);

  return {
    sessions,
    isLoading,
    createSession,
    deleteSession,
    refreshSessions: fetchSessions,
  };
}
