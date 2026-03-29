import { useCallback, useEffect, useRef, useState } from 'react';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Loader2, Printer } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { axiosInstance } from '@/lib/api';
import type { ReportSummaryResponse, ReportResponse } from '@/types/report';

const remarkPlugins = [[remarkGfm, { singleTilde: false }]] as Parameters<typeof Markdown>[0]['remarkPlugins'];

const markdownComponents = {
  table: ({ children }: { children?: React.ReactNode }) => (
    <div className="overflow-x-auto my-2 print:overflow-visible">
      <table>{children}</table>
    </div>
  ),
};

type LoadState = 'loading' | 'ready' | 'error';

export default function CheatsheetPrintPage() {
  const [reports, setReports] = useState<ReportResponse[]>([]);
  const [loadState, setLoadState] = useState<LoadState>('loading');
  const hasPrinted = useRef(false);

  const fetchAllCheatsheets = useCallback(async () => {
    try {
      // 1. 목록 조회
      const listRes = await axiosInstance.get<{ data: ReportSummaryResponse[] }>('/api/v1/reports/cheatsheet');
      const items = listRes.data.data ?? [];

      if (items.length === 0) {
        setLoadState('ready');
        return;
      }

      // 2. 각 치트시트 상세 조회 (병렬)
      const details = await Promise.all(
        items.map((item) =>
          axiosInstance
            .get<{ data: ReportResponse }>(`/api/v1/reports/cheatsheet/${item.id}`)
            .then((res) => res.data.data)
            .catch(() => null),
        ),
      );

      setReports(details.filter((d): d is ReportResponse => d !== null));
      setLoadState('ready');
    } catch {
      setLoadState('error');
    }
  }, []);

  useEffect(() => {
    fetchAllCheatsheets(); // eslint-disable-line react-hooks/set-state-in-effect
  }, [fetchAllCheatsheets]);

  // 렌더링 완료 후 자동 인쇄
  useEffect(() => {
    if (loadState === 'ready' && reports.length > 0 && !hasPrinted.current) {
      hasPrinted.current = true;
      // 마크다운 렌더링이 DOM에 반영될 시간 확보
      requestAnimationFrame(() => {
        setTimeout(() => window.print(), 300);
      });
    }
  }, [loadState, reports.length]);

  if (loadState === 'loading') {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen gap-3">
        <Loader2 className="size-8 animate-spin text-primary" />
        <p className="text-sm text-muted-foreground">치트시트를 불러오는 중...</p>
      </div>
    );
  }

  if (loadState === 'error') {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen gap-3">
        <p className="text-sm text-destructive">치트시트를 불러오지 못했습니다</p>
        <Button variant="outline" size="sm" onClick={() => { setLoadState('loading'); fetchAllCheatsheets(); }}>
          다시 시도
        </Button>
      </div>
    );
  }

  if (reports.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen gap-3">
        <p className="text-sm text-muted-foreground">인쇄할 치트시트가 없습니다</p>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-6 py-8 print:px-0 print:py-0 print:max-w-none">
      {/* 인쇄 버튼 (화면에서만 보임) */}
      <div className="flex items-center justify-between mb-6 print-hide">
        <h1 className="text-xl font-bold text-foreground">
          전체 치트시트 인쇄 ({reports.length}건)
        </h1>
        <Button variant="outline" size="sm" onClick={() => window.print()}>
          <Printer className="size-4 mr-1.5" />
          인쇄하기
        </Button>
      </div>

      {/* 치트시트 목록 렌더링 */}
      {reports.map((report, index) => (
        <article
          key={report.id}
          className={index > 0 ? 'page-break' : ''}
        >
          {/* 치트시트 제목 */}
          <div className="mb-4 pb-2 border-b border-border print:border-gray-300">
            <h2 className="text-lg font-semibold text-foreground">
              {report.title || '제목 없음'}
            </h2>
            <p className="text-xs text-muted-foreground mt-1">
              {new Date(report.createdAt).toLocaleDateString('ko-KR', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })}
            </p>
          </div>

          {/* 마크다운 본문 */}
          <div className="prose max-w-none leading-relaxed [&_h2]:text-lg [&_h2]:font-semibold [&_h3]:text-base [&_h3]:font-semibold [&_p]:text-base [&_li]:text-base">
            <Markdown remarkPlugins={remarkPlugins} components={markdownComponents}>
              {report.content}
            </Markdown>
          </div>
        </article>
      ))}

      {/* 면책 문구 (인쇄에서만 보임) */}
      <div className="hidden print:block disclaimer">
        본 치트시트는 AI가 약관 원문을 기반으로 생성한 참고 자료이며, 법적 효력이 없습니다.
        정확한 보장 내용은 반드시 해당 보험사의 약관 원문을 확인하시기 바랍니다.
      </div>
    </div>
  );
}
