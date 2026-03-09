import Markdown from 'react-markdown';
import { processChildren } from './source-ref-utils';

interface StreamingMessageProps {
  content: string;
  onSourceClick?: (index: number) => void;
}

export function StreamingMessage({ content, onSourceClick }: StreamingMessageProps) {
  return (
    <div className="rounded-lg px-4 py-3 w-fit max-w-[85%] bg-chat-ai-bg mr-auto">
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
            p: ({ children }) => <p>{processChildren(children)}</p>,
            li: ({ children }) => <li>{processChildren(children)}</li>,
          }}
        >
          {content}
        </Markdown>
        {/* 깜빡이는 커서 */}
        <span className="inline-block w-[2px] h-[1em] bg-foreground animate-blink align-middle ml-0.5" />
      </div>
    </div>
  );
}
