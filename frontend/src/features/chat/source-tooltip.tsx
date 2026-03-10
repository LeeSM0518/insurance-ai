import type { ReactNode } from 'react';
import { Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip';
import { useMediaQuery, BREAKPOINTS } from '@/hooks/use-media-query';
import type { Source } from '@/types/chat';

interface SourceTooltipProps {
  source: Source;
  index: number;
  isActive: boolean;
  onMobileSourceTap?: (source: Source) => void;
  children: ReactNode;
}

export function SourceTooltip({
  source,
  onMobileSourceTap,
  children,
}: SourceTooltipProps) {
  const isDesktop = useMediaQuery(BREAKPOINTS.desktop);

  if (!isDesktop) {
    // 모바일: Tooltip 대신 탭 시 바텀시트 콜백
    return (
      <span
        onClick={(e) => {
          e.stopPropagation();
          onMobileSourceTap?.(source);
        }}
      >
        {children}
      </span>
    );
  }

  // 데스크톱: 호버 Tooltip
  return (
    <Tooltip>
      <TooltipTrigger render={<span />}>
        {children}
      </TooltipTrigger>
      <TooltipContent
        side="top"
        className="max-w-[240px] bg-card text-card-foreground border shadow-md p-3"
      >
        <div className="space-y-1">
          <p className="text-xs font-medium">{source.insuranceCompany}</p>
          <p className="text-xs text-muted-foreground">{source.productName}</p>
          {source.articleTitle && (
            <p className="text-xs text-muted-foreground">
              {source.articleNumber} {source.articleTitle}
            </p>
          )}
        </div>
      </TooltipContent>
    </Tooltip>
  );
}
