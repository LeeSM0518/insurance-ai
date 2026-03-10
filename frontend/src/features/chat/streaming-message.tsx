import { useEffect, useRef, useState } from 'react';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { processChildren } from './source-ref-utils';

interface StreamingMessageProps {
  content: string;
  isFadingOut?: boolean;
  onSourceClick?: (index: number) => void;
}

export function StreamingMessage({ content, isFadingOut = false, onSourceClick }: StreamingMessageProps) {
  const [visible, setVisible] = useState(false);
  const mountedRef = useRef(false);

  // mount 직후 opacity 0 → 1 트랜지션
  useEffect(() => {
    if (!mountedRef.current) {
      mountedRef.current = true;
      requestAnimationFrame(() => setVisible(true));
    }
  }, []);

  return (
    <div
      className="rounded-lg px-4 py-3 w-fit max-w-[85%] bg-chat-ai-bg mr-auto transition-opacity duration-300 ease-in-out"
      style={{ opacity: isFadingOut ? 0 : (visible ? 1 : 0) }}
    >
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
            p: ({ children }) => <p>{processChildren(children)}</p>,
            li: ({ children }) => <li>{processChildren(children)}</li>,
            table: ({ children }) => (
              <div className="overflow-x-auto my-2">
                <table>{children}</table>
              </div>
            ),
          }}
        >
          {content}
        </Markdown>
        {/* 깜빡이는 커서 — fade-out 중에는 숨김 */}
        {!isFadingOut && (
          <span className="inline-block w-[2px] h-[1em] bg-foreground animate-blink align-middle ml-0.5" />
        )}
      </div>
    </div>
  );
}
