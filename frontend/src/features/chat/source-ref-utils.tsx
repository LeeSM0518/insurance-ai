import { isValidElement, cloneElement, type ReactNode, type ReactElement } from 'react';
import type { Source } from '@/types/chat';

interface SourceButtonOptions {
  sources?: Source[];
  activeSourceIndex?: number | null;
  onMobileSourceTap?: (source: Source) => void;
  renderWrapper?: (index: number, isActive: boolean, key: number, source?: Source) => ReactNode;
}

// 문자열 내 [N] 또는 [출처 N] 패턴을 인라인 출처 버튼 React 엘리먼트로 변환
export function replaceSourceRefs(text: string, options: SourceButtonOptions = {}): ReactNode[] {
  const { activeSourceIndex, renderWrapper } = options;
  const parts = text.split(/(\[출처\s*\d+\]|\[\d+\])/);
  return parts.map((part, i) => {
    const match = part.match(/^\[(?:출처\s*)?(\d+)\]$/);
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

// React children에서 텍스트만 추출
function extractText(children: ReactNode): string {
  if (typeof children === 'string') return children;
  if (typeof children === 'number') return String(children);
  if (Array.isArray(children)) return children.map(extractText).join('');
  if (isValidElement(children)) {
    const element = children as ReactElement<{ children?: ReactNode }>;
    if (element.props.children) return extractText(element.props.children);
  }
  return '';
}

// 출처 참조 패턴([N] 또는 [출처 N])만으로 구성된 텍스트인지 판별
export function isSourceRefOnly(children: ReactNode): boolean {
  const text = extractText(children);
  if (!text) return false;
  const stripped = text.replace(/\[(?:출처\s*)?\d+\]/g, '').trim();
  return stripped.length === 0 && /\[(?:출처\s*)?\d+\]/.test(text);
}

// React children을 재귀 순회하며 문자열 노드의 [N] 패턴을 버튼으로 변환
export function processChildren(children: ReactNode, options: SourceButtonOptions = {}): ReactNode {
  if (typeof children === 'string') {
    if (/\[(?:출처\s*)?\d+\]/.test(children)) return replaceSourceRefs(children, options);
    return children;
  }
  if (Array.isArray(children)) {
    return children.map((child, i) => (
      <span key={i}>{processChildren(child, options)}</span>
    ));
  }
  // React 요소(strong, em 등) 내부 children도 재귀 처리
  if (isValidElement(children)) {
    const element = children as ReactElement<{ children?: ReactNode }>;
    if (element.props.children) {
      return cloneElement(element, {}, processChildren(element.props.children, options));
    }
  }
  return children;
}
