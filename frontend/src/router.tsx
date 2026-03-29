import { createBrowserRouter } from 'react-router';
import { lazy } from 'react';
import { AuthLayout } from '@/components/auth/auth-layout';
import { ProtectedAppLayoutRoute } from '@/components/layout/app-layout-route';

// 페이지 lazy loading (기획서 05 §8.1)
const LoginPage = lazy(() => import('@/pages/login'));
const AuthCallbackPage = lazy(() => import('@/pages/auth-callback'));
const SearchPage = lazy(() => import('@/pages/search'));
const SearchResultsPage = lazy(() => import('@/pages/search-results'));
const ChatPage = lazy(() => import('@/pages/chat'));
const PolicyDetailPage = lazy(() => import('@/pages/policy-detail'));
const ProfilePage = lazy(() => import('@/pages/profile'));
const TermsPage = lazy(() => import('@/pages/terms'));
const PrivacyPage = lazy(() => import('@/pages/privacy'));
const CheatsheetPage = lazy(() => import('@/pages/cheatsheet'));
const CheatsheetPrintPage = lazy(() => import('@/pages/cheatsheet-print'));
const ComparePage = lazy(() => import('@/pages/compare'));
const SharedReportPage = lazy(() => import('@/pages/shared-report'));
const MyReportsPage = lazy(() => import('@/pages/my-reports'));
const NotFoundPage = lazy(() => import('@/pages/not-found'));

export const router = createBrowserRouter([
  {
    // 최상위: AuthProvider 래퍼
    Component: AuthLayout,
    children: [
      // 인증 불필요
      {
        path: '/login',
        Component: LoginPage,
      },
      {
        path: '/auth/callback',
        Component: AuthCallbackPage,
      },
      {
        path: '/terms',
        Component: TermsPage,
      },
      {
        path: '/privacy',
        Component: PrivacyPage,
      },
      {
        path: '/shared/:shareToken',
        Component: SharedReportPage,
      },

      // 인증 필요 (ProtectedRoute + AppLayout)
      {
        Component: ProtectedAppLayoutRoute,
        children: [
          {
            path: '/',
            Component: ChatPage,
          },
          {
            path: '/chat',
            Component: ChatPage,
          },
          {
            path: '/search',
            Component: SearchPage,
          },
          {
            path: '/search/results',
            Component: SearchResultsPage,
          },
          {
            path: '/policies/:articleId',
            Component: PolicyDetailPage,
          },
          {
            path: '/profile',
            Component: ProfilePage,
          },
          {
            path: '/cheatsheet',
            Component: CheatsheetPage,
          },
          {
            path: '/cheatsheet/print',
            Component: CheatsheetPrintPage,
          },
          {
            path: '/compare',
            Component: ComparePage,
          },
          {
            path: '/reports',
            Component: MyReportsPage,
          },
        ],
      },

      // 404
      {
        path: '*',
        Component: NotFoundPage,
      },
    ],
  },
]);
