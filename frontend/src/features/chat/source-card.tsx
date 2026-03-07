import { useNavigate } from 'react-router';
import type { Source } from '@/types/chat';

interface SourceCardProps {
  source: Source;
  index: number;
  isActive?: boolean;
  onSourceClick?: (source: Source, index: number) => void;
}

function relevanceColor(score: number) {
  if (score >= 0.85) return 'text-relevance-high';
  if (score >= 0.6) return 'text-relevance-mid';
  return 'text-relevance-low';
}

export function SourceCard({ source, index, isActive, onSourceClick }: SourceCardProps) {
  const navigate = useNavigate();

  const handleClick = () => {
    if (onSourceClick) {
      onSourceClick(source, index);
    } else {
      navigate(`/policies/${source.articleId}`);
    }
  };

  return (
    <button
      className={`shrink-0 w-[180px] rounded-lg border p-3 text-left transition-all ${
        isActive
          ? 'border-primary bg-primary/5 shadow-card-hover'
          : 'bg-source-card-bg hover:shadow-card-hover'
      }`}
      onClick={handleClick}
      tabIndex={0}
    >
      <div className="flex items-center gap-1.5">
        <span className="inline-flex items-center justify-center size-5 rounded-full bg-primary/10 text-primary text-xs font-medium">
          {index + 1}
        </span>
        <span className="text-xs text-muted-foreground truncate">{source.insuranceCompany}</span>
      </div>
      <p className="mt-1 text-xs truncate">{source.productName}</p>
      <p className="mt-0.5 text-xs font-medium truncate">
        {source.articleNumber} {source.articleTitle}
      </p>
      <p className={`mt-1 text-[11px] font-medium ${relevanceColor(source.relevanceScore)}`}>
        관련도 {Math.round(source.relevanceScore * 100)}%
      </p>
    </button>
  );
}
