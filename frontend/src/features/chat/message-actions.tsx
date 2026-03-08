import { useState } from 'react';
import { Copy, Share2, ThumbsUp, ThumbsDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

type FeedbackType = 'up' | 'down' | null;

interface MessageActionsProps {
  content: string;
  messageId: string;
  className?: string;
}

export function MessageActions({ content, messageId, className }: MessageActionsProps) {
  const [feedback, setFeedback] = useState<FeedbackType>(null);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      toast.success('복사되었습니다');
    } catch {
      toast.info('준비 중입니다');
    }
  };

  const handleShare = async () => {
    if (navigator.share) {
      try {
        await navigator.share({ text: content });
      } catch {
        // 사용자가 공유 취소한 경우 무시
      }
    } else {
      toast.info('준비 중입니다');
    }
  };

  const handleFeedback = (type: 'up' | 'down') => {
    setFeedback((prev) => (prev === type ? null : type));
  };

  return (
    <div className={cn('flex gap-1', className)} data-message-id={messageId}>
      <Button variant="ghost" size="icon-xs" onClick={handleCopy} aria-label="복사">
        <Copy className="size-3.5" />
      </Button>
      <Button variant="ghost" size="icon-xs" onClick={handleShare} aria-label="공유">
        <Share2 className="size-3.5" />
      </Button>
      <Button
        variant="ghost"
        size="icon-xs"
        onClick={() => handleFeedback('up')}
        aria-label="좋아요"
        aria-pressed={feedback === 'up'}
        className={cn(feedback === 'up' && 'text-primary bg-primary/10')}
      >
        <ThumbsUp className="size-3.5" />
      </Button>
      <Button
        variant="ghost"
        size="icon-xs"
        onClick={() => handleFeedback('down')}
        aria-label="싫어요"
        aria-pressed={feedback === 'down'}
        className={cn(feedback === 'down' && 'text-primary bg-primary/10')}
      >
        <ThumbsDown className="size-3.5" />
      </Button>
    </div>
  );
}
