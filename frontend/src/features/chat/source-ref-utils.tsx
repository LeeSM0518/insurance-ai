import type { ReactNode } from 'react';
import type { Source } from '@/types/chat';

interface SourceButtonOptions {
  sources?: Source[];
  activeSourceIndex?: number | null;
  onMobileSourceTap?: (source: Source) => void;
  renderWrapper?: (index: number, isActive: boolean, key: number, source?: Source) => ReactNode;
}

// 문자열 내 [N] 패턴을 인라인 출처 버튼 React 엘리먼트로 변환
export function replaceSourceRefs(text: string, options: SourceButtonOptions = {}): ReactNode[] {
  const { activeSourceIndex, renderWrapper } = options;
  const parts = text.split(/(\[\d+\])/);
  return parts.map((part, i) => {
    const match = part.match(/^\[(\d+)\]$/);
    if (match) {
      const index = parseInt(match[1], 10);
      const isActive = activeSourceIndex === index;
      const source = options.sources?.[index - 1];
      if (renderWrapper) {
        return renderWrapper(index, isActive, i, source);
      }
      // 기본 버튼 렌더링
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
export function processChildren(children: ReactNode, options: SourceButtonOptions = {}): ReactNode {
  if (typeof children === 'string') {
    if (/\[\d+\]/.test(children)) return replaceSourceRefs(children, options);
    return children;
  }
  if (Array.isArray(children)) {
    return children.map((child, i) => (
      <span key={i}>{processChildren(child, options)}</span>
    ));
  }
  return children;
}
