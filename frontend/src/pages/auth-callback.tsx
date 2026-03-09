import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '@/hooks/use-auth';
import { getApi } from '@/lib/api';
import { logError } from '@/lib/error-logger';
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

    // sessionStorage로 code 중복 사용 방지 (새로고침, 뒤로가기 대응)
    const processedKey = `oauth_code_processed_${code}`;
    if (sessionStorage.getItem(processedKey)) {
      setError('이미 처리된 인증 코드입니다. 다시 로그인해주세요.');
      return;
    }

    // React StrictMode 이중 호출 방지
    if (isProcessing.current) return;
    isProcessing.current = true;
    sessionStorage.setItem(processedKey, 'true');

    handleOAuthCallback(code);
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  async function handleOAuthCallback(code: string) {
    // 1단계: API 호출
    let loginData;
    try {
      const loginResponse = await getApi().login({
        loginRequest: {
          code,
          redirectUri: import.meta.env.VITE_KAKAO_REDIRECT_URI,
        },
      });

      loginData = loginResponse.data.data;
      if (!loginData) {
        logError({
          source: 'page',
          message: '카카오 로그인 응답 데이터 없음',
          context: { responseData: loginResponse.data },
        });
        setError('서버 응답에 로그인 정보가 없습니다.');
        return;
      }
    } catch (err) {
      logError({
        source: 'page',
        message: '카카오 로그인 콜백 API 실패',
        error: err,
        context: { hasCode: !!code },
      });
      setError('로그인 서버와 통신 중 오류가 발생했습니다.');
      return;
    }

    // 2단계: 토큰 저장 및 상태 업데이트
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

    // 3단계: 페이지 이동 (성공 시 code 처리 기록 정리)
    sessionStorage.removeItem(`oauth_code_processed_${code}`);
    navigate('/', { replace: true });
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
