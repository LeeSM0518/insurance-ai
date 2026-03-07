import { Link } from 'react-router';

export default function NotFoundPage() {
  return (
    <div className="min-h-dvh flex flex-col items-center justify-center gap-4">
      <h1 className="text-4xl font-bold">404</h1>
      <p className="text-muted-foreground">페이지를 찾을 수 없습니다.</p>
      <Link to="/" className="text-primary hover:underline">
        메인으로 이동
      </Link>
    </div>
  );
}
