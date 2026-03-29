import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { FileText, BarChart3, MessageSquare, Loader2, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ShareButton } from '@/features/report/share-button';
import { axiosInstance } from '@/lib/api';
import { trackEvent } from '@/lib/analytics';
import type { ReportSummaryResponse } from '@/types/report';

type ReportFilter = 'ALL' | 'CHEATSHEET' | 'COMPARISON' | 'FOLLOWUP';

const FILTER_TABS: { value: ReportFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'CHEATSHEET', label: '치트시트' },
  { value: 'COMPARISON', label: '비교분석' },
  { value: 'FOLLOWUP', label: '팔로업' },
];

const REPORT_TYPE_ICONS: Record<string, typeof FileText> = {
  CHEATSHEET: FileText,
  COMPARISON: BarChart3,
  FOLLOWUP: MessageSquare,
};

const REPORT_TYPE_LABELS: Record<string, string> = {
  CHEATSHEET: '치트시트',
  COMPARISON: '비교분석',
  FOLLOWUP: '팔로업',
};

function calcDaysLeft(expiresAt: string | null): number | null {
  if (!expiresAt) return null;
  return Math.max(0, Math.ceil((new Date(expiresAt).getTime() - Date.now()) / (1000 * 60 * 60 * 24)));
}

export default function MyReportsPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<ReportSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<ReportFilter>('ALL');
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const fetchReports = useCallback(async () => {
    try {
      setLoading(true);
      const res = await axiosInstance.get<{ data: ReportSummaryResponse[] }>('/api/v1/reports');
      setItems(res.data.data ?? []);
    } catch {
      // 에러 무시
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchReports();
  }, [fetchReports]);

  const handleDelete = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    if (!confirm('이 리포트를 삭제하시겠습니까?')) return;
    try {
      setDeletingId(id);
      await axiosInstance.delete(`/api/v1/reports/${id}`);
      setItems((prev) => prev.filter((item) => item.id !== id));
      trackEvent('report', 'delete');
    } catch {
      // 에러 무시
    } finally {
      setDeletingId(null);
    }
  };

  const handleOpen = (item: ReportSummaryResponse) => {
    trackEvent('report', 'open', item.reportType);
    if (item.reportType === 'CHEATSHEET') {
      navigate('/cheatsheet');
    } else {
      // 비교/팔로업은 공유 링크로 열기 (상세 조회 후)
      window.open(`/reports/${item.id}`, '_blank');
    }
  };

  const filteredItems = filter === 'ALL'
    ? items
    : items.filter((item) => item.reportType === filter);

  return (
    <div className="flex-1 px-4 py-6 max-w-3xl mx-auto w-full">
      <h1 className="text-2xl desktop:text-3xl font-bold text-foreground mb-6">내 리포트</h1>

      {/* 필터 탭 */}
      <div className="flex gap-2 mb-6">
        {FILTER_TABS.map((tab) => (
          <button
            key={tab.value}
            onClick={() => setFilter(tab.value)}
            className={`px-3 py-1.5 rounded-full text-sm border transition-colors ${
              filter === tab.value
                ? 'bg-primary text-primary-foreground border-primary'
                : 'bg-background text-foreground border-border hover:border-primary/50'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* 리스트 */}
      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="size-6 animate-spin text-muted-foreground" />
        </div>
      ) : filteredItems.length === 0 ? (
        <div className="text-center py-12">
          <FileText className="size-12 mx-auto mb-4 text-muted-foreground" />
          <p className="text-sm text-muted-foreground">
            {filter === 'ALL' ? '아직 생성된 리포트가 없습니다' : `${REPORT_TYPE_LABELS[filter]} 리포트가 없습니다`}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {filteredItems.map((item) => {
            const Icon = REPORT_TYPE_ICONS[item.reportType] ?? FileText;
            const shareExpiry = 'shareExpiresAt' in item ? calcDaysLeft((item as Record<string, unknown>).shareExpiresAt as string | null) : null;

            return (
              <div
                key={item.id}
                className="flex items-center gap-3 p-4 rounded-lg border border-border hover:bg-accent/50 transition-colors cursor-pointer"
                onClick={() => handleOpen(item)}
              >
                <Icon className="size-5 text-muted-foreground shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground truncate">
                    {item.title || '제목 없음'}
                  </p>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground mt-0.5">
                    <span>
                      {new Date(item.createdAt).toLocaleDateString('ko-KR', {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                      })}
                    </span>
                    <span>·</span>
                    <span>{REPORT_TYPE_LABELS[item.reportType] ?? item.reportType}</span>
                    {shareExpiry !== null && (
                      <>
                        <span>·</span>
                        <span className="text-warning">공유: {shareExpiry}일 남음</span>
                      </>
                    )}
                  </div>
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
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
