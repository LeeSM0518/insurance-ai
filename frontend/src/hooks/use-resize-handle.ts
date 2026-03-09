import { useCallback, useRef } from 'react';

interface UseResizeHandleOptions {
  minWidth: number;
  maxWidth: number;
  onResize: (width: number) => void;
  /** 드래그 방향: 'left'면 마우스가 왼쪽으로 갈수록 넓어짐 */
  direction?: 'left' | 'right';
}

export function useResizeHandle({
  minWidth,
  maxWidth,
  onResize,
  direction = 'right',
}: UseResizeHandleOptions) {
  const startXRef = useRef(0);
  const startWidthRef = useRef(0);

  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      const delta = direction === 'left'
        ? startXRef.current - e.clientX
        : e.clientX - startXRef.current;
      const newWidth = Math.min(maxWidth, Math.max(minWidth, startWidthRef.current + delta));
      onResize(newWidth);
    },
    [direction, minWidth, maxWidth, onResize],
  );

  const handleMouseUpRef = useRef<(() => void) | null>(null);

  const handleMouseDown = useCallback(
    (e: React.MouseEvent, currentWidth: number) => {
      e.preventDefault();
      startXRef.current = e.clientX;
      startWidthRef.current = currentWidth;
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';

      const onMouseUp = () => {
        document.removeEventListener('mousemove', handleMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      };
      handleMouseUpRef.current = onMouseUp;

      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', onMouseUp);
    },
    [handleMouseMove],
  );

  return { handleMouseDown };
}
