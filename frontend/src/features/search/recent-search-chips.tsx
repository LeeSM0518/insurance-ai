import { Clock, X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';

interface RecentSearchChipsProps {
  searches: string[];
  onSelect: (query: string) => void;
  onRemove: (query: string) => void;
}

export function RecentSearchChips({ searches, onSelect, onRemove }: RecentSearchChipsProps) {
  if (searches.length === 0) return null;

  return (
    <section aria-labelledby="recent-search-label" className="space-y-2">
      <h2 id="recent-search-label" className="flex items-center gap-1 text-sm font-medium text-muted-foreground">
        <Clock className="size-3.5" />
        최근 검색
      </h2>
      <div className="flex flex-wrap gap-1.5">
        {searches.map((query) => (
          <Badge key={query} variant="secondary" className="cursor-pointer gap-1 pr-1">
            <span onClick={() => onSelect(query)}>{query}</span>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onRemove(query);
              }}
              aria-label={`검색어 '${query}' 삭제`}
              className="rounded-full p-0.5 hover:bg-muted-foreground/20"
            >
              <X className="size-3" />
            </button>
          </Badge>
        ))}
      </div>
    </section>
  );
}
