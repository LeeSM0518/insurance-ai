import { useMemo } from 'react';
import { MessageSquare, SquarePen } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { groupSessionsByDate } from '@/lib/format-time';
import { SessionItemFeatured, SessionItemCompact } from './session-item';
import type { ChatSession } from '@/types/chat';

interface ChatSidebarProps {
  sessions: ChatSession[];
  activeSessionId: string | null;
  onSelectSession: (id: string) => void;
  onNewChat: () => void;
  onDeleteSession: (id: string) => void;
}

export function ChatSidebar({
  sessions,
  activeSessionId,
  onSelectSession,
  onNewChat,
  onDeleteSession,
}: ChatSidebarProps) {
  const groupedSessions = useMemo(() => groupSessionsByDate(sessions), [sessions]);

  // 전체 세션 중 첫 번째(가장 최신)의 ID
  const latestSessionId = sessions.length > 0 ? sessions[0].id : null;

  return (
    <div className="flex h-full flex-col">
      {/* 새 대화 버튼 */}
      <div className="px-3 pt-3 pb-1">
        <Button
          variant="ghost"
          className="w-full justify-start gap-2 text-sm font-normal"
          onClick={onNewChat}
        >
          <SquarePen className="size-4" />
          새 대화
        </Button>
      </div>

      {/* 그룹별 세션 목록 */}
      <div className="flex-1 overflow-y-auto px-2">
        {groupedSessions.length > 0 ? (
          groupedSessions.map(({ group, label, sessions: groupSessions }) => (
            <div key={group}>
              {/* 그룹 헤더 */}
              <div className="px-3 pt-4 pb-1">
                <span className="text-xs font-medium text-muted-foreground">
                  {label}
                </span>
              </div>

              {/* 세션 목록 */}
              {groupSessions.map((session) =>
                session.id === latestSessionId ? (
                  <SessionItemFeatured
                    key={session.id}
                    session={session}
                    isActive={session.id === activeSessionId}
                    onSelect={onSelectSession}
                    onDelete={onDeleteSession}
                  />
                ) : (
                  <SessionItemCompact
                    key={session.id}
                    session={session}
                    isActive={session.id === activeSessionId}
                    onSelect={onSelectSession}
                    onDelete={onDeleteSession}
                  />
                )
              )}
            </div>
          ))
        ) : (
          <EmptyState
            icon={MessageSquare}
            title="아직 대화가 없습니다"
            description="검색으로 시작해보세요"
          />
        )}
      </div>
    </div>
  );
}
