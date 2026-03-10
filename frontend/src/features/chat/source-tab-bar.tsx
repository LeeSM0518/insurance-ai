import { X } from 'lucide-react';
import type { SourceTab } from './source-tab-panel';

interface SourceTabBarProps {
  tabs: SourceTab[];
  activeTabId: string | null;
  onSelectTab: (articleId: string) => void;
  onCloseTab: (articleId: string) => void;
}

export function SourceTabBar({ tabs, activeTabId, onSelectTab, onCloseTab }: SourceTabBarProps) {
  return (
    <div
      className="flex border-b overflow-x-auto scrollbar-hide"
      role="tablist"
      aria-label="약관 상세 탭"
    >
      {tabs.map((tab) => {
        const isActive = tab.articleId === activeTabId;
        return (
          <button
            key={tab.articleId}
            role="tab"
            aria-selected={isActive}
            className={`group flex items-center gap-1 shrink-0 px-3 py-2 text-sm border-r transition-colors ${
              isActive
                ? 'bg-background border-b-transparent font-medium'
                : 'bg-muted/50 hover:bg-muted text-muted-foreground'
            }`}
            onClick={() => onSelectTab(tab.articleId)}
          >
            <span className="truncate max-w-[120px]">
              {tab.insuranceCompany}&middot;{tab.articleNumber}
            </span>
            <span
              role="button"
              aria-label={`${tab.articleNumber} 탭 닫기`}
              className="inline-flex items-center justify-center size-4 rounded-full hover:bg-destructive/10 hover:text-destructive"
              onClick={(e) => {
                e.stopPropagation();
                onCloseTab(tab.articleId);
              }}
            >
              <X className="size-3" />
            </span>
          </button>
        );
      })}
    </div>
  );
}
