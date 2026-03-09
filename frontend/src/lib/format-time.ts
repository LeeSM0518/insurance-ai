export type DateGroup = 'today' | 'yesterday' | 'last7days' | 'last30days' | 'older';

export function getDateGroup(dateStr: string): DateGroup {
  const date = new Date(dateStr);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const dateOnly = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const diffDays = Math.floor((today.getTime() - dateOnly.getTime()) / (1000 * 60 * 60 * 24));

  if (diffDays === 0) return 'today';
  if (diffDays === 1) return 'yesterday';
  if (diffDays < 7) return 'last7days';
  if (diffDays < 30) return 'last30days';
  return 'older';
}

export function getDateGroupLabel(group: DateGroup): string {
  const labels: Record<DateGroup, string> = {
    today: '오늘',
    yesterday: '어제',
    last7days: '지난 7일',
    last30days: '지난 30일',
    older: '이전',
  };
  return labels[group];
}

// 세션 배열을 그룹별로 분류
export function groupSessionsByDate<T extends { updatedAt: string }>(
  sessions: T[]
): { group: DateGroup; label: string; sessions: T[] }[] {
  const order: DateGroup[] = ['today', 'yesterday', 'last7days', 'last30days', 'older'];
  const grouped = new Map<DateGroup, T[]>();

  for (const session of sessions) {
    const group = getDateGroup(session.updatedAt);
    if (!grouped.has(group)) grouped.set(group, []);
    grouped.get(group)!.push(session);
  }

  return order
    .filter((g) => grouped.has(g))
    .map((g) => ({
      group: g,
      label: getDateGroupLabel(g),
      sessions: grouped.get(g)!,
    }));
}

// 상대 시간 포맷터
export function formatRelativeTime(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMinutes = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffMinutes < 1) return '방금 전';
  if (diffMinutes < 60) return `${diffMinutes}분 전`;
  if (diffHours < 24) return `${diffHours}시간 전`;
  if (diffDays === 1) return '어제';
  if (diffDays < 7) return `${diffDays}일 전`;

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}.${month}.${day}`;
}
