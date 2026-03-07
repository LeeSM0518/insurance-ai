// 카카오 말풍선 심볼 SVG 컴포넌트
// 카카오 브랜드 가이드라인 기반

interface KakaoLogoProps {
  size?: number;
  className?: string;
}

export function KakaoLogo({ size = 20, className }: KakaoLogoProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      className={className}
      aria-hidden="true"
    >
      <path d="M12 3C6.48 3 2 6.54 2 10.86c0 2.78 1.86 5.22 4.66 6.62-.15.56-.96 3.6-.99 3.83 0 0-.02.17.09.24.11.06.24.01.24.01.32-.04 3.7-2.44 4.28-2.86.56.08 1.14.12 1.72.12 5.52 0 10-3.54 10-7.96C22 6.54 17.52 3 12 3" />
    </svg>
  );
}
