import { cva } from 'class-variance-authority';
import Markdown from 'react-markdown';
import { CopyShareButtons } from './copy-share-buttons';
import type { ChatMessage as ChatMessageType } from '@/types/chat';

const chatMessageVariants = cva('rounded-lg px-4 py-3 max-w-[85%]', {
  variants: {
    role: {
      user: 'bg-chat-user-bg ml-auto',
      assistant: 'bg-chat-ai-bg mr-auto',
    },
  },
});

interface ChatMessageProps {
  message: ChatMessageType;
  onSourceClick?: (index: number) => void;
}

// 인라인 출처 [1], [2] 등을 클릭 가능한 링크로 변환
function processSourceLinks(content: string): string {
  return content.replace(
    /\[(\d+)\]/g,
    '<button class="inline-flex items-center justify-center size-5 rounded-full bg-primary/10 text-primary text-xs font-medium hover:bg-primary/20" data-source-index="$1">$1</button>',
  );
}

export function ChatMessage({ message, onSourceClick }: ChatMessageProps) {
  const isAssistant = message.role === 'ASSISTANT';

  return (
    <div className={chatMessageVariants({ role: message.role === 'USER' ? 'user' : 'assistant' })}>
      {isAssistant ? (
        <div>
          <div
            className="prose prose-sm max-w-none [&_h2]:text-base [&_h3]:text-sm [&_p]:text-sm [&_li]:text-sm [&_blockquote]:text-sm"
            onClick={(e) => {
              const target = e.target as HTMLElement;
              const button = target.closest('[data-source-index]');
              if (button) {
                const index = parseInt(button.getAttribute('data-source-index') || '0', 10);
                onSourceClick?.(index);
              }
            }}
          >
            <Markdown
              components={{
                // 출처 번호가 포함된 텍스트를 HTML로 처리
                p: ({ children }) => {
                  const text = String(children);
                  if (/\[\d+\]/.test(text)) {
                    return (
                      <p dangerouslySetInnerHTML={{ __html: processSourceLinks(text) }} />
                    );
                  }
                  return <p>{children}</p>;
                },
              }}
            >
              {message.content}
            </Markdown>
          </div>
          <CopyShareButtons content={message.content} className="mt-2" />
        </div>
      ) : (
        <p className="text-sm">{message.content}</p>
      )}
    </div>
  );
}
