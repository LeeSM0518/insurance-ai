import { useState } from 'react';
import { FileText, Loader2 } from 'lucide-react';
import { PolicyMeta } from '@/features/policy/policy-meta';
import { PolicyContent } from '@/features/policy/policy-content';
import { PdfViewer } from '@/features/policy/pdf-viewer';
import { SupplementarySection } from '@/features/policy/supplementary-section';
import { usePolicyDetail } from '@/hooks/use-policy-detail';
import { useSupplementaryData } from '@/hooks/use-supplementary-data';

type TabType = 'content' | 'pdf';

interface SourceTabContentProps {
  articleId: string;
}

export function SourceTabContent({ articleId }: SourceTabContentProps) {
  const { article, isLoading } = usePolicyDetail(articleId);
  const { surrenderValues, premiumExamples, priceIndices } = useSupplementaryData(article?.productId ?? null);
  const [activeTab, setActiveTab] = useState<TabType>('content');

  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!article) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-sm text-muted-foreground">약관 정보를 찾을 수 없습니다</p>
      </div>
    );
  }

  const isPdfTab = activeTab === 'pdf' && article.dataSourceId;

  return (
    <div className={isPdfTab ? 'flex-1 flex flex-col p-4 gap-4 min-h-0' : 'flex-1 overflow-y-auto p-4 space-y-4'}>
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
            contentType={article.contentType}
            content={article.content}
          />
          <SupplementarySection
            surrenderValues={surrenderValues ?? undefined}
            premiumExamples={premiumExamples ?? undefined}
            priceIndices={priceIndices ?? undefined}
          />
        </>
      ) : (
        article.dataSourceId && (
          <div className="flex-1 min-h-0">
            <PdfViewer dataSourceId={article.dataSourceId} mode="panel" />
          </div>
        )
      )}
    </div>
  );
}
