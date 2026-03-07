import { Search, MessageSquare, type LucideIcon } from 'lucide-react';

export type TabId = 'search' | 'chat';

export interface TabItem {
  id: TabId;
  label: string;
  path: string;
  icon: LucideIcon;
}

export function getActiveTab(pathname: string): TabId | null {
  if (pathname === '/' || pathname.startsWith('/chat')) return 'chat';
  if (pathname.startsWith('/search')) return 'search';
  return null;
}

export const TAB_ITEMS: TabItem[] = [
  { id: 'chat', label: 'AI', path: '/', icon: MessageSquare },
  { id: 'search', label: '검색', path: '/search', icon: Search },
];
