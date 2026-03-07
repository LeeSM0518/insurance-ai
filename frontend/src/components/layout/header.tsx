import { NavLink, useLocation } from 'react-router';
import { User } from 'lucide-react';
import { useMediaQuery, BREAKPOINTS } from '@/hooks/use-media-query';
import { TAB_ITEMS, getActiveTab } from '@/lib/navigation';

export function Header() {
  const isDesktop = useMediaQuery(BREAKPOINTS.desktop);
  const { pathname } = useLocation();
  const activeTab = getActiveTab(pathname);

  return (
    <header className="sticky top-0 z-50 bg-background border-b border-border h-header desktop:h-header-desktop flex items-center px-4">
      <div className="flex items-center justify-between w-full max-w-5xl mx-auto">
        {/* 로고 */}
        <NavLink to="/" className="text-lg font-bold text-primary">
          Insurance AI
        </NavLink>

        <div className="flex items-center gap-1">
          {/* 데스크톱 네비게이션 */}
          {isDesktop && (
            <nav className="flex items-center gap-1 mr-2">
              {TAB_ITEMS.map((tab) => (
                <NavLink
                  key={tab.id}
                  to={tab.path}
                  aria-current={activeTab === tab.id ? 'page' : undefined}
                  className={`px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                    activeTab === tab.id
                      ? 'text-primary bg-primary/10'
                      : 'text-muted-foreground hover:text-foreground hover:bg-accent'
                  }`}
                >
                  {tab.label}
                </NavLink>
              ))}
            </nav>
          )}

          {/* 프로필 아이콘 */}
          <button
            type="button"
            className="touch-target flex items-center justify-center rounded-full text-muted-foreground hover:text-foreground transition-colors"
            aria-label="프로필"
          >
            <User className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </div>
    </header>
  );
}
