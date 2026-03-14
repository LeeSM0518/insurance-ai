import type { Source } from '@/types/chat';
import { SourceTabBar } from './source-tab-bar';
import { SourceTabContent } from './source-tab-content';

// 열린 탭 정보 - Source에서 필요한 필드만 추출 + sourceIndex 추가
export type SourceTab = Pick<Source, 'articleId' | 'articleNumber' | 'articleTitle' | 'insuranceCompany'> & {
  sourceIndex?: number;
  defaultTab?: 'content' | 'pdf';
};

interface SourceTabPanelProps {
  tabs: SourceTab[];
  activeTabId: string | null;
  onSelectTab: (articleId: string) => void;
  onCloseTab: (articleId: string) => void;
}

export function SourceTabPanel({ tabs, activeTabId, onSelectTab, onCloseTab }: SourceTabPanelProps) {
  if (tabs.length === 0) {
    return (
      <div className="h-full flex items-center justify-center bg-muted/30">
        <p className="text-sm text-muted-foreground text-center px-6">
          출처를 클릭하면
          <br />
          약관 상세가 표시됩니다
        </p>
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col">
      <SourceTabBar
        tabs={tabs}
        activeTabId={activeTabId}
        onSelectTab={onSelectTab}
        onCloseTab={onCloseTab}
      />
      {activeTabId && (() => {
        const activeDefaultTab = tabs.find((t) => t.articleId === activeTabId)?.defaultTab;
        return (
          <SourceTabContent
            key={`${activeTabId}-${activeDefaultTab ?? 'content'}`}
            articleId={activeTabId}
            defaultTab={activeDefaultTab}
          />
        );
      })()}
    </div>
  );
}
