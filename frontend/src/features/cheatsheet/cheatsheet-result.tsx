import { useMemo } from 'react';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Copy, RefreshCw, AlertCircle, Loader2, Printer } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { SourceCards } from '@/features/chat/source-cards';
import { trackEvent } from '@/lib/analytics';
import type { Source } from '@/types/chat';
import type { CheatsheetPhase } from '@/hooks/use-cheatsheet-stream';

const remarkPlugins = [[remarkGfm, { singleTilde: false }]] as Parameters<typeof Markdown>[0]['remarkPlugins'];

const markdownComponents = {
  table: ({ children }: { children?: React.ReactNode }) => (
    <div className="overflow-x-auto my-2">
      <table>{children}</table>
    </div>
  ),
};

interface CheatsheetResultProps {
  phase: CheatsheetPhase;
  content: string;
  sources: Source[];
  errorMessage: string | null;
  onRetry: () => void;
  onReset: () => void;
}

export function CheatsheetResult({
  phase,
  content,
  sources,
  errorMessage,
  onRetry,
  onReset,
}: CheatsheetResultProps) {
  const isStreamingPhase = phase === 'searching' || phase === 'sources_found' || phase === 'generating' || phase === 'streaming';

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      trackEvent('cheatsheet', 'copy');
    } catch {
      // 클립보드 API 실패 시 무시
    }
  };

  const fixedContent = useMemo(() => content, [content]);

  // 에러 상태
  if (phase === 'error') {
    return (
      <div className="flex flex-col items-center gap-4 py-8">
        <AlertCircle className="size-10 text-destructive" />
        <p className="text-sm text-destructive">{errorMessage || '치트시트 생성에 실패했습니다'}</p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={onRetry}>
            다시 시도
          </Button>
          <Button variant="ghost" onClick={onReset}>
            돌아가기
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* 스트리밍 인디케이터 */}
      {(phase === 'searching' || phase === 'generating') && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground py-2">
          <Loader2 className="size-4 animate-spin" />
          <span>{phase === 'searching' ? '약관 데이터를 검색 중...' : '치트시트 생성 중...'}</span>
        </div>
      )}

      {/* 출처 카드 */}
      {sources.length > 0 && <SourceCards sources={sources} />}

      {/* 마크다운 컨텐츠 */}
      {content && (
        <div className="prose max-w-none leading-relaxed [&_h2]:text-lg [&_h2]:font-semibold [&_h3]:text-base [&_h3]:font-semibold [&_p]:text-base [&_li]:text-base">
          <Markdown remarkPlugins={remarkPlugins} components={markdownComponents}>
            {fixedContent}
          </Markdown>
          {/* 스트리밍 중 깜빡이는 커서 */}
          {isStreamingPhase && (
            <span className="inline-block w-[2px] h-[1em] bg-foreground animate-blink align-middle ml-0.5" />
          )}
        </div>
      )}

      {/* 완료 액션 버튼 */}
      {phase === 'done' && content && (
        <div className="flex gap-2 pt-2 print-hide">
          <Button variant="outline" size="sm" onClick={handleCopy}>
            <Copy className="size-4 mr-1.5" />
            복사
          </Button>
          <Button variant="outline" size="sm" className="print-hide" onClick={() => { trackEvent('cheatsheet', 'print'); window.print(); }}>
            <Printer className="size-4 mr-1.5" />
            PDF/인쇄
          </Button>
          <Button variant="outline" size="sm" onClick={onReset}>
            <RefreshCw className="size-4 mr-1.5" />
            다시 생성
          </Button>
        </div>
      )}
    </div>
  );
}
