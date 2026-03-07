import { FileQuestion } from 'lucide-react';
import { useNavigate } from 'react-router';

import { Button } from '@/components/ui/button';

export default function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-dvh flex flex-col items-center justify-center gap-4 px-6">
      <FileQuestion className="size-16 text-muted-foreground/50" aria-hidden="true" />
      <h1 className="text-4xl font-bold">404</h1>
      <p className="text-muted-foreground">페이지를 찾을 수 없습니다.</p>
      <p className="text-sm text-muted-foreground/70 text-center">
        주소가 올바른지 확인하거나, 아래 버튼으로 이동하세요.
      </p>
      <Button className="mt-2" onClick={() => navigate('/')}>
        메인으로 이동
      </Button>
    </div>
  );
}
