import { Search, MessageSquare, User, type LucideIcon } from 'lucide-react';

export type TabId = 'search' | 'chat' | 'my';

export interface TabItem {
  id: TabId;
  label: string;
  path: string;
  icon: LucideIcon;
}

export function getActiveTab(pathname: string): TabId | null {
  if (pathname === '/' || pathname.startsWith('/search')) return 'search';
  if (pathname.startsWith('/chat')) return 'chat';
  if (pathname.startsWith('/my')) return 'my';
  return null;
}

export const TAB_ITEMS: TabItem[] = [
  { id: 'search', label: '검색', path: '/', icon: Search },
  { id: 'chat', label: 'AI상담', path: '/chat', icon: MessageSquare },
  { id: 'my', label: 'MY', path: '/my', icon: User },
];
