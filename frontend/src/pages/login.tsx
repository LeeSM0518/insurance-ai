import { ShieldCheck } from 'lucide-react';

import { KakaoLogo } from '@/components/icons/kakao-logo';
import { Button } from '@/components/ui/button';

export default function LoginPage() {
  return (
    <div className="min-h-dvh flex flex-col items-center justify-center bg-background px-6">
      <div className="flex w-full max-w-sm flex-col items-center">
        {/* 로고 영역 */}
        <ShieldCheck className="size-12 text-primary" aria-hidden="true" />

        {/* 서비스 소개 */}
        <div className="mt-4 flex flex-col items-center gap-2">
          <h1 className="text-2xl font-bold">Insurance AI</h1>
          <p className="text-sm text-muted-foreground">
            전 보험사 약관 AI 검색 서비스
          </p>
        </div>

        {/* 카카오 로그인 버튼 */}
        <Button
          variant="kakao"
          size="lg"
          className="mt-10 h-12 w-full"
          aria-label="카카오 계정으로 로그인"
        >
          <KakaoLogo />
          카카오로 시작하기
        </Button>

        {/* 약관 링크 */}
        <div className="mt-6 flex items-center gap-2 text-xs text-muted-foreground">
          <a href="#" className="hover:underline">
            서비스 이용약관
          </a>
          <span>|</span>
          <a href="#" className="hover:underline">
            개인정보처리방침
          </a>
        </div>
      </div>
    </div>
  );
}
