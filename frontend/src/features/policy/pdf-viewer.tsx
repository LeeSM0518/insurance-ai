import { useState, useEffect } from 'react';
import { Download, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useMediaQuery } from '@/hooks/use-media-query';
import { axiosInstance } from '@/lib/api';

interface PdfViewerProps {
  dataSourceId: string;
  mode?: 'standalone' | 'panel';
}

export function PdfViewer({ dataSourceId, mode = 'standalone' }: PdfViewerProps) {
  const isDesktop = useMediaQuery('(min-width: 1025px)');
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(false);

  // Bearer 토큰 포함하여 PDF를 fetch → blob URL 생성
  useEffect(() => {
    let cancelled = false;

    axiosInstance
      .get(`/api/v1/policies/pdf/${dataSourceId}`, { responseType: 'blob' })
      .then((res) => {
        if (cancelled) return;
        const url = URL.createObjectURL(res.data as Blob);
        setBlobUrl(url);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
      // 이전 blob URL 해제
      setBlobUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return null;
      });
      setIsLoading(true);
      setError(false);
    };
  }, [dataSourceId]);

  // 모바일에서는 다운로드 버튼으로 대체
  if (!isDesktop) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-lg border bg-muted/30 p-6">
        <p className="text-sm text-muted-foreground">모바일에서는 PDF를 직접 열어 확인할 수 있습니다</p>
        {blobUrl ? (
          <Button asChild variant="outline" className="gap-2">
            <a href={blobUrl} target="_blank" rel="noopener noreferrer">
              <Download className="size-4" />
              원본 PDF 열기
            </a>
          </Button>
        ) : isLoading ? (
          <Loader2 className="size-5 animate-spin text-muted-foreground" />
        ) : (
          <p className="text-sm text-muted-foreground">PDF를 불러올 수 없습니다</p>
        )}
      </div>
    );
  }

  const heightClass = mode === 'panel' ? 'h-full' : 'h-[600px]';

  if (isLoading) {
    return (
      <div className={`w-full ${heightClass} border rounded-lg flex items-center justify-center`}>
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error || !blobUrl) {
    return (
      <div className={`w-full ${heightClass} border rounded-lg flex items-center justify-center`}>
        <p className="text-sm text-muted-foreground">PDF를 불러올 수 없습니다</p>
      </div>
    );
  }

  return (
    <iframe
      src={blobUrl}
      className={`w-full ${heightClass} border rounded-lg`}
      title="원본 PDF"
    />
  );
}
