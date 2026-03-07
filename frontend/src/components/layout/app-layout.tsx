import { Header } from './header';
import { BottomTabBar } from './bottom-tab-bar';

export function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-dvh flex flex-col">
      <Header />
      <main className="flex-1 flex flex-col min-h-0 pb-tab-bar desktop:pb-0">{children}</main>
      <BottomTabBar />
    </div>
  );
}
