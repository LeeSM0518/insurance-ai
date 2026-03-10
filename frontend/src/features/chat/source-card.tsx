import { useNavigate } from 'react-router';
import { FileText } from 'lucide-react';
import type { Source } from '@/types/chat';
import { CONTENT_TYPE_LABELS, type ContentType } from '@/types/search';

interface SourceCardProps {
  source: Source;
  index: number;
  isActive?: boolean;
  onSourceClick?: (source: Source, index: number) => void;
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
        <span className="text-sm text-muted-foreground truncate">{source.insuranceCompany}</span>
      </div>
      <p className="mt-1 text-sm truncate">{source.productName}</p>
      <p className="mt-0.5 text-sm font-medium truncate">
        {[source.articleNumber, source.articleTitle].filter(Boolean).join(' ') ||
          CONTENT_TYPE_LABELS[source.contentType as ContentType] ||
          source.contentType}
      </p>
      <div className="mt-1 flex items-center justify-end">
        {source.dataSourceId && (
          <a
            href={`/api/v1/policies/pdf/${source.dataSourceId}`}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-0.5 text-xs text-primary hover:no-underline"
            onClick={(e) => e.stopPropagation()}
            title="원본 PDF"
          >
            <FileText className="size-3" />
            PDF
          </a>
        )}
      </div>
    </button>
  );
}
