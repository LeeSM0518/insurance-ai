import { useEffect } from 'react';
import { useLocation } from 'react-router';
import { trackPageView } from '../lib/analytics';

/** 라우트 변경 시 GA4 페이지뷰 자동 추적 */
export function usePageTracking() {
  const location = useLocation();

  useEffect(() => {
    trackPageView(location.pathname + location.search);
  }, [location.pathname, location.search]);
}
