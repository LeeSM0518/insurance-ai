import { useMemo } from 'react';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Copy, Loader2, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { SummaryPhase } from '@/hooks/use-comparison';

const remarkPlugins = [[remarkGfm, { singleTilde: false }]] as Parameters<typeof Markdown>[0]['remarkPlugins'];

const markdownComponents = {
  table: ({ children }: { children?: React.ReactNode }) => (
    <div className="overflow-x-auto my-2">
      <table>{children}</table>
    </div>
  ),
};

interface ComparisonSummaryProps {
  phase: SummaryPhase;
  content: string;
  errorMessage: string | null;
  onGenerate: () => void;
  isStreaming: boolean;
}

export function ComparisonSummary({
  phase,
  content,
  errorMessage,
  onGenerate,
  isStreaming,
}: ComparisonSummaryProps) {
  const fixedContent = useMemo(() => content, [content]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content);
    } catch {
      // 클립보드 API 실패 시 무시
    }
  };

  // 아직 요약을 시작하지 않은 상태
  if (phase === 'idle') {
    return (
      <div className="border border-border rounded-lg p-4 mt-4">
        <Button onClick={onGenerate} variant="outline" className="w-full">
          <Sparkles className="size-4 mr-2" />
          AI 핵심 차이점 분석
        </Button>
      </div>
    );
  }

  // 에러 상태
  if (phase === 'error') {
    return (
      <div className="border border-destructive/30 rounded-lg p-4 mt-4">
        <p className="text-sm text-destructive mb-2">{errorMessage || '요약 생성에 실패했습니다'}</p>
        <Button variant="outline" size="sm" onClick={onGenerate}>
          다시 시도
        </Button>
      </div>
    );
  }

  return (
    <div className="border border-border rounded-lg p-4 mt-4">
      {/* 생성 중 인디케이터 */}
      {phase === 'generating' && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground py-2">
          <Loader2 className="size-4 animate-spin" />
          <span>AI 분석 중...</span>
        </div>
      )}

      {/* 마크다운 컨텐츠 */}
      {content && (
        <div className="prose max-w-none leading-relaxed [&_h3]:text-base [&_h3]:font-semibold [&_p]:text-sm [&_li]:text-sm">
          <Markdown remarkPlugins={remarkPlugins} components={markdownComponents}>
            {fixedContent}
          </Markdown>
          {/* 스트리밍 중 깜빡이는 커서 */}
          {isStreaming && (
            <span className="inline-block w-[2px] h-[1em] bg-foreground animate-blink align-middle ml-0.5" />
          )}
        </div>
      )}

      {/* 완료 액션 */}
      {phase === 'done' && content && (
        <div className="flex gap-2 pt-3 border-t border-border mt-3">
          <Button variant="outline" size="sm" onClick={handleCopy}>
            <Copy className="size-4 mr-1.5" />
            복사
          </Button>
        </div>
      )}
    </div>
  );
}
