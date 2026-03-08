import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ArrowLeft, FileText, MessageSquare } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { PolicyMeta } from '@/features/policy/policy-meta';
import { PolicyContent } from '@/features/policy/policy-content';
import { PdfViewer } from '@/features/policy/pdf-viewer';
import { SupplementarySection } from '@/features/policy/supplementary-section';
import { RelatedArticles } from '@/features/policy/related-articles';
import { CopyShareButtons } from '@/features/chat/copy-share-buttons';
import { useMediaQuery } from '@/hooks/use-media-query';
import {
  MOCK_POLICY_ARTICLE,
  MOCK_SURRENDER_VALUES,
  MOCK_PREMIUM_EXAMPLES,
  MOCK_PRICE_INDICES,
} from '@/mocks/policy-data';

type TabType = 'content' | 'pdf';

export default function PolicyDetailPage() {
  const navigate = useNavigate();
  const isDesktop = useMediaQuery('(min-width: 1025px)');
  const article = MOCK_POLICY_ARTICLE;
  const [activeTab, setActiveTab] = useState<TabType>('content');

  return (
    <div className="flex-1 flex flex-col">
      {/* 헤더 */}
      <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background px-2 py-2">
        <Button variant="ghost" size="icon-sm" onClick={() => navigate(-1)} aria-label="뒤로가기">
          <ArrowLeft className="size-4" />
        </Button>
        <h1 className="text-sm font-semibold">약관 상세</h1>
      </header>

      <div className={`flex-1 ${isDesktop ? 'flex gap-6 px-6 py-4' : 'px-4 py-4 space-y-6'}`}>
        {/* 좌측 (또는 모바일 전체) */}
        <div className={`space-y-6 ${isDesktop ? 'flex-1 min-w-0' : ''}`}>
          <PolicyMeta article={article} />

          {/* 탭 전환: 조문 내용 / 원본 PDF */}
          {article.hasPdfSource && article.dataSourceId && (
            <div className="flex gap-1 border-b" role="tablist">
              <button
                role="tab"
                aria-selected={activeTab === 'content'}
                className={`flex items-center gap-1.5 px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
                  activeTab === 'content'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-muted-foreground hover:text-foreground'
                }`}
                onClick={() => setActiveTab('content')}
              >
                <FileText className="size-3.5" />
                조문 내용
              </button>
              <button
                role="tab"
                aria-selected={activeTab === 'pdf'}
                className={`flex items-center gap-1.5 px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
                  activeTab === 'pdf'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-muted-foreground hover:text-foreground'
                }`}
                onClick={() => setActiveTab('pdf')}
              >
                <FileText className="size-3.5" />
                원본 PDF 보기
              </button>
            </div>
          )}

          {/* 탭 콘텐츠 */}
          {activeTab === 'content' ? (
            <>
              <PolicyContent
                articleNumber={article.articleNumber}
                articleTitle={article.articleTitle}
                content={article.content}
              />
              <RelatedArticles articles={article.relatedArticles} />
            </>
          ) : (
            article.dataSourceId && <PdfViewer dataSourceId={article.dataSourceId} />
          )}

          {/* 하단 액션 */}
          <div className="flex items-center gap-2">
            <Button
              className="flex-1 gap-2"
              onClick={() => navigate(`/chat?q=${encodeURIComponent(`${article.articleNumber} ${article.articleTitle}`)}`)}
            >
              <MessageSquare className="size-4" />
              AI에게 질문하기
            </Button>
            <CopyShareButtons content={article.content} />
          </div>
        </div>

        {/* 우측 사이드 (데스크톱) */}
        {isDesktop && (
          <aside className="w-[360px] shrink-0">
            <SupplementarySection
              surrenderValues={MOCK_SURRENDER_VALUES}
              premiumExamples={MOCK_PREMIUM_EXAMPLES}
              priceIndices={MOCK_PRICE_INDICES}
            />
          </aside>
        )}

        {/* 모바일에서 보충 정보 */}
        {!isDesktop && (
          <SupplementarySection
            surrenderValues={MOCK_SURRENDER_VALUES}
            premiumExamples={MOCK_PREMIUM_EXAMPLES}
            priceIndices={MOCK_PRICE_INDICES}
          />
        )}
      </div>
    </div>
  );
}
