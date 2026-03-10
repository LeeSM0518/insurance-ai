import { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router';
import { ArrowLeft, FileText, Loader2, MessageSquare, SearchX, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { PolicyMeta } from '@/features/policy/policy-meta';
import { PolicyContent } from '@/features/policy/policy-content';
import { PdfViewer } from '@/features/policy/pdf-viewer';
import { SupplementarySection } from '@/features/policy/supplementary-section';
import { RelatedArticles } from '@/features/policy/related-articles';
import { CopyShareButtons } from '@/features/chat/copy-share-buttons';
import { CONTENT_TYPE_LABELS, type ContentType } from '@/types/search';
import { useMediaQuery } from '@/hooks/use-media-query';
import { useLocalStorage } from '@/hooks/use-local-storage';
import { useResizeHandle } from '@/hooks/use-resize-handle';
import { usePolicyDetail } from '@/hooks/use-policy-detail';
import { useSupplementaryData } from '@/hooks/use-supplementary-data';
import { trackEvent } from '@/lib/analytics';

const PDF_PANEL_MIN = 280;
const PDF_PANEL_DEFAULT = 360;

export default function PolicyDetailPage() {
  const { articleId } = useParams<{ articleId: string }>();
  const navigate = useNavigate();
  const isDesktop = useMediaQuery('(min-width: 1025px)');
  const { article, isLoading, error } = usePolicyDetail(articleId);
  const { surrenderValues, premiumExamples, priceIndices } = useSupplementaryData(article?.productId ?? null);
  const [pdfOpen, setPdfOpen] = useState(false);

  // 약관 조회 이벤트
  useEffect(() => {
    if (articleId) trackEvent('policy', 'view', articleId);
  }, [articleId]);
  const [pdfPanelWidth, setPdfPanelWidth] = useLocalStorage('policy-pdf-panel-width', PDF_PANEL_DEFAULT);

  const handlePdfPanelResize = useCallback(
    (width: number) => setPdfPanelWidth(width),
    [setPdfPanelWidth],
  );

  // 화면 너비의 50%를 최대값으로 사용 (창 리사이즈 반영)
  const [windowWidth, setWindowWidth] = useState(window.innerWidth);
  useEffect(() => {
    const handleResize = () => setWindowWidth(window.innerWidth);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);
  const pdfPanelMax = Math.max(PDF_PANEL_MIN, Math.floor(windowWidth * 0.5));

  const { handleMouseDown: handleResizeMouseDown } = useResizeHandle({
    minWidth: PDF_PANEL_MIN,
    maxWidth: pdfPanelMax,
    onResize: handlePdfPanelResize,
    direction: 'left',
  });

  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error || !article) {
    return (
      <div className="flex-1 flex flex-col">
        <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background px-2 py-2">
          <Button variant="ghost" size="icon-sm" onClick={() => navigate(-1)} aria-label="뒤로가기">
            <ArrowLeft className="size-4" />
          </Button>
          <h1 className="text-sm font-semibold">약관 상세</h1>
        </header>
        <div className="flex-1 flex items-center justify-center">
          <EmptyState icon={SearchX} title={error ?? '약관을 찾을 수 없습니다'} />
        </div>
      </div>
    );
  }

  const hasPdfSource = article.hasPdfSource && !!article.dataSourceId;

  return (
    <div className="flex-1 flex flex-col">
      {/* 헤더 */}
      <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background px-2 py-2">
        <Button variant="ghost" size="icon-sm" onClick={() => navigate(-1)} aria-label="뒤로가기">
          <ArrowLeft className="size-4" />
        </Button>
        <h1 className="text-sm font-semibold">약관 상세</h1>
      </header>

      <div className={`flex-1 min-h-0 ${isDesktop ? 'flex gap-6 px-6 py-4' : 'flex flex-col px-4 py-4'}`}>
        {/* 좌측 (또는 모바일 전체) */}
        <div className={`flex flex-col min-h-0 ${isDesktop ? 'flex-1 min-w-0 gap-4' : 'flex-1 gap-4'}`}>
          {/* 상단 고정 영역: 메타 + PDF 버튼 */}
          <div className="shrink-0 space-y-4">
            <PolicyMeta article={article} />

            {/* 원문 PDF 보기 버튼 (PDF 소스가 있을 때만) */}
            {hasPdfSource && (
              <Button
                variant={pdfOpen ? 'default' : 'outline'}
                size="sm"
                className="gap-2"
                onClick={() => { if (!pdfOpen) trackEvent('policy', 'open_pdf', articleId); setPdfOpen(!pdfOpen); }}
              >
                <FileText className="size-4" />
                {pdfOpen ? '원문 PDF 닫기' : '원문 PDF 보기'}
              </Button>
            )}
          </div>

          {/* 조문 내용 — 스크롤 가능한 영역 */}
          <div className="flex-1 min-h-0 overflow-y-auto rounded-md border bg-muted/30 p-4">
            <PolicyContent
              articleNumber={article.articleNumber}
              articleTitle={article.articleTitle}
              contentType={article.contentType}
              content={article.content}
            />
          </div>

          {/* 하단 고정 영역: 관련 조문 + 보충정보 + 액션 버튼 */}
          <div className="shrink-0 space-y-4">
            {/* 관련 조문 & 보충정보 — 데스크톱 2컬럼, 모바일 1컬럼 */}
            <div className={isDesktop ? 'grid grid-cols-2 gap-4' : 'space-y-4'}>
              <RelatedArticles articles={article.relatedArticles} />
              <SupplementarySection
                surrenderValues={surrenderValues ?? undefined}
                premiumExamples={premiumExamples ?? undefined}
                priceIndices={priceIndices ?? undefined}
              />
            </div>

            <div className="flex items-center gap-2">
              <Button
                className="flex-1 gap-2"
                onClick={() => {
                  trackEvent('policy', 'ask_ai', articleId);
                  const queryParts = [article.articleNumber, article.articleTitle].filter(Boolean);
                  const query = queryParts.length > 0
                    ? queryParts.join(' ')
                    : CONTENT_TYPE_LABELS[article.contentType as ContentType] || article.contentType;
                  navigate(`/chat?q=${encodeURIComponent(query)}`);
                }}
              >
                <MessageSquare className="size-4" />
                AI에게 질문하기
              </Button>
              <CopyShareButtons content={article.content} />
            </div>
          </div>
        </div>

        {/* 우측 PDF 사이드바 (데스크톱, PDF 열렸을 때만) */}
        {isDesktop && pdfOpen && article.dataSourceId && (
          <aside
            className="relative shrink-0 flex flex-col border-l"
            style={{ width: pdfPanelWidth }}
          >
            {/* 리사이즈 핸들 */}
            <div
              className="absolute left-0 top-0 bottom-0 w-1 cursor-col-resize hover:bg-primary/20 active:bg-primary/30 z-10"
              onMouseDown={(e) => handleResizeMouseDown(e, pdfPanelWidth)}
            />
            <div className="flex-1 flex flex-col">
              <div className="flex items-center justify-between p-3 border-b">
                <span className="text-sm font-medium">원본 PDF</span>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  onClick={() => setPdfOpen(false)}
                  aria-label="PDF 닫기"
                >
                  <X className="size-4" />
                </Button>
              </div>
              <div className="flex-1">
                <PdfViewer dataSourceId={article.dataSourceId} mode="panel" />
              </div>
            </div>
          </aside>
        )}

        {/* 모바일 PDF 바텀시트 */}
        {!isDesktop && (
          <Sheet open={pdfOpen} onOpenChange={setPdfOpen}>
            <SheetContent side="bottom" className="h-[70vh]">
              <SheetHeader>
                <SheetTitle>원본 PDF</SheetTitle>
              </SheetHeader>
              {article.dataSourceId && (
                <PdfViewer dataSourceId={article.dataSourceId} />
              )}
            </SheetContent>
          </Sheet>
        )}
      </div>
    </div>
  );
}
