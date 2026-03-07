import { useNavigate } from 'react-router';
import { Badge } from '@/components/ui/badge';
import { Coins, Calculator, TrendingUp } from 'lucide-react';
import type { SearchResultItem } from '@/types/policy';
import { CONTENT_TYPE_LABELS, type ContentType } from '@/types/search';

interface PolicyCardProps {
  item: SearchResultItem;
}

function relevanceColor(score: number) {
  if (score >= 0.85) return 'text-relevance-high';
  if (score >= 0.6) return 'text-relevance-mid';
  return 'text-relevance-low';
}

export function PolicyCard({ item }: PolicyCardProps) {
  const navigate = useNavigate();

  return (
    <article
      className="cursor-pointer rounded-lg border bg-card p-4 shadow-card transition-shadow hover:shadow-card-hover active:scale-[0.98]"
      onClick={() => navigate(`/policies/${item.id}`)}
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter') navigate(`/policies/${item.id}`);
      }}
    >
      {/* 보험사 · 상품명 */}
      <div className="text-xs text-muted-foreground">
        {item.insuranceCompany.name} · {item.productName}
      </div>

      {/* 조문 제목 */}
      <h3 className="mt-1 text-sm font-semibold">
        {item.articleNumber} {item.articleTitle}
      </h3>

      {/* 하이라이트 미리보기 */}
      <p
        className="mt-2 text-sm text-muted-foreground line-clamp-3 [&_mark]:bg-warning/30 [&_mark]:text-foreground [&_mark]:rounded-sm [&_mark]:px-0.5"
        dangerouslySetInnerHTML={{ __html: item.highlight }}
      />

      {/* 하단: 내용유형 · 관련도 · 보충 뱃지 */}
      <div className="mt-3 flex items-center gap-2 flex-wrap">
        <span className="text-xs text-muted-foreground">
          {CONTENT_TYPE_LABELS[item.contentType as ContentType] || item.contentType}
        </span>
        <span className="text-xs text-muted-foreground">·</span>
        <span className={`text-xs font-medium ${relevanceColor(item.relevanceScore)}`}>
          관련도 {Math.round(item.relevanceScore * 100)}%
        </span>

        {/* 보충 데이터 뱃지 */}
        <div className="ml-auto flex gap-1">
          {item.supplementary.hasSurrenderValues && (
            <Badge variant="secondary" className="gap-0.5 text-[10px]">
              <Coins className="size-3" />
              환급금
            </Badge>
          )}
          {item.supplementary.hasPremiumExamples && (
            <Badge variant="secondary" className="gap-0.5 text-[10px]">
              <Calculator className="size-3" />
              보험료
            </Badge>
          )}
          {item.supplementary.hasPriceIndices && (
            <Badge variant="secondary" className="gap-0.5 text-[10px]">
              <TrendingUp className="size-3" />
              가격지수
            </Badge>
          )}
        </div>
      </div>
    </article>
  );
}
