import { PanelLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface ChatHeaderProps {
  title: string;
  onToggleSidebar: () => void;
}

export function ChatHeader({ title, onToggleSidebar }: ChatHeaderProps) {
  return (
    <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background px-2 py-2">
      <Button variant="ghost" size="icon-sm" onClick={onToggleSidebar} aria-label="대화 목록">
        <PanelLeft className="size-4" />
      </Button>
      <h1 className="text-sm font-semibold truncate">{title}</h1>
    </header>
  );
}
