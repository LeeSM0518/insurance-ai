import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { ShieldCheck } from 'lucide-react';

import { KakaoLogo } from '@/components/icons/kakao-logo';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/hooks/use-auth';
import { getApi } from '@/lib/api';

function buildKakaoAuthUrl(): string {
  const params = new URLSearchParams({
    client_id: import.meta.env.VITE_KAKAO_REST_API_KEY ?? '',
    redirect_uri: import.meta.env.VITE_KAKAO_REDIRECT_URI ?? '',
    response_type: 'code',
  });
  return `https://kauth.kakao.com/oauth/authorize?${params.toString()}`;
}

export default function LoginPage() {
  const { isAuthenticated, isLoading, login } = useAuth();
  const navigate = useNavigate();

  // 이미 로그인된 사용자 → 메인 리다이렉트
  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      navigate('/', { replace: true });
    }
  }, [isAuthenticated, isLoading, navigate]);

  const handleKakaoLogin = () => {
    window.location.href = buildKakaoAuthUrl();
  };

  const handleDevLogin = async () => {
    try {
      const response = await getApi().devLogin({});
      const data = response.data.data as {
        accessToken: string;
        refreshToken: string;
        expiresIn: number;
        user: { id: string; nickname: string; email?: string; createdAt: string };
      };
      if (!data) return;
      login(data);
      navigate('/', { replace: true });
    } catch {
      // dev-login 실패 시 무시
    }
  };

  return (
    <div className="min-h-dvh flex flex-col items-center justify-center bg-background px-6">
      <div className="flex w-full max-w-sm flex-col items-center">
        {/* 로고 영역 */}
        <ShieldCheck className="size-12 text-primary" aria-hidden="true" />

        {/* 서비스 소개 */}
        <div className="mt-4 flex flex-col items-center gap-2">
          <h1 className="text-3xl font-bold text-primary">Insurance AI</h1>
          <p className="text-base text-muted-foreground">
            보험 설계사를 위한 AI 영업 비서
          </p>
        </div>

        {/* 카카오 로그인 버튼 */}
        <Button
          variant="kakao"
          size="lg"
          className="mt-10 h-12 w-full"
          aria-label="카카오 계정으로 로그인"
          onClick={handleKakaoLogin}
        >
          <KakaoLogo />
          카카오로 시작하기
        </Button>

        {/* 개발 모드 로그인 */}
        {import.meta.env.DEV && (
          <Button
            variant="outline"
            size="lg"
            className="mt-3 h-12 w-full"
            onClick={handleDevLogin}
            data-testid="dev-login-button"
          >
            개발자 로그인
          </Button>
        )}

        {/* 약관 링크 */}
        <div className="mt-6 flex items-center gap-2 text-xs text-muted-foreground">
          <a href="/terms" className="hover:underline">
            서비스 이용약관
          </a>
          <span>|</span>
          <a href="/privacy" className="hover:underline">
            개인정보처리방침
          </a>
        </div>
      </div>
    </div>
  );
}
