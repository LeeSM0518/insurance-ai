import { Search, MessageSquare, User, FileText, type LucideIcon } from 'lucide-react';

export type TabId = 'search' | 'chat' | 'profile' | 'cheatsheet';

export interface TabItem {
  id: TabId;
  label: string;
  path: string;
  icon: LucideIcon;
  /** 모바일 하단 탭바에서만 표시 */
  mobileOnly?: boolean;
}

export function getActiveTab(pathname: string): TabId | null {
  if (pathname === '/' || pathname.startsWith('/chat')) return 'chat';
  if (pathname.startsWith('/search')) return 'search';
  if (pathname === '/profile') return 'profile';
  if (pathname === '/cheatsheet') return 'cheatsheet';
  return null;
}

export const TAB_ITEMS: TabItem[] = [
  { id: 'chat', label: 'AI', path: '/', icon: MessageSquare },
  { id: 'search', label: '검색', path: '/search', icon: Search },
  { id: 'cheatsheet', label: '치트시트', path: '/cheatsheet', icon: FileText },
  { id: 'profile', label: '프로필', path: '/profile', icon: User, mobileOnly: true },
];
