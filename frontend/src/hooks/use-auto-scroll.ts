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
  /** 강제 최하단 스크롤 (isAtBottom 상태를 true로 설정 후 스크롤) */
  forceScrollToBottom: (behavior?: ScrollBehavior) => void;
}

export function useAutoScroll({
  containerRef,
  deps,
  threshold = 100,
}: UseAutoScrollOptions): UseAutoScrollReturn {
  const [isAtBottom, setIsAtBottom] = useState(true);
  const isAtBottomRef = useRef(true);
  const rafRef = useRef<number | null>(null);
  const initialMount = useRef(true);

  // state ↔ ref 동기화
  useEffect(() => {
    isAtBottomRef.current = isAtBottom;
  }, [isAtBottom]);

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
      isAtBottomRef.current = true;
    },
    [containerRef],
  );

  // 강제 스크롤: 메시지 전송 시 isAtBottom을 먼저 true로 설정
  const forceScrollToBottom = useCallback(
    (behavior: ScrollBehavior = 'smooth') => {
      setIsAtBottom(true);
      isAtBottomRef.current = true;
      requestAnimationFrame(() => {
        const container = containerRef.current;
        if (!container) return;
        container.scrollTo({ top: container.scrollHeight, behavior });
      });
    },
    [containerRef],
  );

  // 1. 스크롤 이벤트 리스너 (rAF throttle)
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

  // 2. scrollend 이벤트 (관성 스크롤 완료 후 정확한 위치 판정)
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handleScrollEnd = () => {
      setIsAtBottom(checkIfAtBottom());
    };

    container.addEventListener('scrollend', handleScrollEnd, { passive: true });
    return () => container.removeEventListener('scrollend', handleScrollEnd);
  }, [containerRef, checkIfAtBottom]);

  // 3. visualViewport resize 감지 (모바일 키보드 대응, Safari fallback)
  useEffect(() => {
    const viewport = window.visualViewport;
    if (!viewport) return;

    const handleViewportResize = () => {
      const atBottom = checkIfAtBottom();
      setIsAtBottom(atBottom);
      if (atBottom) {
        scrollToBottom('instant');
      }
    };

    viewport.addEventListener('resize', handleViewportResize);
    return () => viewport.removeEventListener('resize', handleViewportResize);
  }, [checkIfAtBottom, scrollToBottom]);

  // 4. ResizeObserver (콘텐츠 높이 변화 감지: 스트리밍 마크다운 렌더링 등)
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const observer = new ResizeObserver(() => {
      if (isAtBottomRef.current) {
        scrollToBottom('instant');
      }
    });

    // 스크롤 컨테이너의 내부 콘텐츠 높이 변화 감지
    for (const child of container.children) {
      observer.observe(child);
    }

    return () => observer.disconnect();
  }, [containerRef, scrollToBottom]);

  // 5. 초기 마운트 시 하단으로 스크롤
  useEffect(() => {
    if (initialMount.current) {
      initialMount.current = false;
      scrollToBottom('instant');
    }
  }, [scrollToBottom]);

  // 6. deps 변경 시 isAtBottom이면 자동 스크롤
  useEffect(() => {
    if (isAtBottom) {
      scrollToBottom('instant');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { isAtBottom, scrollToBottom, forceScrollToBottom };
}
