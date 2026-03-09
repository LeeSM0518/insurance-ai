import { getAccessToken } from './token-storage';
import { logSseStart, logSseEvent, logSseComplete, logSseError } from './api-logger';
import { logError } from './error-logger';
import type { Source } from '@/types/chat';
import type { DataSource } from '@/types/policy';

// SSE 이벤트 파싱 결과
interface ParsedSSEEvent {
  type: string;
  data: unknown;
}

// SSE 스트리밍 옵션
export interface ChatSSEOptions {
  sessionId: string;
  content: string;
  onSearching: (message: string) => void;
  onSourcesFound: (sources: Source[]) => void;
  onGenerating: (message: string) => void;
  onToken: (content: string) => void;
  onDone: (data: { messageId: string; suggestedQuestions: string[] }) => void;
  onError: (error: string) => void;
  signal?: AbortSignal;
}

// SSE 응답의 SourceInfo → 프론트엔드 Source 변환
function toSource(raw: Record<string, unknown>): Source {
  return {
    articleId: String(raw.articleId ?? ''),
    insuranceCompany: String(raw.insuranceCompany ?? ''),
    productId: String(raw.productId ?? ''),
    productName: String(raw.productName ?? ''),
    contentType: String(raw.contentType ?? ''),
    articleNumber: String(raw.articleNumber ?? ''),
    articleTitle: String(raw.articleTitle ?? ''),
    relevanceScore: Number(raw.relevanceScore ?? 0),
    dataSource: (raw.dataSource as DataSource) ?? 'ASSOCIATION',
    dataSourceId: raw.dataSourceId ? String(raw.dataSourceId) : undefined,
  };
}

/**
 * SSE 이벤트 파서
 * Spring SSE는 event: 필드 없이 data: 라인만 전송
 * 불완전한 이벤트는 버퍼에 보관
 */
function parseSseEvents(chunk: string, buffer: string): { events: ParsedSSEEvent[]; remaining: string } {
  const combined = buffer + chunk;
  const parts = combined.split('\n\n');
  const remaining = parts.pop() ?? '';
  const events: ParsedSSEEvent[] = [];

  for (const part of parts) {
    const trimmed = part.trim();
    if (!trimmed) continue;

    // data: 라인 추출
    const dataLine = trimmed
      .split('\n')
      .find((line) => line.startsWith('data:'));
    if (!dataLine) continue;

    const jsonStr = dataLine.slice(5).trim();
    if (!jsonStr) continue;

    try {
      const parsed = JSON.parse(jsonStr) as { type: string; data: unknown };
      events.push(parsed);
    } catch {
      logError({
        source: 'sse',
        message: 'SSE 이벤트 JSON 파싱 실패',
        context: { rawData: jsonStr.substring(0, 200) },
      });
    }
  }

  return { events, remaining };
}

/** SSE 스트림 처리 공통 로직 */
async function processStream(
  response: Response,
  url: string,
  options: Omit<ChatSSEOptions, 'sessionId' | 'content'>,
): Promise<void> {
  const reader = response.body?.getReader();
  if (!reader) throw new Error('ReadableStream not supported');

  const decoder = new TextDecoder();
  let buffer = '';
  const streamStart = performance.now();

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      const chunk = decoder.decode(value, { stream: true });
      const { events, remaining } = parseSseEvents(chunk, buffer);
      buffer = remaining;

      for (const event of events) {
        logSseEvent(url, event.type, event.data);
        switch (event.type) {
          case 'searching': {
            const data = event.data as { message: string };
            options.onSearching(data.message);
            break;
          }
          case 'sources_found': {
            const data = event.data as { sources: Record<string, unknown>[] };
            options.onSourcesFound(data.sources.map(toSource));
            break;
          }
          case 'generating': {
            const data = event.data as { message: string };
            options.onGenerating(data.message);
            break;
          }
          case 'token': {
            const data = event.data as { content: string };
            options.onToken(data.content);
            break;
          }
          case 'done': {
            const data = event.data as {
              messageId: string;
              suggestedQuestions: string[];
            };
            options.onDone({
              messageId: String(data.messageId),
              suggestedQuestions: data.suggestedQuestions ?? [],
            });
            break;
          }
          case 'error': {
            const data = event.data as { message: string };
            options.onError(data.message);
            break;
          }
        }
      }
    }
  } finally {
    logSseComplete(url, performance.now() - streamStart);
    reader.releaseLock();
  }
}

/** 기존 세션에서 새 메시지 전송 (POST /messages) */
export async function streamChatMessage(options: ChatSSEOptions): Promise<void> {
  const { sessionId, content, signal, ...callbacks } = options;
  const url = `/api/v1/chat/sessions/${sessionId}/messages`;
  const body = { content };

  logSseStart('POST', url, body);

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getAccessToken()}`,
    },
    body: JSON.stringify(body),
    signal,
  });

  if (!response.ok) {
    logSseError(url, response.status);
    if (response.status === 401) throw new Error('AUTH_EXPIRED');
    if (response.status === 429) throw new Error('잠시 후 다시 시도해주세요');
    throw new Error(`서버 오류가 발생했습니다 (${response.status})`);
  }

  await processStream(response, url, callbacks);
}

/** 세션 생성 직후 initial_message에 대한 AI 응답 트리거 (POST /reply) */
export async function streamReply(
  sessionId: string,
  options: Omit<ChatSSEOptions, 'sessionId' | 'content'>,
): Promise<void> {
  const { signal, ...callbacks } = options;
  const url = `/api/v1/chat/sessions/${sessionId}/reply`;

  logSseStart('POST', url);

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${getAccessToken()}`,
    },
    signal,
  });

  if (!response.ok) {
    logSseError(url, response.status);
    if (response.status === 401) throw new Error('AUTH_EXPIRED');
    if (response.status === 429) throw new Error('잠시 후 다시 시도해주세요');
    throw new Error(`서버 오류가 발생했습니다 (${response.status})`);
  }

  await processStream(response, url, callbacks);
}
