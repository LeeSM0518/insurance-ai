import { useState, useCallback, useMemo } from 'react';
import { ChatHeader } from '@/features/chat/chat-header';
import { ChatSidebar } from '@/features/chat/chat-sidebar';
import { ChatMessage } from '@/features/chat/chat-message';
import { SourceCards } from '@/features/chat/source-cards';
import { SuggestedQuestions } from '@/features/chat/suggested-questions';
import { ChatInput } from '@/features/chat/chat-input';
import { DisclaimerBanner } from '@/features/chat/disclaimer-banner';
import { SourceTabPanel } from '@/features/chat/source-tab-panel';
import type { SourceTab } from '@/features/chat/source-tab-panel';
import { Sheet, SheetContent } from '@/components/ui/sheet';
import { useMediaQuery } from '@/hooks/use-media-query';
import { useResizeHandle } from '@/hooks/use-resize-handle';
import { MOCK_SESSIONS, MOCK_MESSAGES } from '@/mocks/chat-data';
import { toast } from 'sonner';
import type { ChatSession, Source } from '@/types/chat';

const SOURCE_PANEL_MIN = 280;
const SOURCE_PANEL_MAX = 700;
const SOURCE_PANEL_DEFAULT = 360;

const MAX_TABS = 5;

export default function ChatPage() {
  const isDesktop = useMediaQuery('(min-width: 1025px)');
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [sessions, setSessions] = useState<ChatSession[]>(MOCK_SESSIONS);
  const [sidebarOpen, setSidebarOpen] = useState(isDesktop);
  const [openTabs, setOpenTabs] = useState<SourceTab[]>([]);
  const [activeTabId, setActiveTabId] = useState<string | null>(null);
  const [sourcePanelWidth, setSourcePanelWidth] = useState(SOURCE_PANEL_DEFAULT);

  const handleSourceResize = useCallback((width: number) => {
    setSourcePanelWidth(width);
  }, []);

  const { handleMouseDown: handleResizeMouseDown } = useResizeHandle({
    minWidth: SOURCE_PANEL_MIN,
    maxWidth: SOURCE_PANEL_MAX,
    onResize: handleSourceResize,
    direction: 'left',
  });

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
    if (!isDesktop) setSidebarOpen(false);
    setOpenTabs([]);
    setActiveTabId(null);
  };

  const handleNewChat = () => {
    setActiveSessionId(null);
    if (!isDesktop) setSidebarOpen(false);
    setOpenTabs([]);
    setActiveTabId(null);
  };

  const handleDelete = (id: string) => {
    setSessions((prev) => prev.filter((s) => s.id !== id));
    if (activeSessionId === id) {
      setActiveSessionId(null);
      setOpenTabs([]);
      setActiveTabId(null);
    }
    toast.success('대화가 삭제되었습니다');
  };

  const handleSend = (message: string) => {
    toast.info(`메시지 전송: "${message}" (Mock — 실제 전송 없음)`);
  };

  const handleSuggestedQuestion = (question: string) => {
    toast.info(`추천 질문 선택: "${question}" (Mock — 실제 전송 없음)`);
  };

  const handleOpenSource = (source: Source, index: number) => {
    // 이미 열린 탭이면 활성화만
    if (openTabs.some((tab) => tab.articleId === source.articleId)) {
      setActiveTabId(source.articleId);
      return;
    }

    const newTab: SourceTab = {
      articleId: source.articleId,
      articleNumber: source.articleNumber,
      articleTitle: source.articleTitle,
      insuranceCompany: source.insuranceCompany,
      sourceIndex: index + 1,
    };

    // 최대 탭 초과 시 가장 오래된 탭 제거
    const updated =
      openTabs.length >= MAX_TABS ? [...openTabs.slice(1), newTab] : [...openTabs, newTab];

    setOpenTabs(updated);
    setActiveTabId(source.articleId);
  };

  const handleCloseTab = (articleId: string) => {
    const idx = openTabs.findIndex((t) => t.articleId === articleId);
    const updated = openTabs.filter((t) => t.articleId !== articleId);

    setOpenTabs(updated);

    // 닫는 탭이 활성 탭이면 인접 탭으로 전환
    if (activeTabId === articleId) {
      if (updated.length === 0) {
        setActiveTabId(null);
      } else {
        const nextIdx = Math.min(idx, updated.length - 1);
        setActiveTabId(updated[nextIdx].articleId);
      }
    }
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
      {/* 데스크톱: 접기/펼치기 애니메이션 사이드바 */}
      {isDesktop && (
        <aside
          className={`border-r flex-shrink-0 overflow-hidden transition-[width] duration-200 ease-in-out ${sidebarOpen ? 'w-[280px]' : 'w-0 border-r-0'}`}
        >
          <div className="w-[280px] h-full">{sidebarContent}</div>
        </aside>
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
          onToggleSidebar={() => setSidebarOpen((prev) => !prev)}
        />

        {/* 메시지 영역 */}
        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4" role="log" aria-live="polite">
          {messages.map((message) => (
            <div key={message.id}>
              {message.role === 'ASSISTANT' && message.sources && message.sources.length > 0 && (
                <SourceCards
                  sources={message.sources}
                  onSourceClick={isDesktop ? handleOpenSource : undefined}
                  activeArticleId={isDesktop ? activeTabId : undefined}
                />
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

      {/* 데스크톱 출처 사이드패널 (리사이즈 가능) */}
      {isDesktop && openTabs.length > 0 && (
        <aside className="relative flex-shrink-0 border-l" style={{ width: sourcePanelWidth }}>
          {/* 리사이즈 핸들 */}
          <div
            className="absolute left-0 top-0 bottom-0 w-1 cursor-col-resize hover:bg-primary/20 active:bg-primary/30 z-10"
            onMouseDown={(e) => handleResizeMouseDown(e, sourcePanelWidth)}
          />
          <SourceTabPanel
            tabs={openTabs}
            activeTabId={activeTabId}
            onSelectTab={setActiveTabId}
            onCloseTab={handleCloseTab}
          />
        </aside>
      )}
    </div>
  );
}
