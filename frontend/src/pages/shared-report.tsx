import { useReducer, useEffect } from 'react';
import { useParams } from 'react-router';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { FileText, Loader2, Printer } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { axiosInstance } from '@/lib/api';
import { trackEvent } from '@/lib/analytics';

const remarkPlugins = [[remarkGfm, { singleTilde: false }]] as Parameters<typeof Markdown>[0]['remarkPlugins'];

interface SharedReport {
  reportType: string;
  title: string | null;
  content: string;
  createdAt: string;
  expiresAt: string | null;
}

type State =
  | { status: 'loading' }
  | { status: 'success'; report: SharedReport; daysLeft: number | null }
  | { status: 'expired' }
  | { status: 'not-found' };

type Action =
  | { type: 'success'; report: SharedReport; daysLeft: number | null }
  | { type: 'expired' }
  | { type: 'not-found' };

function reducer(_state: State, action: Action): State {
  switch (action.type) {
    case 'success':
      return { status: 'success', report: action.report, daysLeft: action.daysLeft };
    case 'expired':
      return { status: 'expired' };
    case 'not-found':
      return { status: 'not-found' };
  }
}

function calcDaysLeft(expiresAt: string | null): number | null {
  if (!expiresAt) return null;
  return Math.max(0, Math.ceil((new Date(expiresAt).getTime() - Date.now()) / (1000 * 60 * 60 * 24)));
}

export default function SharedReportPage() {
  const { shareToken } = useParams<{ shareToken: string }>();
  const [state, dispatch] = useReducer(reducer, { status: 'loading' });

  useEffect(() => {
    if (!shareToken) {
      dispatch({ type: 'not-found' });
      return;
    }

    const fetchReport = async () => {
      try {
        const res = await axiosInstance.get(`/api/v1/shared/${shareToken}`, {
          headers: { Accept: 'application/json' },
        });
        const data = res.data.data as SharedReport;
        trackEvent('share', 'view_report', data.reportType);
        dispatch({ type: 'success', report: data, daysLeft: calcDaysLeft(data.expiresAt) });
      } catch (err: unknown) {
        const status = (err as { response?: { status?: number } })?.response?.status;
        const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code;
        if (code === 'REPORT:SHARE_LINK_EXPIRED' || status === 410) {
          dispatch({ type: 'expired' });
        } else {
          dispatch({ type: 'not-found' });
        }
      }
    };
    fetchReport();
  }, [shareToken]);

  if (state.status === 'loading') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <Loader2 className="size-8 animate-spin text-primary" />
      </div>
    );
  }

  if (state.status === 'expired') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
        <div className="text-center max-w-sm">
          <FileText className="size-12 mx-auto mb-4 text-muted-foreground" />
          <h2 className="text-lg font-semibold mb-2">이 리포트의 공유 기간이 만료되었습니다.</h2>
          <p className="text-sm text-muted-foreground">담당 설계사에게 문의해 주세요.</p>
        </div>
      </div>
    );
  }

  if (state.status === 'not-found') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
        <div className="text-center max-w-sm">
          <FileText className="size-12 mx-auto mb-4 text-muted-foreground" />
          <h2 className="text-lg font-semibold mb-2">리포트를 찾을 수 없습니다.</h2>
          <p className="text-sm text-muted-foreground">링크가 올바른지 확인해 주세요.</p>
        </div>
      </div>
    );
  }

  const { report, daysLeft } = state;

  return (
    <div className="min-h-screen bg-gray-50 print:bg-white">
      {/* 헤더 */}
      <header className="bg-white border-b print:border-none">
        <div className="max-w-full mx-auto px-6 py-4 flex items-center gap-2">
          <FileText className="size-5 text-primary print-hide" />
          <h1 className="font-semibold text-lg">
            {report.title || '보장 분석 리포트'}
          </h1>
          <div className="flex-1" />
          <Button variant="outline" size="sm" className="print-hide" onClick={() => window.print()}>
            <Printer className="size-4 mr-1" />
            PDF/인쇄
          </Button>
        </div>
      </header>

      {/* 본문 */}
      <main className="max-w-full mx-auto px-6 py-6">
        <article className="bg-white rounded-lg border p-6 print:border-none print:p-0">
          <div className="prose prose-sm max-w-none
            prose-headings:text-foreground prose-p:text-foreground/90
            prose-table:text-sm prose-th:bg-muted/50 prose-th:p-2 prose-td:p-2
            prose-th:border prose-td:border prose-th:text-left
            [&_table]:w-full [&_table]:border-collapse
            prose-blockquote:border-l-primary/30 prose-blockquote:text-muted-foreground
          ">
            <Markdown remarkPlugins={remarkPlugins}>
              {report.content}
            </Markdown>
          </div>
        </article>
      </main>

      {/* 푸터 */}
      <footer className="border-t bg-white print-hide">
        <div className="max-w-full mx-auto px-6 py-3 flex items-center justify-between text-xs text-muted-foreground">
          <span>© Insurance AI</span>
          {daysLeft !== null && (
            <span>이 리포트는 {daysLeft}일 후 만료됩니다</span>
          )}
        </div>
      </footer>
    </div>
  );
}
