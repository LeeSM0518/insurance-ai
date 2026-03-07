import { Search, MessageSquare, User, type LucideIcon } from 'lucide-react';

export type TabId = 'search' | 'chat' | 'my';

export interface TabItem {
  id: TabId;
  label: string;
  path: string;
  icon: LucideIcon;
}

export function getActiveTab(pathname: string): TabId | null {
  if (pathname === '/' || pathname.startsWith('/chat')) return 'chat';
  if (pathname.startsWith('/search')) return 'search';
  if (pathname.startsWith('/my')) return 'my';
  return null;
}

export const TAB_ITEMS: TabItem[] = [
  { id: 'chat', label: 'AI', path: '/', icon: MessageSquare },
  { id: 'search', label: '검색', path: '/search', icon: Search },
  { id: 'my', label: 'MY', path: '/my', icon: User },
];
