import { useCallback, useEffect, useRef, useState } from 'react';
import { CheatsheetForm } from '@/features/cheatsheet/cheatsheet-form';
import { CheatsheetResult } from '@/features/cheatsheet/cheatsheet-result';
import { CheatsheetList } from '@/features/cheatsheet/cheatsheet-list';
import { useCheatsheetStream } from '@/hooks/use-cheatsheet-stream';
import type { CheatsheetRequest } from '@/types/report';
import type { ReportResponse } from '@/types/report';

type PagePhase = 'form' | 'streaming' | 'viewing';

export default function CheatsheetPage() {
  const [pagePhase, setPagePhase] = useState<PagePhase>('form');
  const [viewingContent, setViewingContent] = useState<string>('');
  const [lastRequest, setLastRequest] = useState<CheatsheetRequest | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const stream = useCheatsheetStream();

  const handleGenerate = useCallback(
    (request: CheatsheetRequest) => {
      setLastRequest(request);
      setPagePhase('streaming');
      stream.generate(request);
    },
    [stream],
  );

  const handleRetry = useCallback(() => {
    if (lastRequest) {
      stream.generate(lastRequest);
    }
  }, [lastRequest, stream]);

  const handleReset = useCallback(() => {
    stream.reset();
    setPagePhase('form');
    setViewingContent('');
    setRefreshKey((k) => k + 1);
  }, [stream]);

  const handleSelectSaved = useCallback(
    (report: ReportResponse) => {
      stream.reset();
      setViewingContent(report.content);
      setPagePhase('viewing');
    },
    [stream],
  );

  // 스트리밍 완료 시 목록 갱신
  const prevPhaseRef = useRef(stream.phase);
  useEffect(() => {
    if (prevPhaseRef.current !== 'done' && stream.phase === 'done' && pagePhase === 'streaming') {
      setRefreshKey((k) => k + 1);
    }
    prevPhaseRef.current = stream.phase;
  }, [stream.phase, pagePhase]);

  return (
    <div className="flex-1 px-4 py-6 max-w-7xl mx-auto w-full">
      <h1 className="text-2xl desktop:text-3xl font-bold text-foreground mb-6">
        상담 준비 치트시트
      </h1>

      {pagePhase === 'form' && (
        <>
          <CheatsheetForm onSubmit={handleGenerate} isLoading={stream.isStreaming} />
          <div className="mt-8">
            <h2 className="text-lg font-semibold text-foreground mb-4">내 치트시트</h2>
            <CheatsheetList onSelect={handleSelectSaved} refreshKey={refreshKey} />
          </div>
        </>
      )}

      {pagePhase === 'streaming' && (
        <CheatsheetResult
          phase={stream.phase}
          content={stream.streamingContent}
          sources={stream.sources}
          errorMessage={stream.errorMessage}
          onRetry={handleRetry}
          onReset={handleReset}
        />
      )}

      {pagePhase === 'viewing' && (
        <CheatsheetResult
          phase="done"
          content={viewingContent}
          sources={[]}
          errorMessage={null}
          onRetry={() => {}}
          onReset={handleReset}
        />
      )}
    </div>
  );
}
