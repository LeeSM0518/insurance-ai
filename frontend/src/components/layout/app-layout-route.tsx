import { Outlet } from 'react-router';
import { AppLayout } from './app-layout';
import { ProtectedRoute } from '@/components/auth/protected-route';

export function ProtectedAppLayoutRoute() {
  return (
    <ProtectedRoute>
      <AppLayout>
        <Outlet />
      </AppLayout>
    </ProtectedRoute>
  );
}
