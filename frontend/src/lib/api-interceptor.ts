import type { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { axiosInstance, getApi } from './api';
import { logRequest, logResponse, logError, getRequestStartTime } from './api-logger';
import {
  getAccessToken,
  getRefreshToken,
  setAccessToken,
  setRefreshToken,
  clearTokens,
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

// Request 인터셉터: Authorization 헤더 추가 + 로깅
axiosInstance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken();
  if (token && !config.url?.includes('/auth/refresh')) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  logRequest(config);
  return config;
});

// Response 인터셉터: 로깅 + 401 시 토큰 자동 갱신
axiosInstance.interceptors.response.use(
  (response) => {
    logResponse(response);
    return response;
  },
  async (error: AxiosError) => {
    const config = error.config;
    if (config) {
      const method = (config.method ?? 'GET').toUpperCase();
      logError(method, config.url ?? '', error.response?.status, error.response?.data, getRequestStartTime(config));
    }
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    // 401이 아니거나 이미 재시도한 요청이면 그대로 반환
    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    // refresh 요청 자체의 401 → 무한 루프 방지
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

    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      isRefreshing = false;
      clearTokens();
      window.dispatchEvent(new Event('auth:logout'));
      return Promise.reject(error);
    }

    try {
      const response = await getApi().refresh({
        tokenRefreshRequest: { refreshToken },
      });
      const data = response.data.data;
      if (!data) {
        throw new Error('Refresh response data is empty');
      }

      setAccessToken(data.accessToken);
      setRefreshToken(data.refreshToken);
      processQueue(null, data.accessToken);

      originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
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
