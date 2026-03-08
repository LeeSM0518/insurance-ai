import { Download } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useMediaQuery } from '@/hooks/use-media-query';

interface PdfViewerProps {
  dataSourceId: string;
}

export function PdfViewer({ dataSourceId }: PdfViewerProps) {
  const isDesktop = useMediaQuery('(min-width: 1025px)');
  const pdfUrl = `/api/v1/policies/pdf/${dataSourceId}`;

  // 모바일에서는 다운로드 버튼으로 대체
  if (!isDesktop) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-lg border bg-muted/30 p-6">
        <p className="text-sm text-muted-foreground">모바일에서는 PDF를 직접 열어 확인할 수 있습니다</p>
        <Button asChild variant="outline" className="gap-2">
          <a href={pdfUrl} target="_blank" rel="noopener noreferrer">
            <Download className="size-4" />
            원본 PDF 열기
          </a>
        </Button>
      </div>
    );
  }

  return (
    <iframe
      src={pdfUrl}
      className="w-full h-[600px] border rounded-lg"
      title="원본 PDF"
    />
  );
}
