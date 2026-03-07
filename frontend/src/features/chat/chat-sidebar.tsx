import { MessageSquare, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { SessionItem } from './session-item';
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
  return (
    <div className="flex h-full flex-col">
      {/* 헤더 */}
      <div className="flex items-center justify-between px-4 pt-4 pb-2">
        <h2 className="text-lg font-semibold">AI 상담</h2>
        <Button variant="outline" size="sm" className="gap-1" onClick={onNewChat}>
          <Plus className="size-3.5" />
          새 대화
        </Button>
      </div>

      {/* 세션 목록 */}
      <div className="flex-1 overflow-y-auto px-4">
        {sessions.length > 0 ? (
          <div className="divide-y">
            {sessions.map((session) => (
              <SessionItem
                key={session.id}
                session={session}
                isActive={session.id === activeSessionId}
                onSelect={onSelectSession}
                onDelete={onDeleteSession}
              />
            ))}
          </div>
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
