import { PolicyMeta } from '@/features/policy/policy-meta';
import { PolicyContent } from '@/features/policy/policy-content';
import { SupplementarySection } from '@/features/policy/supplementary-section';
import {
  MOCK_POLICY_ARTICLES_MAP,
  MOCK_SURRENDER_VALUES,
  MOCK_PREMIUM_EXAMPLES,
  MOCK_PRICE_INDICES,
} from '@/mocks/policy-data';

interface SourceTabContentProps {
  articleId: string;
}

export function SourceTabContent({ articleId }: SourceTabContentProps) {
  const article = MOCK_POLICY_ARTICLES_MAP[articleId];

  if (!article) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-sm text-muted-foreground">약관 정보를 찾을 수 없습니다</p>
      </div>
    );
  }

  // art-001에만 보충 데이터 표시 (Mock)
  const isFirstArticle = articleId === 'art-001';

  return (
    <div className="flex-1 overflow-y-auto p-4 space-y-4">
      <PolicyMeta article={article} />
      <PolicyContent
        articleNumber={article.articleNumber}
        articleTitle={article.articleTitle}
        content={article.content}
      />
      <SupplementarySection
        surrenderValues={isFirstArticle ? MOCK_SURRENDER_VALUES : undefined}
        premiumExamples={isFirstArticle ? MOCK_PREMIUM_EXAMPLES : undefined}
        priceIndices={isFirstArticle ? MOCK_PRICE_INDICES : undefined}
      />
    </div>
  );
}
