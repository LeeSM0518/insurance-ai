import { useAuth } from '@/hooks/use-auth';
import { ProfileSection } from '@/features/my/profile-section';
import { AccountMenu, ServiceInfoMenu } from '@/features/my/menu-list';

export default function MyPage() {
  const { user } = useAuth();

  if (!user) return null;

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="mx-auto max-w-lg space-y-6 p-4">
        <ProfileSection user={user} />
        <AccountMenu />
        <ServiceInfoMenu />
      </div>
    </div>
  );
}
