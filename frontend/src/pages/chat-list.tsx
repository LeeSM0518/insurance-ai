import { useState } from 'react';
import { useNavigate } from 'react-router';
import { MessageSquare, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { SessionList } from '@/features/chat/session-list';
import { MOCK_SESSIONS } from '@/mocks/chat-data';
import { toast } from 'sonner';
import type { ChatSession } from '@/types/chat';

export default function ChatListPage() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<ChatSession[]>(MOCK_SESSIONS);

  const handleDelete = (id: string) => {
    setSessions((prev) => prev.filter((s) => s.id !== id));
    toast.success('대화가 삭제되었습니다');
  };

  const handleNewChat = () => {
    navigate('/chat/new');
  };

  return (
    <div className="flex-1 flex flex-col">
      {/* 헤더 */}
      <div className="flex items-center justify-between px-4 pt-4 pb-2">
        <h1 className="text-lg font-semibold">AI 상담</h1>
        <Button variant="outline" size="sm" className="gap-1" onClick={handleNewChat}>
          <Plus className="size-3.5" />
          새 대화
        </Button>
      </div>

      {/* 세션 목록 */}
      <div className="flex-1 px-4">
        {sessions.length > 0 ? (
          <SessionList sessions={sessions} onDelete={handleDelete} />
        ) : (
          <EmptyState
            icon={MessageSquare}
            title="아직 대화가 없습니다"
            description="검색으로 시작해보세요"
            action={{ label: '검색하러 가기', onClick: () => navigate('/') }}
          />
        )}
      </div>
    </div>
  );
}
