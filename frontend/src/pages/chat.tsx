import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useBlocker, useSearchParams } from 'react-router';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { ChatHeader } from '@/features/chat/chat-header';
import { ChatSidebar } from '@/features/chat/chat-sidebar';
import { ChatMessage } from '@/features/chat/chat-message';
import { SourceCards } from '@/features/chat/source-cards';
import { SuggestedQuestions } from '@/features/chat/suggested-questions';
import { ChatInput } from '@/features/chat/chat-input';
import { DisclaimerBanner } from '@/features/chat/disclaimer-banner';
import { SourceTabPanel } from '@/features/chat/source-tab-panel';
import type { SourceTab } from '@/features/chat/source-tab-panel';
import { StreamingIndicator } from '@/features/chat/streaming-indicator';
import { StreamingMessage } from '@/features/chat/streaming-message';
import { ScrollToBottom } from '@/features/chat/scroll-to-bottom';
import { SourceTabContent } from '@/features/chat/source-tab-content';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { useMediaQuery } from '@/hooks/use-media-query';
import { useLocalStorage } from '@/hooks/use-local-storage';
import { useResizeHandle } from '@/hooks/use-resize-handle';
import { useStreamingChat } from '@/hooks/use-streaming-chat';
import { useAutoScroll } from '@/hooks/use-auto-scroll';
import { useChatSessions } from '@/hooks/use-chat-sessions';
import { toast } from 'sonner';
import type { Source } from '@/types/chat';

const SOURCE_PANEL_MIN = 280;
const SOURCE_PANEL_MAX = 700;
const SOURCE_PANEL_DEFAULT = 360;

const MAX_TABS = 5;
const MAX_RETRIES = 3;

export default function ChatPage() {
  const isDesktop = useMediaQuery('(min-width: 1025px)');
  const [searchParams, setSearchParams] = useSearchParams();
  const [activeSessionId, setActiveSessionId] = useLocalStorage<string | null>('chat-active-session', null);
  const { sessions, createSession, deleteSession } = useChatSessions();
  const [sidebarOpen, setSidebarOpen] = useLocalStorage('chat-sidebar-open', isDesktop);
  const [openTabs, setOpenTabs] = useState<SourceTab[]>([]);
  const [activeTabId, setActiveTabId] = useState<string | null>(null);
  const [sourcePanelWidth, setSourcePanelWidth] = useLocalStorage('chat-source-panel-width', SOURCE_PANEL_DEFAULT);
  const [mobileSourceSheet, setMobileSourceSheet] = useState<Source | null>(null);
  const initialQueryProcessed = useRef(false);

  // 스트리밍 채팅 Hook
  const { messages, streamingState, isStreaming, sendMessage, retryStreaming } =
    useStreamingChat(activeSessionId);

  // 스크롤 컨테이너 ref + 자동 스크롤
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const { isAtBottom, scrollToBottom } = useAutoScroll({
    containerRef: scrollContainerRef,
    deps: [messages, streamingState.streamingContent, streamingState.phase],
  });

  const handleSourceResize = useCallback((width: number) => {
    setSourcePanelWidth(width);
  }, [setSourcePanelWidth]);

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

  const handleSelectSession = (id: string) => {
    setActiveSessionId(id);
    if (!isDesktop) setSidebarOpen(false);
    setOpenTabs([]);
    setActiveTabId(null);
    // 세션 전환 시 하단으로 스크롤
    setTimeout(() => scrollToBottom('instant'), 0);
  };

  const handleNewChat = () => {
    setActiveSessionId(null);
    if (!isDesktop) setSidebarOpen(false);
    setOpenTabs([]);
    setActiveTabId(null);
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteSession(id);
      if (activeSessionId === id) {
        setActiveSessionId(null);
        setOpenTabs([]);
        setActiveTabId(null);
      }
      toast.success('대화가 삭제되었습니다');
    } catch {
      toast.error('대화 삭제에 실패했습니다');
    }
  };

  const handleSend = async (message: string) => {
    if (!activeSessionId) {
      // 새 세션 생성 (initialMessage가 USER 메시지로 저장됨)
      try {
        const newSessionId = await createSession(message);
        setActiveSessionId(newSessionId);
        // /reply 엔드포인트로 AI 응답 트리거 (USER 메시지 중복 저장 방지)
        // sendMessage의 isInitialMessage=true로 호출
        sendMessage(message, true);
      } catch {
        toast.error('대화를 시작할 수 없습니다');
      }
    } else {
      // 기존 세션: /messages 엔드포인트 (USER 메시지 저장 + AI 응답)
      sendMessage(message);
    }
  };

  const handleSuggestedQuestion = (question: string) => {
    if (activeSessionId) {
      sendMessage(question);
    }
  };

  // URL 쿼리 파라미터 처리 (/chat?q=검색어)
  useEffect(() => {
    const q = searchParams.get('q');
    if (q && !initialQueryProcessed.current && !isStreaming) {
      initialQueryProcessed.current = true;
      // URL에서 q 파라미터 제거
      setSearchParams({}, { replace: true });
      handleSend(q);
    }
  }, [searchParams]); // eslint-disable-line react-hooks/exhaustive-deps

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

  // 모바일 인라인 출처 탭 핸들러
  const handleMobileSourceTap = useCallback((source: Source) => {
    setMobileSourceSheet(source);
  }, []);

  // 스트리밍 중 에러 toast 표시
  useEffect(() => {
    if (streamingState.phase === 'error' && streamingState.errorMessage) {
      toast.error(streamingState.errorMessage);
    }
  }, [streamingState.phase, streamingState.errorMessage]);

  // 스트리밍 중 beforeunload 방지
  useEffect(() => {
    if (!isStreaming) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [isStreaming]);

  // 스트리밍 중 라우트 이동 방지
  useBlocker(({ currentLocation, nextLocation }) => {
    if (!isStreaming) return false;
    return currentLocation.pathname !== nextLocation.pathname;
  });

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

        {/* 메시지 영역 (relative 컨테이너) */}
        <div className="relative flex-1 min-h-0">
          <div
            ref={scrollContainerRef}
            className="h-full overflow-y-auto px-4 py-3 space-y-4"
            role="log"
            aria-live="polite"
          >
            {messages.map((message) => (
              <div key={message.id}>
                {message.role === 'ASSISTANT' && message.sources && message.sources.length > 0 && (
                  <SourceCards
                    sources={message.sources}
                    onSourceClick={isDesktop ? handleOpenSource : undefined}
                    activeArticleId={isDesktop ? activeTabId : undefined}
                  />
                )}
                <ChatMessage
                  message={message}
                  onSourceClick={
                    isDesktop && message.sources
                      ? (index) => {
                          const source = message.sources![index - 1];
                          if (source) handleOpenSource(source, index - 1);
                        }
                      : undefined
                  }
                  onMobileSourceTap={!isDesktop ? handleMobileSourceTap : undefined}
                  activeSourceIndex={
                    isDesktop && activeTabId && message.sources
                      ? message.sources.findIndex((s) => s.articleId === activeTabId) + 1 || null
                      : null
                  }
                />
                {message.role === 'ASSISTANT' && message.suggestedQuestions && (
                  <SuggestedQuestions
                    questions={message.suggestedQuestions}
                    onSelect={handleSuggestedQuestion}
                  />
                )}
              </div>
            ))}

            {/* 스트리밍 중인 AI 응답 */}
            {isStreaming && (
              <div>
                {/* searching/generating 단계 인디케이터 + 재연결 상태 */}
                <StreamingIndicator
                  phase={streamingState.phase}
                  retryInfo={{
                    isReconnecting: streamingState.isReconnecting,
                    retryCount: streamingState.retryCount,
                    maxRetries: MAX_RETRIES,
                  }}
                />

                {/* sources_found 이후: 출처 카드 표시 */}
                {streamingState.sources.length > 0 && (
                  <SourceCards
                    sources={streamingState.sources}
                    onSourceClick={isDesktop ? handleOpenSource : undefined}
                    activeArticleId={isDesktop ? activeTabId : undefined}
                  />
                )}

                {/* streaming 단계: 실시간 텍스트 */}
                {streamingState.phase === 'streaming' && streamingState.streamingContent && (
                  <StreamingMessage
                    content={streamingState.streamingContent}
                    onSourceClick={
                      isDesktop
                        ? (index) => {
                            const source = streamingState.sources[index - 1];
                            if (source) handleOpenSource(source, index - 1);
                          }
                        : undefined
                    }
                  />
                )}
              </div>
            )}

            {/* 에러 상태: 재시도 UI */}
            {streamingState.phase === 'error' && (
              <div className="flex flex-col items-center gap-2 py-4 text-center">
                <div className="flex items-center gap-2 text-muted-foreground">
                  <AlertTriangle className="size-5" />
                  <span className="text-sm">연결이 끊어졌습니다.</span>
                </div>
                <p className="text-sm text-muted-foreground">다시 시도해주세요.</p>
                <Button variant="outline" size="sm" onClick={retryStreaming}>
                  <RefreshCw className="size-4" />
                  다시 시도
                </Button>
              </div>
            )}
          </div>

          {/* ScrollToBottom 버튼 */}
          <ScrollToBottom
            visible={!isAtBottom}
            onClick={() => scrollToBottom('smooth')}
          />
        </div>

        {/* 면책 고지 + 입력 영역 */}
        <DisclaimerBanner />
        <ChatInput onSend={handleSend} disabled={isStreaming} />
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

      {/* 모바일 출처 바텀시트 */}
      <Sheet open={!!mobileSourceSheet} onOpenChange={() => setMobileSourceSheet(null)}>
        <SheetContent side="bottom" className="h-[70vh]">
          <SheetHeader>
            <SheetTitle>
              {mobileSourceSheet?.insuranceCompany} — {mobileSourceSheet?.articleTitle}
            </SheetTitle>
          </SheetHeader>
          {mobileSourceSheet && (
            <SourceTabContent articleId={mobileSourceSheet.articleId} />
          )}
        </SheetContent>
      </Sheet>
    </div>
  );
}
