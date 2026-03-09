import type { AxiosError } from 'axios';

type ErrorSource =
  | 'api'        // HTTP API 호출
  | 'sse'        // SSE 스트리밍
  | 'auth'       // 인증 관련
  | 'render'     // React 렌더링
  | 'hook'       // 커스텀 훅
  | 'page'       // 페이지 컴포넌트
  | 'global';    // 전역 (unhandled)

interface ErrorLogOptions {
  source: ErrorSource;
  message: string;
  error?: unknown;
  context?: Record<string, unknown>;
}

function isAxiosError(error: unknown): error is AxiosError {
  return (
    typeof error === 'object' &&
    error !== null &&
    'isAxiosError' in error &&
    (error as AxiosError).isAxiosError === true
  );
}

export function logError({ source, message, error, context }: ErrorLogOptions): void {
  const timestamp = new Date().toLocaleTimeString('ko-KR');
  const tag = `[${timestamp}] [${source.toUpperCase()}]`;

  console.error(`${tag} ${message}`);

  if (error) {
    if (isAxiosError(error)) {
      console.error(`${tag}   → Status: ${error.response?.status}`);
      console.error(`${tag}   → URL: ${error.config?.method?.toUpperCase()} ${error.config?.url}`);
      if (error.response?.data) {
        console.error(`${tag}   → Response:`, error.response.data);
      }
    } else if (error instanceof Error) {
      console.error(`${tag}   → ${error.message}`);
      if (error.stack) {
        console.error(`${tag}   → Stack:`, error.stack);
      }
    } else {
      console.error(`${tag}   →`, error);
    }
  }

  if (context) {
    console.error(`${tag}   → context:`, context);
  }
}
