import { test, expect } from '@playwright/test';
import { mockAuthenticatedUser } from './fixtures';
import type { Page } from '@playwright/test';

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

test.describe('프로필 드롭다운', () => {
  test.describe('데스크톱', () => {
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

  test.describe('모바일', () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 812 });
    });

    test('모바일에서도 프로필 드롭다운이 동작한다', async ({ page }) => {
      await mockAuthenticatedUser(page);
      await page.goto('/');

      await page.getByRole('button', { name: '프로필 메뉴' }).click();
      await expect(page.getByText('테스트 사용자')).toBeVisible();
      await expect(page.getByText('로그아웃')).toBeVisible();
    });

    test('비인증 사용자는 /login으로 리다이렉트된다', async ({ page }) => {
      await page.goto('/');
      await expect(page).toHaveURL('/login');
    });
  });
});
