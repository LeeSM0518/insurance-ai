import { cva } from 'class-variance-authority';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { MessageActions } from './message-actions';
import { SourceTooltip } from './source-tooltip';
import { processChildren } from './source-ref-utils';
import type { ChatMessage as ChatMessageType, Source } from '@/types/chat';

const chatMessageVariants = cva('rounded-lg px-4 py-3 w-fit max-w-[85%] animate-[fade-in_0.3s_ease-in-out]', {
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
  onMobileSourceTap?: (source: Source) => void;
  activeSourceIndex?: number | null;
}

export function ChatMessage({ message, onSourceClick, onMobileSourceTap, activeSourceIndex }: ChatMessageProps) {
  const isAssistant = message.role === 'ASSISTANT';

  const sourceButtonOptions = {
    sources: message.sources,
    activeSourceIndex,
    onMobileSourceTap,
    renderWrapper: message.sources
      ? (index: number, isActive: boolean, key: number, source?: Source) => {
          const button = (
            <button
              className={`inline-flex items-center justify-center size-4 rounded-full text-[10px] font-medium transition-colors ${
                isActive
                  ? 'bg-primary/20 text-primary ring-1 ring-primary'
                  : 'bg-primary/10 text-primary hover:bg-primary/20'
              }`}
              data-source-index={index}
            >
              {index}
            </button>
          );

          if (source) {
            return (
              <SourceTooltip
                key={key}
                source={source}
                index={index}
                isActive={isActive}
                onMobileSourceTap={onMobileSourceTap}
              >
                {button}
              </SourceTooltip>
            );
          }

          return button;
        }
      : undefined,
  };

  return (
    <div className={chatMessageVariants({ role: message.role === 'USER' ? 'user' : 'assistant' })}>
      {isAssistant ? (
        <div>
          <div
            className="prose max-w-none leading-relaxed [&_h2]:text-lg [&_h2]:font-semibold [&_h3]:text-base [&_h3]:font-semibold [&_p]:text-base [&_li]:text-base [&_blockquote]:text-base"
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
              remarkPlugins={[remarkGfm]}
              components={{
                // h1은 채팅에서 너무 크므로 h2로 렌더링
                h1: ({ children }) => <h2>{processChildren(children, sourceButtonOptions)}</h2>,
                h2: ({ children }) => <h2>{processChildren(children, sourceButtonOptions)}</h2>,
                h3: ({ children }) => <h3>{processChildren(children, sourceButtonOptions)}</h3>,
                // hr(---) 제거 — 제목만으로 섹션 구분 충분
                hr: () => null,
                p: ({ children }) => <p>{processChildren(children, sourceButtonOptions)}</p>,
                blockquote: ({ children }) => <blockquote>{processChildren(children, sourceButtonOptions)}</blockquote>,
                li: ({ children }) => <li>{processChildren(children, sourceButtonOptions)}</li>,
                td: ({ children }) => <td>{processChildren(children, sourceButtonOptions)}</td>,
                th: ({ children }) => <th>{processChildren(children, sourceButtonOptions)}</th>,
                table: ({ children }) => (
                  <div className="overflow-x-auto my-2">
                    <table>{children}</table>
                  </div>
                ),
              }}
            >
              {message.content}
            </Markdown>
          </div>
          <MessageActions content={message.content} messageId={message.id} className="mt-2" />
        </div>
      ) : (
        <p className="text-base leading-relaxed">{message.content}</p>
      )}
    </div>
  );
}
