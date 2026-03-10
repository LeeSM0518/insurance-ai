import { Copy, Share2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { trackEvent } from '@/lib/analytics';

interface CopyShareButtonsProps {
  content: string;
  className?: string;
}

export function CopyShareButtons({ content, className }: CopyShareButtonsProps) {
  const handleCopy = async () => {
    try {
      trackEvent('policy', 'copy');
      await navigator.clipboard.writeText(content);
      toast.success('복사되었습니다');
    } catch {
      toast.info('준비 중입니다');
    }
  };

  const handleShare = async () => {
    if (navigator.share) {
      try {
        trackEvent('policy', 'share');
        await navigator.share({ text: content });
      } catch {
        // 사용자가 공유 취소한 경우 무시
      }
    } else {
      toast.info('준비 중입니다');
    }
  };

  return (
    <div className={cn('flex gap-1', className)}>
      <Button variant="ghost" size="icon-xs" onClick={handleCopy} aria-label="복사">
        <Copy className="size-3" />
      </Button>
      <Button variant="ghost" size="icon-xs" onClick={handleShare} aria-label="공유">
        <Share2 className="size-3" />
      </Button>
    </div>
  );
}
