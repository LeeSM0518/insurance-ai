import type { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { axiosInstance, getApi } from './api';
import {
  getAccessToken,
  getRefreshToken,
  setAccessToken,
  setRefreshToken,
  setExpiresAt,
  clearTokens,
  needsProactiveRefresh,
} from './token-storage';

interface FailedRequest {
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}

let isRefreshing = false;
let failedQueue: FailedRequest[] = [];

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach((req) => {
    if (token) {
      req.resolve(token);
    } else {
      req.reject(error);
    }
  });
  failedQueue = [];
}

// 갱신 로직 공통 함수
async function performRefresh(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;

  const response = await getApi().refresh({
    tokenRefreshRequest: { refreshToken },
  });
  const data = response.data.data;
  if (!data) return null;

  setAccessToken(data.accessToken);
  setRefreshToken(data.refreshToken);
  setExpiresAt(Date.now() + data.expiresIn * 1000);
  return data.accessToken;
}

// Request 인터셉터: Authorization 헤더 추가 + 사전 갱신
axiosInstance.interceptors.request.use(async (config) => {
  // refresh 요청 자체는 인터셉터 건너뛰기
  if (config.url?.includes('/auth/refresh')) {
    return config;
  }

  const token = getAccessToken();
  if (!token) return config;

  // 사전 갱신: 만료 2분 전이면 선제 갱신 시도
  if (needsProactiveRefresh() && !isRefreshing) {
    isRefreshing = true;
    try {
      const newToken = await performRefresh();
      if (newToken) {
        config.headers.Authorization = `Bearer ${newToken}`;
        processQueue(null, newToken);
        return config;
      }
    } catch {
      // 사전 갱신 실패 시 기존 토큰으로 진행 (401 fallback에서 처리)
    } finally {
      isRefreshing = false;
    }
  } else if (isRefreshing) {
    // 이미 갱신 중이면 큐에 추가하고 대기
    return new Promise((resolve, reject) => {
      failedQueue.push({
        resolve: (newToken) => {
          config.headers.Authorization = `Bearer ${newToken}`;
          resolve(config);
        },
        reject,
      });
    });
  }

  config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Transient 에러 재시도 설정
const MAX_TRANSIENT_RETRIES = 2;
const INITIAL_BACKOFF_MS = 1000;
const RETRYABLE_STATUS_CODES = [408, 429, 500, 502, 503, 504];

// 재시도하지 않는 비멱등 POST 패턴
const NON_RETRYABLE_POST_PATTERNS = [
  /\/api\/v1\/chat\/sessions\/[^/]+\/messages$/,
];

type RetryableRequest = InternalAxiosRequestConfig & {
  _retry?: boolean;
  _retryCount?: number;
};

function isRetryableError(
  config: RetryableRequest,
  error: AxiosError,
): boolean {
  if ((config._retryCount || 0) >= MAX_TRANSIENT_RETRIES) return false;

  // 비멱등 POST는 재시도하지 않음
  if (
    config.method === 'post' &&
    NON_RETRYABLE_POST_PATTERNS.some((p) => p.test(config.url || ''))
  ) {
    return false;
  }

  // 응답 없음 (네트워크 에러/타임아웃)
  if (!error.response) return true;

  return RETRYABLE_STATUS_CODES.includes(error.response.status);
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Response 인터셉터: 401 시 토큰 갱신 + Transient 재시도
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetryableRequest;

    // 401이 아니거나 이미 재시도한 요청
    if (error.response?.status !== 401 || originalRequest._retry) {
      // Transient 에러 재시도
      if (isRetryableError(originalRequest, error)) {
        const retryCount = (originalRequest._retryCount || 0) + 1;
        originalRequest._retryCount = retryCount;

        // 429 Retry-After 헤더 존중
        const retryAfter = error.response?.headers?.['retry-after'];
        const backoffMs = retryAfter
          ? parseInt(retryAfter, 10) * 1000
          : INITIAL_BACKOFF_MS * Math.pow(2, retryCount - 1);

        await delay(backoffMs);
        return axiosInstance(originalRequest);
      }
      return Promise.reject(error);
    }

    // refresh 요청 자체의 401/403 → 무한 루프 방지
    if (originalRequest.url?.includes('/auth/refresh')) {
      clearTokens();
      window.dispatchEvent(new Event('auth:logout'));
      return Promise.reject(error);
    }

    // 이미 갱신 중이면 큐에 추가
    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      }).then((token) => {
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return axiosInstance(originalRequest);
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const newToken = await performRefresh();
      if (!newToken) throw new Error('Refresh failed');

      processQueue(null, newToken);
      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return axiosInstance(originalRequest);
    } catch (refreshError) {
      processQueue(refreshError, null);
      clearTokens();
      window.dispatchEvent(new Event('auth:logout'));
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);
