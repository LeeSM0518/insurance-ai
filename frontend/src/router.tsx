import { createBrowserRouter } from 'react-router';
import { lazy } from 'react';

// 페이지 lazy loading (기획서 05 §8.1)
const LoginPage = lazy(() => import('@/pages/login'));
const AuthCallbackPage = lazy(() => import('@/pages/auth-callback'));
const SearchPage = lazy(() => import('@/pages/search'));
const SearchResultsPage = lazy(() => import('@/pages/search-results'));
const ChatListPage = lazy(() => import('@/pages/chat-list'));
const ChatPage = lazy(() => import('@/pages/chat'));
const PolicyDetailPage = lazy(() => import('@/pages/policy-detail'));
const MyPage = lazy(() => import('@/pages/my'));
const NotFoundPage = lazy(() => import('@/pages/not-found'));

export const router = createBrowserRouter([
  // 인증 불필요 라우트
  {
    path: '/login',
    Component: LoginPage,
  },
  {
    path: '/auth/callback',
    Component: AuthCallbackPage,
  },

  // 인증 필요 라우트 (Step 5에서 ProtectedRoute 래핑 추가)
  {
    path: '/',
    Component: SearchPage,
  },
  {
    path: '/search',
    Component: SearchResultsPage,
  },
  {
    path: '/chat',
    Component: ChatListPage,
  },
  {
    path: '/chat/:sessionId',
    Component: ChatPage,
  },
  {
    path: '/policies/:articleId',
    Component: PolicyDetailPage,
  },
  {
    path: '/my',
    Component: MyPage,
  },

  // 404
  {
    path: '*',
    Component: NotFoundPage,
  },
]);
