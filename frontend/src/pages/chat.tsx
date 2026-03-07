import { useMemo } from 'react';
import { useParams } from 'react-router';
import { ChatHeader } from '@/features/chat/chat-header';
import { ChatMessage } from '@/features/chat/chat-message';
import { SourceCards } from '@/features/chat/source-cards';
import { SuggestedQuestions } from '@/features/chat/suggested-questions';
import { ChatInput } from '@/features/chat/chat-input';
import { DisclaimerBanner } from '@/features/chat/disclaimer-banner';
import { useMediaQuery } from '@/hooks/use-media-query';
import { MOCK_SESSIONS, MOCK_MESSAGES } from '@/mocks/chat-data';
import { toast } from 'sonner';

export default function ChatPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const isDesktop = useMediaQuery('(min-width: 1025px)');

  const session = useMemo(() => MOCK_SESSIONS.find((s) => s.id === sessionId), [sessionId]);
  const messages = useMemo(() => (sessionId ? MOCK_MESSAGES[sessionId] || [] : []), [sessionId]);

  const handleSend = (message: string) => {
    toast.info(`메시지 전송: "${message}" (Mock — 실제 전송 없음)`);
  };

  const handleSuggestedQuestion = (question: string) => {
    toast.info(`추천 질문 선택: "${question}" (Mock — 실제 전송 없음)`);
  };

  const title = session?.title || '새 대화';

  return (
    <div className="flex-1 flex flex-col desktop:flex-row">
      {/* 채팅 영역 */}
      <div className="flex-1 flex flex-col min-w-0">
        <ChatHeader title={title} />

        {/* 메시지 영역 */}
        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4" role="log" aria-live="polite">
          {messages.map((message) => (
            <div key={message.id}>
              {/* 출처 카드 (AI 메시지 위에) */}
              {message.role === 'ASSISTANT' && message.sources && message.sources.length > 0 && (
                <SourceCards sources={message.sources} />
              )}
              <ChatMessage message={message} />
              {/* 추천 질문 (AI 메시지 뒤에) */}
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

      {/* 데스크톱 사이드패널 */}
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
