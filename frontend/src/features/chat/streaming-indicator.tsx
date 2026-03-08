import { Loader2 } from 'lucide-react';
import type { StreamingState } from '@/hooks/use-streaming-chat';

interface StreamingIndicatorProps {
  phase: StreamingState['phase'];
}

export function StreamingIndicator({ phase }: StreamingIndicatorProps) {
  if (phase === 'searching') {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground py-2">
        <Loader2 className="size-4 animate-spin" />
        <span>관련 약관을 검색 중입니다...</span>
      </div>
    );
  }

  if (phase === 'generating') {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground py-2">
        <div className="flex gap-1">
          <span className="size-1.5 rounded-full bg-muted-foreground animate-blink" />
          <span
            className="size-1.5 rounded-full bg-muted-foreground animate-blink"
            style={{ animationDelay: '0.2s' }}
          />
          <span
            className="size-1.5 rounded-full bg-muted-foreground animate-blink"
            style={{ animationDelay: '0.4s' }}
          />
        </div>
        <span>답변을 생성 중입니다...</span>
      </div>
    );
  }

  return null;
}
