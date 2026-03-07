import { useState, useMemo } from 'react';
import { ChatHeader } from '@/features/chat/chat-header';
import { ChatSidebar } from '@/features/chat/chat-sidebar';
import { ChatMessage } from '@/features/chat/chat-message';
import { SourceCards } from '@/features/chat/source-cards';
import { SuggestedQuestions } from '@/features/chat/suggested-questions';
import { ChatInput } from '@/features/chat/chat-input';
import { DisclaimerBanner } from '@/features/chat/disclaimer-banner';
import { Sheet, SheetContent } from '@/components/ui/sheet';
import { useMediaQuery } from '@/hooks/use-media-query';
import { MOCK_SESSIONS, MOCK_MESSAGES } from '@/mocks/chat-data';
import { toast } from 'sonner';
import type { ChatSession } from '@/types/chat';

export default function ChatPage() {
  const isDesktop = useMediaQuery('(min-width: 1025px)');
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [sessions, setSessions] = useState<ChatSession[]>(MOCK_SESSIONS);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const session = useMemo(
    () => sessions.find((s) => s.id === activeSessionId),
    [sessions, activeSessionId],
  );
  const messages = useMemo(
    () => (activeSessionId ? MOCK_MESSAGES[activeSessionId] || [] : []),
    [activeSessionId],
  );

  const handleSelectSession = (id: string) => {
    setActiveSessionId(id);
    setSidebarOpen(false);
  };

  const handleNewChat = () => {
    setActiveSessionId(null);
    setSidebarOpen(false);
  };

  const handleDelete = (id: string) => {
    setSessions((prev) => prev.filter((s) => s.id !== id));
    if (activeSessionId === id) setActiveSessionId(null);
    toast.success('대화가 삭제되었습니다');
  };

  const handleSend = (message: string) => {
    toast.info(`메시지 전송: "${message}" (Mock — 실제 전송 없음)`);
  };

  const handleSuggestedQuestion = (question: string) => {
    toast.info(`추천 질문 선택: "${question}" (Mock — 실제 전송 없음)`);
  };

  const title = session?.title || '새 대화';

  const sidebarContent = (
    <ChatSidebar
      sessions={sessions}
      activeSessionId={activeSessionId}
      onSelectSession={handleSelectSession}
      onNewChat={handleNewChat}
      onDeleteSession={handleDelete}
    />
  );

  return (
    <div className="flex-1 flex flex-col desktop:flex-row min-h-0">
      {/* 데스크톱: 사이드바 상시 표시 */}
      {isDesktop && (
        <aside className="w-[280px] border-r flex-shrink-0">{sidebarContent}</aside>
      )}

      {/* 모바일: Sheet 사이드바 */}
      {!isDesktop && (
        <Sheet open={sidebarOpen} onOpenChange={setSidebarOpen}>
          <SheetContent side="left" showCloseButton={false} className="w-[280px] p-0">
            {sidebarContent}
          </SheetContent>
        </Sheet>
      )}

      {/* 채팅 영역 */}
      <div className="flex-1 flex flex-col min-w-0">
        <ChatHeader
          title={title}
          isDesktop={isDesktop}
          onToggleSidebar={() => setSidebarOpen(true)}
        />

        {/* 메시지 영역 */}
        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4" role="log" aria-live="polite">
          {messages.map((message) => (
            <div key={message.id}>
              {message.role === 'ASSISTANT' && message.sources && message.sources.length > 0 && (
                <SourceCards sources={message.sources} />
              )}
              <ChatMessage message={message} />
              {message.role === 'ASSISTANT' && message.suggestedQuestions && (
                <SuggestedQuestions
                  questions={message.suggestedQuestions}
                  onSelect={handleSuggestedQuestion}
                />
              )}
            </div>
          ))}
        </div>

        {/* 면책 고지 + 입력 영역 */}
        <DisclaimerBanner />
        <ChatInput onSend={handleSend} />
      </div>

      {/* 데스크톱 출처 사이드패널 (Step 8에서 활성화) */}
      {isDesktop && (
        <aside className="w-[var(--sidebar-width)] border-l bg-muted/30 flex items-center justify-center">
          <p className="text-sm text-muted-foreground text-center px-6">
            출처를 클릭하면
            <br />
            약관 상세가 표시됩니다
          </p>
        </aside>
      )}
    </div>
  );
}
