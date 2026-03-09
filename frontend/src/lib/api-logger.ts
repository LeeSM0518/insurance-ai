// 개발 환경에서만 동작
const isDev = import.meta.env.DEV;

// 콘솔 스타일
const styles = {
  sse: 'color: #a855f7; font-weight: bold',        // 보라
  success: 'color: #22c55e; font-weight: bold',    // 초록
  error: 'color: #ef4444; font-weight: bold',      // 빨강
  dim: 'color: #9ca3af',                           // 회색
} as const;

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
