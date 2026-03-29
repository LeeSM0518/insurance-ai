import { useState, useCallback } from 'react';
import { Loader2, Check, ExternalLink, Copy } from 'lucide-react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { getReportApi } from '@/lib/api';
import { trackEvent } from '@/lib/analytics';
import { toast } from 'sonner';

interface FollowupReportModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  chatSessionId: string;
}

type ModalPhase = 'form' | 'generating' | 'done';

export function FollowupReportModal({ open, onOpenChange, chatSessionId }: FollowupReportModalProps) {
  const [phase, setPhase] = useState<ModalPhase>('form');
  const [title, setTitle] = useState('');
  const [agentName, setAgentName] = useState('');
  const [agentContact, setAgentContact] = useState('');
  const [includeComparison, setIncludeComparison] = useState(true);
  const [includeSources, setIncludeSources] = useState(true);
  const [expiresInDays, setExpiresInDays] = useState(7);
  const [shareUrl, setShareUrl] = useState('');

  const resetForm = useCallback(() => {
    setPhase('form');
    setTitle('');
    setAgentName('');
    setAgentContact('');
    setIncludeComparison(true);
    setIncludeSources(true);
    setExpiresInDays(7);
    setShareUrl('');
  }, []);

  const handleOpenChange = useCallback((nextOpen: boolean) => {
    if (!nextOpen && phase === 'generating') return;
    onOpenChange(nextOpen);
    if (!nextOpen) {
      setTimeout(resetForm, 300);
    }
  }, [onOpenChange, phase, resetForm]);

  const handleGenerate = useCallback(async () => {
    setPhase('generating');
    try {
      const api = getReportApi();

      // 1. 팔로업 리포트 생성 (LLM 요약 생성으로 최대 2분 소요)
      const reportRes = await api.generateFollowupReport({
        followupReportRequest: {
          chatSessionId,
          title: title || undefined,
          includeComparison,
          includeSources,
          agentName: agentName || undefined,
          agentContact: agentContact || undefined,
        },
      }, { timeout: 120_000 });
      const reportId = reportRes.data.data?.id;
      if (!reportId) throw new Error('리포트 생성 실패');
      trackEvent('share', 'create_link', 'FOLLOWUP');

      // 2. 공유 링크 생성
      const shareRes = await api.createShareLink({
        reportId,
        shareLinkRequest: { expiresInDays },
      });
      const token = shareRes.data.data?.shareUrl;
      if (token) {
        const fullUrl = `${window.location.origin}${token}`;
        setShareUrl(fullUrl);
        try {
          await navigator.clipboard.writeText(fullUrl);
          toast.success('공유 링크가 복사되었습니다');
        } catch {
          // 클립보드 복사 실패 (탭 포커스 해제 등) — 리포트 생성은 성공
          toast.info('리포트가 생성되었습니다. 아래 링크를 복사해 주세요.');
        }
      }
      setPhase('done');
    } catch {
      toast.error('리포트 생성에 실패했습니다');
      setPhase('form');
    }
  }, [chatSessionId, title, includeComparison, includeSources, agentName, agentContact, expiresInDays]);

  const handleCopyLink = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(shareUrl);
      toast.success('공유 링크가 복사되었습니다');
    } catch {
      toast.error('복사에 실패했습니다');
    }
  }, [shareUrl]);

  const handlePreview = useCallback(() => {
    if (shareUrl) {
      window.open(shareUrl.replace(window.location.origin, ''), '_blank');
    }
  }, [shareUrl]);

  return (
    <Sheet open={open} onOpenChange={handleOpenChange}>
      <SheetContent side="right" className="w-full sm:w-[400px] overflow-y-auto">
        <SheetHeader>
          <SheetTitle>팔로업 리포트 생성</SheetTitle>
        </SheetHeader>

        <div className="flex flex-col gap-4 p-4 pt-2">
          {phase === 'form' && (
            <>
              <div className="space-y-1.5">
                <label className="text-sm font-medium">리포트 제목 (선택)</label>
                <Input
                  placeholder="예: 암보험 비교 분석 리포트"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium">설계사 이름 (선택)</label>
                <Input
                  placeholder="리포트에 표시할 이름"
                  value={agentName}
                  onChange={(e) => setAgentName(e.target.value)}
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium">연락처 (선택)</label>
                <Input
                  placeholder="010-0000-0000"
                  value={agentContact}
                  onChange={(e) => setAgentContact(e.target.value)}
                  maxLength={20}
                />
              </div>

              <div className="space-y-2">
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={includeComparison}
                    onChange={(e) => setIncludeComparison(e.target.checked)}
                    className="rounded"
                  />
                  비교표 포함
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={includeSources}
                    onChange={(e) => setIncludeSources(e.target.checked)}
                    className="rounded"
                  />
                  약관 출처 포함
                </label>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium">공유 기간</label>
                <select
                  value={expiresInDays}
                  onChange={(e) => setExpiresInDays(Number(e.target.value))}
                  className="w-full h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm"
                >
                  <option value={1}>1일</option>
                  <option value={3}>3일</option>
                  <option value={7}>7일</option>
                </select>
              </div>

              <Button onClick={handleGenerate} className="w-full mt-2">
                리포트 생성 및 공유 링크 복사
              </Button>
            </>
          )}

          {phase === 'generating' && (
            <div className="flex flex-col items-center gap-3 py-12">
              <Loader2 className="size-8 animate-spin text-primary" />
              <p className="text-sm text-muted-foreground">AI가 상담 내용을 요약하고 있습니다...</p>
              <p className="text-xs text-muted-foreground">잠시만 기다려 주세요 (약 5~15초)</p>
            </div>
          )}

          {phase === 'done' && (
            <div className="flex flex-col items-center gap-4 py-8">
              <div className="size-12 rounded-full bg-green-100 flex items-center justify-center">
                <Check className="size-6 text-green-600" />
              </div>
              <p className="text-sm font-medium">공유 링크가 복사되었습니다</p>
              <p className="text-xs text-muted-foreground text-center break-all px-4">
                {shareUrl}
              </p>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={handlePreview}>
                  <ExternalLink className="size-4" />
                  미리보기
                </Button>
                <Button variant="outline" size="sm" onClick={handleCopyLink}>
                  <Copy className="size-4" />
                  다시 복사
                </Button>
              </div>
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
