import { useState } from 'react';
import { Copy, Share2, ThumbsUp, ThumbsDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { useFeedback } from './use-feedback';
import { FeedbackCategoryPopover } from './feedback-category-popover';
import type { FeedbackCategory } from './use-feedback';

interface MessageActionsProps {
  content: string;
  messageId: string;
  className?: string;
  initialFeedback?: {
    feedbackType: 'POSITIVE' | 'NEGATIVE';
    category?: string;
    comment?: string;
  } | null;
}

export function MessageActions({ content, messageId, className, initialFeedback = null }: MessageActionsProps) {
  const { feedbackType, isLoading, submitPositive, submitNegative, removeFeedback } = useFeedback({
    messageId,
    initialFeedback: initialFeedback
      ? {
          ...initialFeedback,
          category: initialFeedback.category as FeedbackCategory | undefined,
        }
      : null,
  });
  const [popoverOpen, setPopoverOpen] = useState(false);

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

  const handleThumbsUp = () => {
    if (isLoading) return;
    submitPositive();
  };

  const handleThumbsDown = () => {
    if (isLoading) return;
    // 이미 NEGATIVE면 해제
    if (feedbackType === 'NEGATIVE') {
      removeFeedback();
      return;
    }
    // Popover 열기
    setPopoverOpen(true);
  };

  const handleCategorySubmit = (category: FeedbackCategory, comment?: string) => {
    setPopoverOpen(false);
    submitNegative(category, comment);
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
        onClick={handleThumbsUp}
        aria-label="좋아요"
        aria-pressed={feedbackType === 'POSITIVE'}
        disabled={isLoading}
        className={cn(feedbackType === 'POSITIVE' && 'text-primary bg-primary/10')}
      >
        <ThumbsUp className="size-3.5" />
      </Button>
      <FeedbackCategoryPopover
        open={popoverOpen}
        onOpenChange={setPopoverOpen}
        onSubmit={handleCategorySubmit}
        isLoading={isLoading}
      >
        <Button
          variant="ghost"
          size="icon-xs"
          onClick={handleThumbsDown}
          aria-label="싫어요"
          aria-pressed={feedbackType === 'NEGATIVE'}
          disabled={isLoading}
          className={cn(feedbackType === 'NEGATIVE' && 'text-primary bg-primary/10')}
        >
          <ThumbsDown className="size-3.5" />
        </Button>
      </FeedbackCategoryPopover>
    </div>
  );
}
