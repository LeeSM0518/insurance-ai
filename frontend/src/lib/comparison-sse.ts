import { getAccessToken } from './token-storage';
import { logSseStart, logSseEvent, logSseComplete, logSseError } from './api-logger';
import { logError } from './error-logger';
import type { ComparisonRequest } from '@insurance-ai/api-client';

interface ParsedSSEEvent {
  type: string;
  data: unknown;
}

export interface ComparisonSummarySSEOptions {
  request: ComparisonRequest;
  onGenerating: (message: string) => void;
  onToken: (content: string) => void;
  onDone: (data: { messageId: string; suggestedQuestions: string[] }) => void;
  onError: (error: string) => void;
  signal?: AbortSignal;
}

function parseSseEvents(chunk: string, buffer: string): { events: ParsedSSEEvent[]; remaining: string } {
  const combined = buffer + chunk;
  const parts = combined.split('\n\n');
  const remaining = parts.pop() ?? '';
  const events: ParsedSSEEvent[] = [];

  for (const part of parts) {
    const trimmed = part.trim();
    if (!trimmed) continue;

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
        message: '비교 요약 SSE 이벤트 JSON 파싱 실패',
        context: { rawData: jsonStr.substring(0, 200) },
      });
    }
  }

  return { events, remaining };
}

/** 비교 요약 AI 생성 SSE 스트리밍 */
export async function streamComparisonSummary(options: ComparisonSummarySSEOptions): Promise<void> {
  const { request, signal, ...callbacks } = options;
  const url = '/api/v1/compare/summary';

  logSseStart('POST', url, request);

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getAccessToken()}`,
    },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) {
    logSseError(url, response.status);
    if (response.status === 401) throw new Error('AUTH_EXPIRED');
    if (response.status === 429) throw new Error('잠시 후 다시 시도해주세요');
    throw new Error(`서버 오류가 발생했습니다 (${response.status})`);
  }

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
          case 'generating': {
            const data = event.data as { message: string };
            callbacks.onGenerating(data.message);
            break;
          }
          case 'token': {
            const data = event.data as { content: string };
            callbacks.onToken(data.content);
            break;
          }
          case 'done': {
            const data = event.data as { messageId: string; suggestedQuestions: string[] };
            callbacks.onDone({
              messageId: String(data.messageId),
              suggestedQuestions: data.suggestedQuestions ?? [],
            });
            break;
          }
          case 'error': {
            const data = event.data as { message: string };
            callbacks.onError(data.message);
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
