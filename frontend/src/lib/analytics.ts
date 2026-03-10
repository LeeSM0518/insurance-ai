import ReactGA from 'react-ga4';

const GA_MEASUREMENT_ID = import.meta.env.VITE_GA_MEASUREMENT_ID as
  | string
  | undefined;

/** GA4 초기화 — 측정 ID 미설정 시 자동 비활성화 */
export function initGA() {
  if (!GA_MEASUREMENT_ID) return;
  ReactGA.initialize(GA_MEASUREMENT_ID);
}

/** SPA 페이지뷰 추적 */
export function trackPageView(path: string) {
  if (!GA_MEASUREMENT_ID) return;
  ReactGA.send({ hitType: 'pageview', page: path });
}

/** 커스텀 이벤트 추적 */
export function trackEvent(
  category: string,
  action: string,
  label?: string,
  value?: number,
) {
  if (!GA_MEASUREMENT_ID) return;
  ReactGA.event({ category, action, label, value });
}
