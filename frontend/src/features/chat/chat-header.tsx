import { useNavigate } from 'react-router';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface ChatHeaderProps {
  title: string;
}

export function ChatHeader({ title }: ChatHeaderProps) {
  const navigate = useNavigate();

  return (
    <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background px-2 py-2">
      <Button variant="ghost" size="icon-sm" onClick={() => navigate('/chat')} aria-label="뒤로가기">
        <ArrowLeft className="size-4" />
      </Button>
      <h1 className="text-sm font-semibold truncate">{title}</h1>
    </header>
  );
}
