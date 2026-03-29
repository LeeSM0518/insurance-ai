import { useState, useCallback } from 'react';
import { Share2, Check, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { getReportApi } from '@/lib/api';
import { toast } from 'sonner';

interface ShareButtonProps {
  reportId: string;
  variant?: 'default' | 'outline' | 'ghost';
  size?: 'default' | 'sm' | 'icon';
}

const EXPIRY_OPTIONS = [
  { label: '1일', days: 1 },
  { label: '3일', days: 3 },
  { label: '7일', days: 7 },
];

export function ShareButton({ reportId, variant = 'outline', size = 'sm' }: ShareButtonProps) {
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  const handleShare = useCallback(async (days: number) => {
    setLoading(true);
    try {
      const api = getReportApi();
      const res = await api.createShareLink({
        reportId,
        shareLinkRequest: { expiresInDays: days },
      });
      const shareUrl = res.data.data?.shareUrl;
      if (shareUrl) {
        const fullUrl = `${window.location.origin}${shareUrl}`;
        await navigator.clipboard.writeText(fullUrl);
        setCopied(true);
        toast.success('공유 링크가 복사되었습니다');
        setTimeout(() => setCopied(false), 2000);
      }
    } catch {
      toast.error('공유 링크 생성에 실패했습니다');
    } finally {
      setLoading(false);
    }
  }, [reportId]);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button variant={variant} size={size} disabled={loading}>
            {loading ? (
              <Loader2 className="size-4 animate-spin" />
            ) : copied ? (
              <Check className="size-4" />
            ) : (
              <Share2 className="size-4" />
            )}
            공유
          </Button>
        }
      />
      <DropdownMenuContent align="end">
        {EXPIRY_OPTIONS.map(({ label, days }) => (
          <DropdownMenuItem key={days} onClick={() => handleShare(days)}>
            {label} 동안 공유
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
