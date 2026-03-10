import { useState } from 'react';
import { useSwipeable } from 'react-swipeable';
import { Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';

import { useMediaQuery, BREAKPOINTS } from '@/hooks/use-media-query';
import type { ChatSession } from '@/types/chat';

interface SessionItemProps {
  session: ChatSession;
  isActive?: boolean;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
}

// 스와이프 삭제 (모바일) + 호버 삭제 (데스크톱) 공통 로직
function useSessionItemInteraction(sessionId: string, onDelete: (id: string) => void) {
  const isDesktop = useMediaQuery(BREAKPOINTS.desktop);
  const [offsetX, setOffsetX] = useState(0);

  const swipeHandlers = useSwipeable({
    onSwiping: (e) => {
      if (e.dir === 'Left') {
        setOffsetX(Math.min(Math.abs(e.deltaX), 80));
      }
    },
    onSwipedLeft: () => setOffsetX(80),
    onSwipedRight: () => setOffsetX(0),
    trackMouse: false,
    preventScrollOnSwipe: true,
  });

  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    onDelete(sessionId);
  };

  return { isDesktop, offsetX, swipeHandlers, handleDelete };
}

// 대화 세션 항목: 제목 한 줄만 표시
export function SessionItemCompact({ session, isActive, onSelect, onDelete }: SessionItemProps) {
  const { isDesktop, offsetX, swipeHandlers, handleDelete } = useSessionItemInteraction(session.id, onDelete);

  return (
    <div className="group relative overflow-hidden rounded-lg">
      {/* 모바일 스와이프 삭제 버튼 */}
      {!isDesktop && (
        <div className="absolute inset-y-0 right-0 flex items-center">
          <Button
            variant="destructive"
            className="h-full rounded-none px-4"
            onClick={handleDelete}
            aria-label="대화 삭제"
          >
            <Trash2 className="size-4" />
          </Button>
        </div>
      )}

      <div
        {...(isDesktop ? {} : swipeHandlers)}
        className={`relative cursor-pointer rounded-lg bg-card px-3 py-2 transition-all ${
          isActive ? 'bg-muted' : 'hover:bg-muted/50'
        }`}
        style={isDesktop ? undefined : { transform: `translateX(-${offsetX}px)` }}
        onClick={() => onSelect(session.id)}
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter') onSelect(session.id);
        }}
      >
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-sm truncate">{session.title}</h3>
          {/* 데스크톱 호버 삭제 버튼 */}
          {isDesktop && (
            <button
              className="shrink-0 opacity-0 group-hover:opacity-100 transition-opacity text-muted-foreground hover:text-destructive"
              onClick={handleDelete}
              aria-label="대화 삭제"
            >
              <Trash2 className="size-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
