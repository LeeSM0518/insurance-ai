import { useState } from 'react';
import { FileText, FolderOpen, Lock, LogOut } from 'lucide-react';
import { useAuth } from '@/hooks/use-auth';
import { trackEvent } from '@/lib/analytics';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

export default function ProfilePage() {
  const { user, logout } = useAuth();
  const [logoutDialogOpen, setLogoutDialogOpen] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const handleLogout = async () => {
    trackEvent('auth', 'logout');
    setIsLoggingOut(true);
    await logout();
  };

  if (!user) return null;

  return (
    <div className="flex-1 px-4 py-6 max-w-md mx-auto w-full space-y-6">
      {/* 사용자 정보 */}
      <div className="flex flex-col items-center gap-2 py-4">
        <div className="size-16 rounded-full bg-muted flex items-center justify-center">
          <span className="text-2xl font-semibold text-muted-foreground">
            {user.nickname.charAt(0)}
          </span>
        </div>
        <div className="text-center">
          <p className="text-lg font-semibold">{user.nickname}</p>
          {user.email && (
            <p className="text-sm text-muted-foreground">{user.email}</p>
          )}
        </div>
      </div>

      {/* 메뉴 목록 */}
      <div className="space-y-1">
        <a
          href="/reports"
          className="flex items-center gap-3 px-3 py-3 rounded-lg text-sm hover:bg-accent transition-colors"
        >
          <FolderOpen className="size-5 text-muted-foreground" aria-hidden="true" />
          내 리포트
        </a>
        <a
          href="/terms"
          className="flex items-center gap-3 px-3 py-3 rounded-lg text-sm hover:bg-accent transition-colors"
        >
          <FileText className="size-5 text-muted-foreground" aria-hidden="true" />
          서비스 이용약관
        </a>
        <a
          href="/privacy"
          className="flex items-center gap-3 px-3 py-3 rounded-lg text-sm hover:bg-accent transition-colors"
        >
          <Lock className="size-5 text-muted-foreground" aria-hidden="true" />
          개인정보처리방침
        </a>
      </div>

      {/* 로그아웃 */}
      <div className="space-y-1">
        <button
          type="button"
          onClick={() => setLogoutDialogOpen(true)}
          className="flex items-center gap-3 px-3 py-3 rounded-lg text-sm text-destructive hover:bg-accent transition-colors w-full"
        >
          <LogOut className="size-5" aria-hidden="true" />
          로그아웃
        </button>
      </div>

      <AlertDialog open={logoutDialogOpen} onOpenChange={setLogoutDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>로그아웃</AlertDialogTitle>
            <AlertDialogDescription>
              정말 로그아웃 하시겠습니까?
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={handleLogout}
              disabled={isLoggingOut}
            >
              로그아웃
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
