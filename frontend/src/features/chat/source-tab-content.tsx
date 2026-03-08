import { useState } from 'react';
import { FileText } from 'lucide-react';
import { PolicyMeta } from '@/features/policy/policy-meta';
import { PolicyContent } from '@/features/policy/policy-content';
import { PdfViewer } from '@/features/policy/pdf-viewer';
import { SupplementarySection } from '@/features/policy/supplementary-section';
import {
  MOCK_POLICY_ARTICLES_MAP,
  MOCK_SURRENDER_VALUES,
  MOCK_PREMIUM_EXAMPLES,
  MOCK_PRICE_INDICES,
} from '@/mocks/policy-data';

type TabType = 'content' | 'pdf';

interface SourceTabContentProps {
  articleId: string;
}

export function SourceTabContent({ articleId }: SourceTabContentProps) {
  const article = MOCK_POLICY_ARTICLES_MAP[articleId];
  const [activeTab, setActiveTab] = useState<TabType>('content');

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

      {/* PDF 탭 전환 */}
      {article.hasPdfSource && article.dataSourceId && (
        <div className="flex gap-1 border-b" role="tablist">
          <button
            role="tab"
            aria-selected={activeTab === 'content'}
            className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border-b-2 transition-colors ${
              activeTab === 'content'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
            onClick={() => setActiveTab('content')}
          >
            <FileText className="size-3" />
            조문
          </button>
          <button
            role="tab"
            aria-selected={activeTab === 'pdf'}
            className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border-b-2 transition-colors ${
              activeTab === 'pdf'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
            onClick={() => setActiveTab('pdf')}
          >
            <FileText className="size-3" />
            원본 PDF
          </button>
        </div>
      )}

      {activeTab === 'content' ? (
        <>
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
        </>
      ) : (
        article.dataSourceId && <PdfViewer dataSourceId={article.dataSourceId} />
      )}
    </div>
  );
}
