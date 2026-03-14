import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useBlocker, useSearchParams } from 'react-router';
import { AlertTriangle, ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';
import { ChatHeader } from '@/features/chat/chat-header';
import { ChatSidebar } from '@/features/chat/chat-sidebar';
import { ChatMessage } from '@/features/chat/chat-message';
import { SourceCards } from '@/features/chat/source-cards';
import { SuggestedQuestions } from '@/features/chat/suggested-questions';
import { ChatInput, type ChatInputHandle } from '@/features/chat/chat-input';
import { ChatWelcome } from '@/features/chat/chat-welcome';
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
import { CONTENT_TYPE_LABELS, type ContentType } from '@/types/search';
import { trackEvent } from '@/lib/analytics';

const SOURCE_PANEL_MIN = 280;
const SOURCE_PANEL_DEFAULT = 360;

const MAX_TABS = 5;
const MAX_RETRIES = 3;

export default function ChatPage() {
  const isDesktop = useMediaQuery('(min-width: 1025px)');
  const [searchParams, setSearchParams] = useSearchParams();
  const [activeSessionId, setActiveSessionId] = useLocalStorage<string | null>('chat-active-session', null);
  const [isNewChatMode, setIsNewChatMode] = useState(false);
  const { sessions, createSession, deleteSession, refreshSessions, updateSessionTitle } = useChatSessions();
  const [sidebarOpen, setSidebarOpen] = useLocalStorage('chat-sidebar-open', isDesktop);
  const [openTabs, setOpenTabs] = useState<SourceTab[]>([]);
  const [activeTabId, setActiveTabId] = useState<string | null>(null);
  const [sourcePanelWidth, setSourcePanelWidth] = useLocalStorage('chat-source-panel-width', SOURCE_PANEL_DEFAULT);
  const [mobileSourceSheet, setMobileSourceSheet] = useState<Source | null>(null);
  const initialQueryProcessed = useRef(false);
  const chatInputRef = useRef<ChatInputHandle>(null);

  // 스트리밍 채팅 Hook
  const { messages, streamingState, isStreaming, sendMessage, retryStreaming } =
    useStreamingChat(activeSessionId);

  // 스크롤 컨테이너 ref + 자동 스크롤
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const { isAtBottom, scrollToBottom, forceScrollToBottom } = useAutoScroll({
    containerRef: scrollContainerRef,
    deps: [messages, streamingState.streamingContent, streamingState.phase],
  });

  const handleSourceResize = useCallback((width: number) => {
    setSourcePanelWidth(width);
  }, [setSourcePanelWidth]);

  // 화면 너비의 50%를 최대값으로 사용 (창 리사이즈 반영)
  const [windowWidth, setWindowWidth] = useState(window.innerWidth);
  useEffect(() => {
    const handleResize = () => setWindowWidth(window.innerWidth);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);
  const sourcePanelMax = Math.max(SOURCE_PANEL_MIN, Math.floor(windowWidth * 0.5));

  const { handleMouseDown: handleResizeMouseDown } = useResizeHandle({
    minWidth: SOURCE_PANEL_MIN,
    maxWidth: sourcePanelMax,
    onResize: handleSourceResize,
    direction: 'left',
  });

  const session = useMemo(
    () => sessions.find((s) => s.id === activeSessionId),
    [sessions, activeSessionId],
  );

  const handleSelectSession = (id: string) => {
    trackEvent('chat', 'select_session', id);
    setActiveSessionId(id);
    setIsNewChatMode(false);
    if (!isDesktop) setSidebarOpen(false);
    setOpenTabs([]);
    setActiveTabId(null);
    // 세션 전환 시 하단으로 스크롤
    setTimeout(() => scrollToBottom('instant'), 0);
  };

  const handleNewChat = () => {
    trackEvent('chat', 'new_chat');
    setActiveSessionId(null);
    setIsNewChatMode(true);
    if (!isDesktop) setSidebarOpen(false);
    setOpenTabs([]);
    setActiveTabId(null);
    // 새 대화 시 입력 필드에 자동 포커스
    setTimeout(() => chatInputRef.current?.focus(), 0);
  };

  // 최신 세션 자동 선택 (새 대화 모드가 아닐 때만)
  useEffect(() => {
    if (isNewChatMode || sessions.length === 0) return;

    const isValid = activeSessionId && sessions.some((s) => s.id === activeSessionId);
    if (!isValid) {
      setActiveSessionId(sessions[0].id);
    }
  }, [sessions, isNewChatMode]); // eslint-disable-line react-hooks/exhaustive-deps

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

  const sendingRef = useRef(false);

  const handleSend = useCallback(async (message: string) => {
    // 동시 호출 방지
    if (sendingRef.current) return;
    sendingRef.current = true;
    trackEvent('chat', 'send_message');

    try {
      if (!activeSessionId) {
        // 새 세션 생성 (initialMessage가 USER 메시지로 저장됨)
        const newSessionId = await createSession(message);
        setActiveSessionId(newSessionId);
        setIsNewChatMode(false);
        // newSessionId를 직접 전달하여 React 상태 비동기 문제 회피
        sendMessage(message, true, newSessionId);
      } else {
        // 기존 세션: /messages 엔드포인트 (USER 메시지 저장 + AI 응답)
        sendMessage(message);
      }
      // 메시지 전송 후 최하단으로 스크롤 (키보드 상태 무관하게 강제)
      forceScrollToBottom('smooth');
    } catch {
      toast.error('대화를 시작할 수 없습니다');
    } finally {
      sendingRef.current = false;
    }
  }, [activeSessionId, createSession, sendMessage, setActiveSessionId, forceScrollToBottom]);

  const handleSuggestedQuestion = (question: string) => {
    if (activeSessionId) {
      trackEvent('chat', 'suggested_question', question);
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

  const handleOpenSource = (source: Source, index: number, defaultTab?: 'content' | 'pdf') => {
    trackEvent('chat', 'source_click', source.articleId);

    // 이미 열린 탭이면 활성화 (+ defaultTab 변경 시 업데이트)
    if (openTabs.some((tab) => tab.articleId === source.articleId)) {
      if (defaultTab) {
        setOpenTabs((prev) =>
          prev.map((tab) => tab.articleId === source.articleId ? { ...tab, defaultTab } : tab),
        );
      }
      setActiveTabId(source.articleId);
      return;
    }

    const newTab: SourceTab = {
      articleId: source.articleId,
      articleNumber: source.articleNumber,
      articleTitle: source.articleTitle,
      insuranceCompany: source.insuranceCompany,
      sourceIndex: index + 1,
      defaultTab,
    };

    // 최대 탭 초과 시 가장 오래된 탭 제거
    const updated =
      openTabs.length >= MAX_TABS ? [...openTabs.slice(1), newTab] : [...openTabs, newTab];

    setOpenTabs(updated);
    setActiveTabId(source.articleId);
  };

  const handleOpenPdf = (source: Source, index: number) => {
    handleOpenSource(source, index, 'pdf');
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

  // 스트리밍 완료 시 세션 타이틀 즉시 반영 + DB 동기화
  const prevPhaseRef = useRef(streamingState.phase);
  useEffect(() => {
    // fading_out 단계에서 타이틀 즉시 반영 (INITIAL_STATE 리셋 전에 처리)
    if (streamingState.phase === 'fading_out' && streamingState.title && activeSessionId) {
      updateSessionTitle(activeSessionId, streamingState.title);
    }
    // idle 전환 시 DB 동기화 (기존 로직 유지)
    if (prevPhaseRef.current !== 'idle' && streamingState.phase === 'idle') {
      refreshSessions();
    }
    prevPhaseRef.current = streamingState.phase;
  }, [streamingState.phase, streamingState.title, activeSessionId, updateSessionTitle, refreshSessions]);

  // 스트리밍 중 에러 toast 표시
  useEffect(() => {
    if (streamingState.phase === 'error' && streamingState.errorMessage) {
      trackEvent('chat', 'stream_error', streamingState.errorMessage);
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

  const showWelcome = !activeSessionId && messages.length === 0 && !isStreaming;

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
            className="h-full overflow-y-auto px-4 pt-3 pb-28 desktop:pb-3 space-y-4"
            role="log"
            aria-live="polite"
          >
            {showWelcome && (
              <ChatWelcome onSelectQuestion={handleSend} />
            )}

            {messages.map((message) => (
              <div key={message.id}>
                {message.role === 'ASSISTANT' && message.sources && message.sources.length > 0 && (
                  <SourceCards
                    sources={message.sources}
                    onSourceClick={isDesktop ? handleOpenSource : undefined}
                    onPdfClick={isDesktop ? handleOpenPdf : undefined}
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
                    onPdfClick={isDesktop ? handleOpenPdf : undefined}
                    activeArticleId={isDesktop ? activeTabId : undefined}
                  />
                )}

                {/* streaming / fading_out 단계: 실시간 텍스트 + fade 효과 */}
                {(streamingState.phase === 'streaming' || streamingState.phase === 'fading_out') && streamingState.streamingContent && (
                  <StreamingMessage
                    content={streamingState.streamingContent}
                    isFadingOut={streamingState.phase === 'fading_out'}
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
                <Button variant="outline" size="sm" onClick={() => { trackEvent('chat', 'stream_retry', String(streamingState.retryCount)); retryStreaming(); }}>
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

        {/* 면책 고지 + 입력 영역 - 모바일: 탭바 위 고정, 데스크톱: flex 흐름 */}
        <div className="fixed bottom-tab-bar-full left-0 right-0 z-40 bg-background desktop:static desktop:bottom-auto desktop:z-auto">
          <DisclaimerBanner />
          <ChatInput ref={chatInputRef} onSend={handleSend} disabled={isStreaming} />
        </div>
      </div>

      {/* 데스크톱 출처 사이드패널 (리사이즈 가능) */}
      {isDesktop && openTabs.length > 0 && (
        <aside className="relative flex-shrink-0 border-l" style={{ width: sourcePanelWidth }}>
          {/* 리사이즈 핸들 - 원형 버튼 + 좌우 쉐브론 */}
          <div
            className="absolute -left-3 top-0 bottom-0 w-6 cursor-col-resize z-10 group flex items-center justify-center"
            onMouseDown={(e) => handleResizeMouseDown(e, sourcePanelWidth)}
          >
            <div className="flex items-center justify-center w-6 h-6 rounded-full border bg-background shadow-sm group-hover:bg-accent group-active:bg-accent group-hover:shadow transition-all">
              <ChevronLeft className="w-3 h-3 text-muted-foreground -mr-0.5" />
              <ChevronRight className="w-3 h-3 text-muted-foreground -ml-0.5" />
            </div>
          </div>
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
              {mobileSourceSheet?.insuranceCompany} —{' '}
              {mobileSourceSheet?.articleTitle ||
                CONTENT_TYPE_LABELS[mobileSourceSheet?.contentType as ContentType] ||
                mobileSourceSheet?.contentType}
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
