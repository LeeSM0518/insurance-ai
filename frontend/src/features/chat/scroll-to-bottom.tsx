import { ArrowDown } from 'lucide-react';
import { cn } from '@/lib/utils';

interface ScrollToBottomProps {
  visible: boolean;
  onClick: () => void;
}

export function ScrollToBottom({ visible, onClick }: ScrollToBottomProps) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'absolute bottom-4 left-1/2 -translate-x-1/2 z-10',
        'flex items-center gap-1.5 px-3 py-1.5',
        'bg-background border shadow-md rounded-full',
        'text-sm text-muted-foreground hover:text-foreground',
        'transition-opacity duration-200',
        visible ? 'opacity-100' : 'opacity-0 pointer-events-none',
      )}
      aria-label="최신 메시지로 이동"
    >
      <ArrowDown className="size-4" />
      <span>최신 메시지</span>
    </button>
  );
}
