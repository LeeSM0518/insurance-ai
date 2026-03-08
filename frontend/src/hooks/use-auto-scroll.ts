import { useState, useEffect, useCallback, useRef, type RefObject } from 'react';

interface UseAutoScrollOptions {
  /** 스크롤 컨테이너 ref */
  containerRef: RefObject<HTMLDivElement | null>;
  /** 자동 스크롤 트리거 의존성 (메시지 배열, 스트리밍 콘텐츠 등) */
  deps: unknown[];
  /** 하단 판정 임계값 (px). 기본 100 */
  threshold?: number;
}

interface UseAutoScrollReturn {
  /** 사용자가 하단 근처에 있는지 여부 */
  isAtBottom: boolean;
  /** 최하단으로 스크롤 */
  scrollToBottom: (behavior?: ScrollBehavior) => void;
}

export function useAutoScroll({
  containerRef,
  deps,
  threshold = 100,
}: UseAutoScrollOptions): UseAutoScrollReturn {
  const [isAtBottom, setIsAtBottom] = useState(true);
  const rafRef = useRef<number | null>(null);
  const initialMount = useRef(true);

  const checkIfAtBottom = useCallback(() => {
    const container = containerRef.current;
    if (!container) return true;
    return container.scrollTop + container.clientHeight >= container.scrollHeight - threshold;
  }, [containerRef, threshold]);

  const scrollToBottom = useCallback(
    (behavior: ScrollBehavior = 'smooth') => {
      const container = containerRef.current;
      if (!container) return;
      container.scrollTo({ top: container.scrollHeight, behavior });
      setIsAtBottom(true);
    },
    [containerRef],
  );

  // 스크롤 이벤트 리스너 (rAF throttle)
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handleScroll = () => {
      if (rafRef.current !== null) return;
      rafRef.current = requestAnimationFrame(() => {
        setIsAtBottom(checkIfAtBottom());
        rafRef.current = null;
      });
    };

    container.addEventListener('scroll', handleScroll, { passive: true });
    return () => {
      container.removeEventListener('scroll', handleScroll);
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, [containerRef, checkIfAtBottom]);

  // 초기 마운트 시 하단으로 스크롤
  useEffect(() => {
    if (initialMount.current) {
      initialMount.current = false;
      scrollToBottom('instant');
    }
  }, [scrollToBottom]);

  // deps 변경 시 isAtBottom이면 자동 스크롤
  useEffect(() => {
    if (isAtBottom) {
      scrollToBottom('instant');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { isAtBottom, scrollToBottom };
}
