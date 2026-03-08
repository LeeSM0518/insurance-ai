import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios';

// 개발 환경에서만 동작
const isDev = import.meta.env.DEV;

// 요청 시작 시간 기록용
const REQUEST_START_TIME = Symbol('requestStartTime');

type TimedConfig = InternalAxiosRequestConfig & {
  [REQUEST_START_TIME]?: number;
};

// 콘솔 스타일
const styles = {
  request: 'color: #3b82f6; font-weight: bold',   // 파랑
  success: 'color: #22c55e; font-weight: bold',    // 초록
  error: 'color: #ef4444; font-weight: bold',      // 빨강
  sse: 'color: #a855f7; font-weight: bold',        // 보라
  dim: 'color: #9ca3af',                           // 회색
} as const;

/** body 요약 (너무 긴 경우 truncate) */
function summarizeBody(data: unknown): unknown {
  if (data === undefined || data === null) return undefined;
  const str = JSON.stringify(data);
  if (str.length > 500) {
    return JSON.parse(str.slice(0, 500) + '..."');
  }
  return data;
}

/** 메서드별 이모지 */
function methodEmoji(method: string): string {
  switch (method) {
    case 'GET': return '📥';
    case 'POST': return '📤';
    case 'PUT': return '📝';
    case 'PATCH': return '🔧';
    case 'DELETE': return '🗑️';
    default: return '📡';
  }
}

/** Axios 요청 로깅 — 인터셉터에서 호출 */
export function logRequest(config: InternalAxiosRequestConfig): void {
  if (!isDev) return;

  (config as TimedConfig)[REQUEST_START_TIME] = performance.now();
}

/** Axios 성공 응답 로깅 — 인터셉터에서 호출 */
export function logResponse(response: AxiosResponse): void {
  if (!isDev) return;

  const config = response.config as TimedConfig;
  const method = (config.method ?? 'GET').toUpperCase();
  const url = config.url ?? '';
  const startTime = config[REQUEST_START_TIME];
  const duration = startTime ? Math.round(performance.now() - startTime) : '?';

  console.groupCollapsed(
    `%c${methodEmoji(method)} [API] ${method} ${url} %c${response.status} %c(${duration}ms)`,
    styles.request,
    styles.success,
    styles.dim,
  );
  if (config.data) {
    console.log('%cRequest Body:', styles.dim, summarizeBody(config.data));
  }
  console.log('%cResponse:', styles.dim, summarizeBody(response.data));
  console.groupEnd();
}

/** Axios 에러 응답 로깅 — 인터셉터에서 호출 */
export function logError(method: string, url: string, status: number | undefined, errorData: unknown, startTime?: number): void {
  if (!isDev) return;

  const duration = startTime ? Math.round(performance.now() - startTime) : '?';

  console.groupCollapsed(
    `%c${methodEmoji(method)} [API] ${method} ${url} %c${status ?? 'NETWORK_ERROR'} %c(${duration}ms)`,
    styles.request,
    styles.error,
    styles.dim,
  );
  if (errorData) {
    console.log('%cError Data:', styles.dim, errorData);
  }
  console.trace('Stack');
  console.groupEnd();
}

/** Axios 에러에서 시작 시간 추출 */
export function getRequestStartTime(config: InternalAxiosRequestConfig): number | undefined {
  return (config as TimedConfig)[REQUEST_START_TIME];
}

// --- SSE 로깅 ---

/** SSE 스트림 시작 */
export function logSseStart(method: string, url: string, body?: unknown): void {
  if (!isDev) return;

  console.groupCollapsed(
    `%c🔴 [SSE] ${method} ${url}`,
    styles.sse,
  );
  if (body) {
    console.log('%cBody:', styles.dim, body);
  }
  console.groupEnd();
}

/** SSE 이벤트 수신 */
export function logSseEvent(url: string, eventType: string, data: unknown): void {
  if (!isDev) return;

  // token 이벤트는 너무 빈번하므로 간략하게 표시
  if (eventType === 'token') {
    const content = (data as { content?: string })?.content ?? '';
    console.log(`%c⚡ [SSE] %c${eventType}%c: "${content}"`, styles.sse, 'font-weight:bold', 'font-weight:normal');
    return;
  }

  console.log(`%c⚡ [SSE] %c${eventType}`, styles.sse, 'font-weight:bold', data);
}

/** SSE 스트림 완료 */
export function logSseComplete(url: string, duration: number): void {
  if (!isDev) return;

  console.log(
    `%c✅ [SSE] 스트림 완료 %c${url} %c(${Math.round(duration)}ms)`,
    styles.success,
    styles.dim,
    styles.dim,
  );
}

/** SSE 에러 */
export function logSseError(url: string, status: number | string, error?: unknown): void {
  if (!isDev) return;

  console.groupCollapsed(
    `%c❌ [SSE] ${url} %c${status}`,
    styles.error,
    styles.error,
  );
  if (error) {
    console.log('%cError:', styles.dim, error);
  }
  console.groupEnd();
}
