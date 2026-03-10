const ACCESS_TOKEN_KEY = 'insurance_ai_access_token';
const REFRESH_TOKEN_KEY = 'insurance_ai_refresh_token';
const EXPIRES_AT_KEY = 'insurance_ai_token_expires_at';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token);
}

export function getExpiresAt(): number | null {
  const val = localStorage.getItem(EXPIRES_AT_KEY);
  return val ? parseInt(val, 10) : null;
}

export function setExpiresAt(expiresAt: number): void {
  localStorage.setItem(EXPIRES_AT_KEY, expiresAt.toString());
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(EXPIRES_AT_KEY);
}

// 만료 2분 전이면 사전 갱신 필요
const REFRESH_BUFFER_MS = 2 * 60 * 1000;

export function needsProactiveRefresh(): boolean {
  const expiresAt = getExpiresAt();
  if (!expiresAt) return false;
  return expiresAt - Date.now() < REFRESH_BUFFER_MS;
}
