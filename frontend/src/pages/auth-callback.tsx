import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '@/hooks/use-auth';
import { getApi } from '@/lib/api';
import { logError } from '@/lib/error-logger';
import { trackEvent } from '@/lib/analytics';
import { Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';

export default function AuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { login } = useAuth();
  const errorParam = searchParams.get('error');
  const code = searchParams.get('code');

  // 동기적으로 판별 가능한 초기 에러를 useState 초기값으로 설정
  const processedKey = code ? `oauth_code_processed_${code}` : null;
  const [error, setError] = useState<string | null>(() => {
    if (errorParam) {
      return errorParam === 'access_denied'
        ? '로그인이 취소되었습니다.'
        : `로그인 중 오류가 발생했습니다: ${errorParam}`;
    }
    if (!code) return '인증 코드가 없습니다.';
    if (processedKey && sessionStorage.getItem(processedKey)) {
      return '이미 처리된 인증 코드입니다. 다시 로그인해주세요.';
    }
    return null;
  });
  const isProcessing = useRef(false);

  useEffect(() => {
    if (!code || error) return;

    // React StrictMode 이중 호출 방지
    if (isProcessing.current) return;
    isProcessing.current = true;
    if (processedKey) sessionStorage.setItem(processedKey, 'true');

    let cancelled = false;

    getApi()
      .login({
        loginRequest: {
          code,
          redirectUri: import.meta.env.VITE_KAKAO_REDIRECT_URI,
        },
      })
      .then((loginResponse) => {
        if (cancelled) return;

        const loginData = loginResponse.data.data;
        if (!loginData) {
          logError({
            source: 'page',
            message: '카카오 로그인 응답 데이터 없음',
            context: { responseData: loginResponse.data },
          });
          setError('서버 응답에 로그인 정보가 없습니다.');
          return;
        }

        try {
          login(loginData);
        } catch (err) {
          logError({
            source: 'page',
            message: '로그인 토큰 저장 실패',
            error: err,
          });
          setError('로그인 정보 저장 중 오류가 발생했습니다.');
          return;
        }

        // 성공 시 code 처리 기록 정리 후 이동
        trackEvent('auth', 'login_success', 'kakao');
        sessionStorage.removeItem(`oauth_code_processed_${code}`);
        navigate('/', { replace: true });
      })
      .catch((err) => {
        if (cancelled) return;
        logError({
          source: 'page',
          message: '카카오 로그인 콜백 API 실패',
          error: err,
          context: { hasCode: !!code },
        });
        setError('로그인 서버와 통신 중 오류가 발생했습니다.');
      });

    return () => {
      cancelled = true;
    };
  }, [code, error, processedKey, login, navigate]);

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
