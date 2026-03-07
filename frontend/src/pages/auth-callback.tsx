import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '@/hooks/use-auth';
import { getApi } from '@/lib/api';
import { Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';

export default function AuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { login } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const isProcessing = useRef(false);

  useEffect(() => {
    const errorParam = searchParams.get('error');
    if (errorParam) {
      setError(
        errorParam === 'access_denied'
          ? '로그인이 취소되었습니다.'
          : `로그인 중 오류가 발생했습니다: ${errorParam}`,
      );
      return;
    }

    const code = searchParams.get('code');
    if (!code) {
      setError('인증 코드가 없습니다.');
      return;
    }

    // React Strict Mode 이중 호출 방지
    if (isProcessing.current) return;
    isProcessing.current = true;

    handleOAuthCallback(code);
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  async function handleOAuthCallback(code: string) {
    try {
      // authorization_code를 백엔드로 전송하여 토큰 교환 + 로그인 처리
      const loginResponse = await getApi().login({
        loginRequest: {
          code,
          redirectUri: import.meta.env.VITE_KAKAO_REDIRECT_URI,
        },
      });

      const loginData = loginResponse.data.data;
      if (!loginData) {
        throw new Error('Login response data is empty');
      }

      login(loginData);
      navigate('/', { replace: true });
    } catch {
      setError('로그인 처리 중 오류가 발생했습니다.');
    }
  }

  if (error) {
    return (
      <div className="min-h-dvh flex flex-col items-center justify-center gap-4 px-6">
        <p className="text-destructive text-center">{error}</p>
        <Button variant="outline" onClick={() => navigate('/login', { replace: true })}>
          다시 시도
        </Button>
      </div>
    );
  }

  return (
    <div className="min-h-dvh flex flex-col items-center justify-center gap-3">
      <Loader2 className="size-8 animate-spin text-primary" />
      <p className="text-muted-foreground">로그인 처리 중...</p>
    </div>
  );
}
