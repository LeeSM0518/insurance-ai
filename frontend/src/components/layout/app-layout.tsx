import { Header } from './header';
import { BottomTabBar } from './bottom-tab-bar';

export function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="h-dvh flex flex-col overflow-hidden">
      <Header />
      <main className="flex-1 flex flex-col min-h-0 overflow-y-auto pb-tab-bar desktop:pb-0">{children}</main>
      <BottomTabBar />
    </div>
  );
}
