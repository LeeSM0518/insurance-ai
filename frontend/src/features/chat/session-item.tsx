import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useSwipeable } from 'react-swipeable';
import { Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { formatRelativeTime } from '@/lib/format-time';
import type { ChatSession } from '@/types/chat';

interface SessionItemProps {
  session: ChatSession;
  onDelete: (id: string) => void;
}

export function SessionItem({ session, onDelete }: SessionItemProps) {
  const navigate = useNavigate();
  const [offsetX, setOffsetX] = useState(0);

  const handlers = useSwipeable({
    onSwiping: (e) => {
      if (e.dir === 'Left') {
        setOffsetX(Math.min(Math.abs(e.deltaX), 80));
      }
    },
    onSwipedLeft: () => {
      setOffsetX(80);
    },
    onSwipedRight: () => {
      setOffsetX(0);
    },
    trackMouse: false,
    preventScrollOnSwipe: true,
  });

  return (
    <div className="relative overflow-hidden rounded-lg">
      {/* 삭제 버튼 (뒤에 숨어있다가 스와이프 시 노출) */}
      <div className="absolute inset-y-0 right-0 flex items-center">
        <Button
          variant="destructive"
          className="h-full rounded-none px-4"
          onClick={(e) => {
            e.stopPropagation();
            onDelete(session.id);
          }}
          aria-label="대화 삭제"
        >
          <Trash2 className="size-4" />
        </Button>
      </div>

      {/* 세션 카드 */}
      <div
        {...handlers}
        className="relative cursor-pointer border-b bg-card px-4 py-3 transition-transform"
        style={{ transform: `translateX(-${offsetX}px)` }}
        onClick={() => navigate(`/chat/${session.id}`)}
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter') navigate(`/chat/${session.id}`);
        }}
      >
        <h3 className="text-sm font-medium truncate">{session.title}</h3>
        <p className="mt-0.5 text-xs text-muted-foreground truncate">{session.lastMessagePreview}</p>
        <time className="mt-1 block text-[11px] text-muted-foreground/70">
          {formatRelativeTime(session.updatedAt)}
        </time>
      </div>
    </div>
  );
}
