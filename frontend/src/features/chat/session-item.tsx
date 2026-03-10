import { Trash2 } from 'lucide-react';

import { useMediaQuery, BREAKPOINTS } from '@/hooks/use-media-query';
import type { ChatSession } from '@/types/chat';

interface SessionItemProps {
  session: ChatSession;
  isActive?: boolean;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
}

// 대화 세션 항목: 제목 한 줄만 표시
export function SessionItemCompact({ session, isActive, onSelect, onDelete }: SessionItemProps) {
  const isDesktop = useMediaQuery(BREAKPOINTS.desktop);

  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    onDelete(session.id);
  };

  return (
    <div className="group relative overflow-hidden rounded-lg">
      <div
        className={`relative cursor-pointer rounded-lg bg-card px-3 py-2 transition-all ${
          isActive ? 'bg-muted' : 'hover:bg-muted/50'
        }`}
        onClick={() => onSelect(session.id)}
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter') onSelect(session.id);
        }}
      >
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-sm font-normal truncate">{session.title}</h3>
          {/* 데스크톱: 호버 시 표시 / 모바일: 항상 표시 */}
          <button
            className={`shrink-0 transition-opacity text-muted-foreground hover:text-destructive ${
              isDesktop ? 'opacity-0 group-hover:opacity-100' : 'opacity-100'
            }`}
            onClick={handleDelete}
            aria-label="대화 삭제"
          >
            <Trash2 className="size-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
