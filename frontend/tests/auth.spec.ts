import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';

// API mock 헬퍼: 로그인된 상태를 시뮬레이션
async function mockAuthenticatedUser(page: Page) {
  // localStorage에 토큰 설정
  await page.addInitScript(() => {
    localStorage.setItem('insurance_ai_access_token', 'mock-access-token');
    localStorage.setItem('insurance_ai_refresh_token', 'mock-refresh-token');
  });

  // GET /api/v1/users/me 성공 응답
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

// API mock 헬퍼: 비인증 상태 (토큰 만료)
async function mockUnauthenticatedUser(page: Page) {
  await page.route('**/api/v1/users/me', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({
        success: false,
        statusCode: 401,
        code: 'AUTH:TOKEN_EXPIRED',
        message: '만료된 토큰입니다.',
      }),
    }),
  );
}

test.describe('인증 흐름', () => {
  test('비인증 사용자가 보호된 라우트에 접근하면 /login으로 리다이렉트된다', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page).toHaveURL('/login');
  });

  test('비인증 사용자가 /search에 접근하면 /login으로 리다이렉트된다', async ({
    page,
  }) => {
    await page.goto('/search');
    await expect(page).toHaveURL('/login');
  });

  test('로그인된 사용자는 메인 화면에 진입할 수 있다', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/');
    await expect(page).toHaveURL('/');
    // AppLayout이 렌더링됨 (하단 탭 바 또는 헤더 확인)
    await expect(page.getByText('Insurance AI')).toBeVisible();
  });

  test('토큰이 만료되면 /login으로 리다이렉트된다', async ({ page }) => {
    // 토큰은 있지만 /users/me가 401 반환
    await page.addInitScript(() => {
      localStorage.setItem('insurance_ai_access_token', 'expired-token');
      localStorage.setItem('insurance_ai_refresh_token', 'expired-refresh');
    });
    await mockUnauthenticatedUser(page);
    // refresh도 실패하도록 mock
    await page.route('**/api/v1/auth/refresh', (route) =>
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          statusCode: 401,
          code: 'AUTH:TOKEN_EXPIRED',
          message: '만료된 토큰입니다.',
        }),
      }),
    );

    await page.goto('/');
    await expect(page).toHaveURL('/login');
  });

  test('이미 로그인된 사용자가 /login에 접근하면 메인으로 리다이렉트된다', async ({
    page,
  }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/login');
    await expect(page).toHaveURL('/');
  });

  test('DEV 환경에서 개발자 로그인 버튼이 표시된다', async ({ page }) => {
    await page.goto('/login');
    const devButton = page.getByTestId('dev-login-button');
    await expect(devButton).toBeVisible();
    await expect(devButton).toContainText('개발자 로그인');
  });

  test('개발자 로그인 버튼 클릭 시 로그인 후 메인으로 이동한다', async ({
    page,
  }) => {
    // dev-login API mock
    await page.route('**/api/v1/auth/dev-login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          statusCode: 200,
          data: {
            accessToken: 'dev-access-token',
            refreshToken: 'dev-refresh-token',
            expiresIn: 1800000,
            user: {
              id: '550e8400-e29b-41d4-a716-446655440000',
              nickname: '개발자',
              email: null,
              createdAt: '2026-01-01T00:00:00Z',
            },
          },
        }),
      }),
    );

    // 로그인 후 메인 진입 시 users/me mock 필요
    await page.route('**/api/v1/users/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          statusCode: 200,
          data: {
            id: '550e8400-e29b-41d4-a716-446655440000',
            nickname: '개발자',
            email: null,
            createdAt: '2026-01-01T00:00:00Z',
          },
        }),
      }),
    );

    await page.goto('/login');
    await page.getByTestId('dev-login-button').click();
    await expect(page).toHaveURL('/');
  });

  test('OAuth 콜백 에러 시 에러 메시지가 표시된다', async ({ page }) => {
    await page.goto('/auth/callback?error=access_denied');
    await expect(page.getByText('로그인이 취소되었습니다.')).toBeVisible();
    await expect(page.getByText('다시 시도')).toBeVisible();
  });

  test('OAuth 콜백 에러 시 다시 시도 버튼이 /login으로 이동한다', async ({
    page,
  }) => {
    await page.goto('/auth/callback?error=access_denied');
    await page.getByText('다시 시도').click();
    await expect(page).toHaveURL('/login');
  });
});
