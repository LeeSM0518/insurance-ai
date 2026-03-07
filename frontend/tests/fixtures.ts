import type { Page } from '@playwright/test';

/**
 * 인증된 사용자 상태를 시뮬레이션합니다.
 * localStorage에 토큰을 설정하고 /users/me API를 mock합니다.
 * 반드시 page.goto() 호출 전에 실행해야 합니다.
 */
export async function mockAuthenticatedUser(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('insurance_ai_access_token', 'mock-access-token');
    localStorage.setItem('insurance_ai_refresh_token', 'mock-refresh-token');
  });

  await page.route('**/api/v1/users/me', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        statusCode: 200,
        data: {
          id: '550e8400-e29b-41d4-a716-446655440000',
          nickname: '테스트 사용자',
          email: 'test@example.com',
          createdAt: '2026-01-01T00:00:00Z',
        },
      }),
    }),
  );
}
