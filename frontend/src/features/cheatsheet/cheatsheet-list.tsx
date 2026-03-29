import { useCallback, useEffect, useState } from 'react';
import { Loader2, Trash2, FileText, Printer } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ShareButton } from '@/features/report/share-button';
import { axiosInstance } from '@/lib/api';
import type { ReportSummaryResponse, ReportResponse } from '@/types/report';

interface CheatsheetListProps {
  onSelect: (report: ReportResponse) => void;
  refreshKey?: number;
}

export function CheatsheetList({ onSelect, refreshKey }: CheatsheetListProps) {
  const [items, setItems] = useState<ReportSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const fetchList = useCallback(async () => {
    try {
      setLoading(true);
      const res = await axiosInstance.get<{ data: ReportSummaryResponse[] }>('/api/v1/reports/cheatsheet');
      setItems(res.data.data ?? []);
    } catch {
      // 에러 무시 (빈 리스트 표시)
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchList();
  }, [fetchList, refreshKey]);

  const handleSelect = async (id: string) => {
    try {
      const res = await axiosInstance.get<{ data: ReportResponse }>(`/api/v1/reports/cheatsheet/${id}`);
      if (res.data.data) {
        onSelect(res.data.data);
      }
    } catch {
      // 에러 무시
    }
  };

  const handleDelete = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    if (!confirm('이 치트시트를 삭제하시겠습니까?')) return;
    try {
      setDeletingId(id);
      await axiosInstance.delete(`/api/v1/reports/cheatsheet/${id}`);
      setItems((prev) => prev.filter((item) => item.id !== id));
    } catch {
      // 에러 무시
    } finally {
      setDeletingId(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <p className="text-sm text-muted-foreground text-center py-8">
        아직 생성된 치트시트가 없습니다
      </p>
    );
  }

  return (
    <div className="space-y-2">
      {/* 전체 인쇄 버튼 */}
      <div className="flex justify-end mb-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => window.open('/cheatsheet/print', '_blank')}
        >
          <Printer className="size-4 mr-1.5" />
          전체 인쇄
        </Button>
      </div>
      {items.map((item) => (
        <button
          key={item.id}
          onClick={() => handleSelect(item.id)}
          className="w-full flex items-center gap-3 p-3 rounded-lg border border-border hover:bg-accent/50 transition-colors text-left"
        >
          <FileText className="size-5 text-muted-foreground shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-foreground truncate">
              {item.title || '제목 없음'}
            </p>
            <p className="text-xs text-muted-foreground">
              {new Date(item.createdAt).toLocaleDateString('ko-KR', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })}
            </p>
          </div>
          <div className="flex items-center gap-1 shrink-0" onClick={(e) => e.stopPropagation()}>
            <ShareButton reportId={item.id} variant="ghost" size="icon" />
            <Button
              variant="ghost"
              size="sm"
              onClick={(e) => handleDelete(e, item.id)}
              disabled={deletingId === item.id}
              className="text-muted-foreground hover:text-destructive"
            >
              {deletingId === item.id ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Trash2 className="size-4" />
              )}
            </Button>
          </div>
        </button>
      ))}
    </div>
  );
}
