import { Configuration, DefaultApi, AIApi, ComparisonApi } from '@insurance-ai/api-client';
import axios from 'axios';
import type { AxiosInstance } from 'axios';

// 공유 axios 인스턴스 (인터셉터 적용 대상)
// baseURL은 빈 문자열: OpenAPI Generator 클라이언트가 '/api/v1/...' 전체 경로를 사용
// Vite proxy가 '/api' 경로를 localhost:8080으로 전달
export const axiosInstance: AxiosInstance = axios.create({
  baseURL: '',
  timeout: 30000,
});

// Configuration (토큰은 인터셉터에서 관리)
const sharedConfiguration = new Configuration({
  basePath: '',
});

// API 팩토리 함수
export const getApi = (): DefaultApi =>
  new DefaultApi(sharedConfiguration, undefined, axiosInstance);

export const getAIApi = (): AIApi =>
  new AIApi(sharedConfiguration, undefined, axiosInstance);

export const getComparisonApi = (): ComparisonApi =>
  new ComparisonApi(sharedConfiguration, undefined, axiosInstance);
