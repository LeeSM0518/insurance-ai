import { test, expect } from '@playwright/test';
import { mockAuthenticatedUser } from './fixtures';
import type { Page } from '@playwright/test';

// 이메일 없는 사용자 mock
async function mockUserWithoutEmail(page: Page) {
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
          email: null,
          createdAt: '2026-01-01T00:00:00Z',
        },
      }),
    }),
  );
}

// 로그아웃 API mock
async function mockLogout(page: Page) {
  await page.route('**/api/v1/auth/logout', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, statusCode: 200 }),
    }),
  );
}

test.describe('모바일 MY 페이지', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
  });

  test('프로필 정보가 표시된다', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/my');

    await expect(page.getByText('테스트 사용자')).toBeVisible();
    await expect(page.getByText('test@example.com')).toBeVisible();
    await expect(page.getByText('2026년 1월 1일 가입')).toBeVisible();
  });

  test('이메일 없는 경우 이메일 영역이 숨겨진다', async ({ page }) => {
    await mockUserWithoutEmail(page);
    await page.goto('/my');

    await expect(page.getByText('테스트 사용자')).toBeVisible();
    await expect(page.getByText('test@example.com')).toHaveCount(0);
  });

  test('로그아웃 버튼이 표시된다', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/my');

    await expect(page.getByText('로그아웃')).toBeVisible();
  });

  test('로그아웃 클릭 시 확인 다이얼로그가 표시된다', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/my');

    await page.getByText('로그아웃').click();
    await expect(page.getByText('정말 로그아웃 하시겠습니까?')).toBeVisible();
  });

  test('로그아웃 취소 시 다이얼로그가 닫히고 MY 화면이 유지된다', async ({
    page,
  }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/my');

    await page.getByText('로그아웃').click();
    await expect(page.getByText('정말 로그아웃 하시겠습니까?')).toBeVisible();

    await page.getByRole('button', { name: '취소' }).click();
    await expect(
      page.getByText('정말 로그아웃 하시겠습니까?'),
    ).not.toBeVisible();
    await expect(page).toHaveURL('/my');
  });

  test('로그아웃 확인 시 /login으로 리다이렉트된다', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await mockLogout(page);
    await page.goto('/my');

    await page.getByText('로그아웃').click();
    // 다이얼로그 내 로그아웃 버튼 (destructive)
    const dialog = page.getByRole('alertdialog');
    await dialog.getByRole('button', { name: '로그아웃' }).click();

    await expect(page).toHaveURL('/login');
  });

  test('서비스 정보가 표시된다', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/my');

    await expect(page.getByText('서비스 이용약관')).toBeVisible();
    await expect(page.getByText('개인정보처리방침')).toBeVisible();
    await expect(page.getByText('v0.1.0')).toBeVisible();
  });

  test('비인증 사용자가 /my에 접근하면 /login으로 리다이렉트된다', async ({
    page,
  }) => {
    await page.goto('/my');
    await expect(page).toHaveURL('/login');
  });
});

test.describe('데스크톱 프로필 드롭다운', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
  });

  test('헤더 프로필 아이콘 클릭 시 드롭다운이 표시된다', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/');

    await page.getByRole('button', { name: '프로필 메뉴' }).click();
    await expect(page.getByText('테스트 사용자')).toBeVisible();
    await expect(page.getByText('test@example.com')).toBeVisible();
  });

  test('드롭다운에 서비스 정보가 표시된다', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/');

    await page.getByRole('button', { name: '프로필 메뉴' }).click();
    await expect(page.getByText('서비스 이용약관')).toBeVisible();
    await expect(page.getByText('개인정보처리방침')).toBeVisible();
    await expect(page.getByText('v0.1.0')).toBeVisible();
  });

  test('드롭다운에서 로그아웃 클릭 시 확인 다이얼로그가 표시된다', async ({
    page,
  }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/');

    await page.getByRole('button', { name: '프로필 메뉴' }).click();
    await page.getByRole('menuitem', { name: '로그아웃' }).click();
    await expect(page.getByText('정말 로그아웃 하시겠습니까?')).toBeVisible();
  });

  test('드롭다운 로그아웃 확인 시 /login으로 리다이렉트된다', async ({
    page,
  }) => {
    await mockAuthenticatedUser(page);
    await mockLogout(page);
    await page.goto('/');

    await page.getByRole('button', { name: '프로필 메뉴' }).click();
    await page.getByRole('menuitem', { name: '로그아웃' }).click();

    const dialog = page.getByRole('alertdialog');
    await dialog.getByRole('button', { name: '로그아웃' }).click();

    await expect(page).toHaveURL('/login');
  });

  test('드롭다운 외부 클릭 시 닫힌다', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/');

    await page.getByRole('button', { name: '프로필 메뉴' }).click();
    await expect(page.getByText('테스트 사용자')).toBeVisible();

    // 드롭다운 외부 클릭 (본문 영역)
    await page.locator('body').click({ position: { x: 100, y: 400 } });
    // base-ui 드롭다운은 외부 클릭 시 닫힘
    await expect(
      page.locator('[data-slot="dropdown-menu-content"]'),
    ).not.toBeVisible();
  });
});
