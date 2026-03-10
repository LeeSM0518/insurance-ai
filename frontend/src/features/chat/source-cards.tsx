import { SourceCard } from './source-card';
import type { Source } from '@/types/chat';

interface SourceCardsProps {
  sources: Source[];
  onSourceClick?: (source: Source, index: number) => void;
  activeArticleId?: string | null;
}

export function SourceCards({ sources, onSourceClick, activeArticleId }: SourceCardsProps) {
  if (sources.length === 0) return null;

  return (
    <div className="mb-2">
      <p className="text-sm text-muted-foreground mb-1.5">참고 약관</p>
      <div className="flex gap-2 overflow-x-auto scrollbar-hide pb-1">
        {sources.map((source, index) => (
          <SourceCard
            key={source.articleId}
            source={source}
            index={index}
            isActive={activeArticleId === source.articleId}
            onSourceClick={onSourceClick}
          />
        ))}
      </div>
    </div>
  );
}
