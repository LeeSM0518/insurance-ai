import { User } from 'lucide-react';
import type { UserResponse } from '@insurance-ai/api-client';

function formatJoinDate(createdAt: string): string {
  const date = new Date(createdAt);
  const year = date.getFullYear();
  const month = date.getMonth() + 1;
  const day = date.getDate();
  return `${year}년 ${month}월 ${day}일 가입`;
}

interface ProfileSectionProps {
  user: UserResponse;
}

export function ProfileSection({ user }: ProfileSectionProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl bg-muted/50 p-6">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10">
        <User className="h-8 w-8 text-primary" aria-hidden="true" />
      </div>
      <div className="flex flex-col items-center gap-1">
        <span className="text-xl font-semibold">{user.nickname}</span>
        {user.email && (
          <span className="text-sm text-muted-foreground">{user.email}</span>
        )}
        <span className="text-sm text-muted-foreground">
          {formatJoinDate(user.createdAt)}
        </span>
      </div>
    </div>
  );
}
