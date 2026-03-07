import { NavLink, useLocation } from 'react-router';
import { TAB_ITEMS, getActiveTab } from '@/lib/navigation';

export function BottomTabBar() {
  const { pathname } = useLocation();
  const activeTab = getActiveTab(pathname);

  return (
    <nav
      aria-label="하단 탭"
      className="fixed bottom-0 inset-x-0 z-50 bg-background border-t border-border shadow-tab-bar h-tab-bar pb-safe-bottom desktop:hidden"
    >
      <div className="flex items-center justify-around h-full">
        {TAB_ITEMS.map((tab) => {
          const isActive = activeTab === tab.id;
          const Icon = tab.icon;

          return (
            <NavLink
              key={tab.id}
              to={tab.path}
              aria-current={isActive ? 'page' : undefined}
              className={`touch-target flex flex-col items-center justify-center gap-0.5 text-xs font-medium transition-colors ${
                isActive ? 'text-primary' : 'text-muted-foreground'
              }`}
            >
              <Icon className="h-5 w-5" aria-hidden="true" />
              <span>{tab.label}</span>
            </NavLink>
          );
        })}
      </div>
    </nav>
  );
}
