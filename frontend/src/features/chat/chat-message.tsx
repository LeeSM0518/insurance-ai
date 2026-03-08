import { cva } from 'class-variance-authority';
import Markdown from 'react-markdown';
import { CopyShareButtons } from './copy-share-buttons';
import { SourceTooltip } from './source-tooltip';
import { processChildren } from './source-ref-utils';
import type { ChatMessage as ChatMessageType, Source } from '@/types/chat';

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
                p: ({ children }) => <p>{processChildren(children, sourceButtonOptions)}</p>,
                li: ({ children }) => <li>{processChildren(children, sourceButtonOptions)}</li>,
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
