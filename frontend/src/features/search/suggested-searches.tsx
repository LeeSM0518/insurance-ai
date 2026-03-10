import { Flame } from 'lucide-react';
import { Badge } from '@/components/ui/badge';

interface SuggestedSearchesProps {
  suggestions: string[];
  onSelect: (query: string) => void;
}

export function SuggestedSearches({ suggestions, onSelect }: SuggestedSearchesProps) {
  if (suggestions.length === 0) return null;

  return (
    <section aria-labelledby="suggested-search-label" className="space-y-2">
      <h2 id="suggested-search-label" className="flex items-center gap-1 text-base font-medium text-muted-foreground">
        <Flame className="size-4" />
        추천 검색어
      </h2>
      <div className="flex flex-wrap gap-1.5">
        {suggestions.map((suggestion) => (
          <Badge
            key={suggestion}
            variant="outline"
            className="cursor-pointer text-primary"
            onClick={() => onSelect(suggestion)}
          >
            #{suggestion}
          </Badge>
        ))}
      </div>
    </section>
  );
}
