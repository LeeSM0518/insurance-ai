import { NavLink, useLocation } from 'react-router';
import { useMediaQuery, BREAKPOINTS } from '@/hooks/use-media-query';
import { TAB_ITEMS, getActiveTab } from '@/lib/navigation';
import { ProfileDropdown } from '@/features/my/profile-dropdown';

export function Header() {
  const isDesktop = useMediaQuery(BREAKPOINTS.desktop);
  const { pathname } = useLocation();
  const activeTab = getActiveTab(pathname);

  return (
    <header className="sticky top-0 z-50 bg-background border-b border-border h-header desktop:h-header-desktop flex items-center px-4">
      <div className="flex items-center justify-between w-full">
        {/* 로고 */}
        <NavLink to="/" className="text-lg font-bold text-primary">
          Insurance AI
        </NavLink>

        <div className="flex items-center gap-1">
          {/* 데스크톱 네비게이션 */}
          {isDesktop && (
            <nav className="flex items-center gap-1 mr-2">
              {TAB_ITEMS.map((tab) => {
                const Icon = tab.icon;
                return (
                  <NavLink
                    key={tab.id}
                    to={tab.path}
                    aria-current={activeTab === tab.id ? 'page' : undefined}
                    aria-label={tab.label}
                    className={`touch-target flex items-center justify-center rounded-full transition-colors ${
                      activeTab === tab.id
                        ? 'text-primary bg-primary/10'
                        : 'text-muted-foreground hover:text-foreground hover:bg-accent'
                    }`}
                  >
                    <Icon className="h-5 w-5" aria-hidden="true" />
                  </NavLink>
                );
              })}
            </nav>
          )}

          {/* 프로필 드롭다운 */}
          <ProfileDropdown />
        </div>
      </div>
    </header>
  );
}
