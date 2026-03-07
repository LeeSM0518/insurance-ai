import {
  createContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from 'react';
import { useNavigate } from 'react-router';
import type { LoginResponse, UserResponse } from '@insurance-ai/api-client';
import { getApi } from '@/lib/api';
import '@/lib/api-interceptor';
import {
  getAccessToken,
  getRefreshToken,
  setAccessToken,
  setRefreshToken,
  clearTokens,
} from '@/lib/token-storage';

export interface AuthContextValue {
  user: UserResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (loginResponse: LoginResponse) => void;
  logout: () => Promise<void>;
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  // 토큰이 없으면 로딩 없이 바로 비인증 상태로 시작
  const [isLoading, setIsLoading] = useState(() => !!getAccessToken());
  const navigate = useNavigate();

  const isAuthenticated = user !== null;

  // 초기 마운트 시 토큰 검증 (토큰이 있을 때만 실행)
  useEffect(() => {
    if (!isLoading) return;

    getApi()
      .getMe()
      .then((response) => {
        setUser(response.data.data ?? null);
      })
      .catch(() => {
        clearTokens();
        setUser(null);
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // auth:logout 이벤트 수신
  useEffect(() => {
    const handleLogout = () => {
      setUser(null);
      navigate('/login', { replace: true });
    };
    window.addEventListener('auth:logout', handleLogout);
    return () => window.removeEventListener('auth:logout', handleLogout);
  }, [navigate]);

  const login = useCallback((loginResponse: LoginResponse) => {
    setAccessToken(loginResponse.accessToken);
    setRefreshToken(loginResponse.refreshToken);
    setUser(loginResponse.user);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      try {
        await getApi().logout({ logoutRequest: { refreshToken } });
      } catch {
        // 로그아웃 API 실패해도 로컬 토큰은 삭제
      }
    }
    clearTokens();
    setUser(null);
    navigate('/login', { replace: true });
  }, [navigate]);

  return (
    <AuthContext.Provider
      value={{ user, isAuthenticated, isLoading, login, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}
