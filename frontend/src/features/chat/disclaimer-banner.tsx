import { Info } from 'lucide-react';

export function DisclaimerBanner() {
  return (
    <div className="flex items-center gap-2 px-4 py-2 bg-muted/50 text-xs text-muted-foreground">
      <Info className="size-3.5 shrink-0" />
      <p>AI 답변은 참고용이며, 정확한 내용은 약관 원문과 보험사 확인이 필요합니다.</p>
    </div>
  );
}
