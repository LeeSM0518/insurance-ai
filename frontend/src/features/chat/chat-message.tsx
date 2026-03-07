import type { ReactNode } from 'react';
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
  activeSourceIndex?: number | null;
}

// 문자열 내 [N] 패턴을 인라인 출처 버튼 React 엘리먼트로 변환
function replaceSourceRefs(text: string, activeSourceIndex?: number | null): ReactNode[] {
  const parts = text.split(/(\[\d+\])/);
  return parts.map((part, i) => {
    const match = part.match(/^\[(\d+)\]$/);
    if (match) {
      const index = parseInt(match[1], 10);
      const isActive = activeSourceIndex === index;
      return (
        <button
          key={i}
          className={`inline-flex items-center justify-center size-4 rounded-full text-[10px] font-medium transition-colors ${
            isActive
              ? 'bg-primary/20 text-primary ring-1 ring-primary'
              : 'bg-primary/10 text-primary hover:bg-primary/20'
          }`}
          data-source-index={match[1]}
        >
          {match[1]}
        </button>
      );
    }
    return part;
  });
}

// React children을 재귀 순회하며 문자열 노드의 [N] 패턴을 버튼으로 변환
function processChildren(children: ReactNode, activeSourceIndex?: number | null): ReactNode {
  if (typeof children === 'string') {
    if (/\[\d+\]/.test(children)) return replaceSourceRefs(children, activeSourceIndex);
    return children;
  }
  if (Array.isArray(children)) {
    return children.map((child, i) => <span key={i}>{processChildren(child, activeSourceIndex)}</span>);
  }
  return children;
}

export function ChatMessage({ message, onSourceClick, activeSourceIndex }: ChatMessageProps) {
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
                p: ({ children }) => <p>{processChildren(children, activeSourceIndex)}</p>,
                li: ({ children }) => <li>{processChildren(children, activeSourceIndex)}</li>,
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
