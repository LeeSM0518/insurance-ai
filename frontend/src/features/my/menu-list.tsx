import { useState } from 'react';
import { LogOut, FileText, Lock, Info, ChevronRight } from 'lucide-react';
import { useAuth } from '@/hooks/use-auth';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';

const APP_VERSION = '0.1.0';

function MenuGroup({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1">
      <h2 className="px-1 text-xs font-medium text-muted-foreground">
        {label}
      </h2>
      <div className="rounded-xl bg-muted/50 divide-y divide-border">
        {children}
      </div>
    </div>
  );
}

function MenuItem({
  icon: Icon,
  label,
  onClick,
  href,
  rightText,
  variant = 'default',
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  onClick?: () => void;
  href?: string;
  rightText?: string;
  variant?: 'default' | 'destructive';
}) {
  const content = (
    <>
      <Icon
        className={`h-5 w-5 shrink-0 ${variant === 'destructive' ? 'text-destructive' : 'text-muted-foreground'}`}
        aria-hidden="true"
      />
      <span
        className={`flex-1 ${variant === 'destructive' ? 'text-destructive' : ''}`}
      >
        {label}
      </span>
      {rightText ? (
        <span className="text-sm text-muted-foreground">{rightText}</span>
      ) : (
        <ChevronRight
          className="h-4 w-4 text-muted-foreground"
          aria-hidden="true"
        />
      )}
    </>
  );

  const className =
    'flex items-center gap-3 px-4 py-3 text-sm transition-colors hover:bg-accent/50';

  if (href) {
    return (
      <a
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        className={className}
      >
        {content}
      </a>
    );
  }

  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={`w-full ${className}`}>
        {content}
      </button>
    );
  }

  // 클릭 불가 항목 (앱 버전 등)
  return <div className={className}>{content}</div>;
}

export function AccountMenu() {
  const { logout } = useAuth();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const handleLogout = async () => {
    setIsLoggingOut(true);
    await logout();
  };

  return (
    <MenuGroup label="계정">
      <AlertDialog>
        <AlertDialogTrigger
          render={
            <MenuItem icon={LogOut} label="로그아웃" variant="destructive" />
          }
        />
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
    </MenuGroup>
  );
}

export function ServiceInfoMenu() {
  return (
    <MenuGroup label="서비스 정보">
      <MenuItem icon={FileText} label="서비스 이용약관" href="#" />
      <MenuItem icon={Lock} label="개인정보처리방침" href="#" />
      <MenuItem icon={Info} label="앱 버전" rightText={`v${APP_VERSION}`} />
    </MenuGroup>
  );
}
