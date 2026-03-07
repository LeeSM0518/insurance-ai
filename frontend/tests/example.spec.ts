import { test, expect } from '@playwright/test';

test('앱이 정상적으로 로딩된다', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Insurance AI/);
});

test('존재하지 않는 경로는 404 페이지를 표시한다', async ({ page }) => {
  await page.goto('/nonexistent');
  await expect(page.getByText('404')).toBeVisible();
  await expect(page.getByText('페이지를 찾을 수 없습니다')).toBeVisible();
});

test('404 페이지에서 메인으로 이동할 수 있다', async ({ page }) => {
  await page.goto('/nonexistent');
  await page.getByRole('button', { name: '메인으로 이동' }).click();
  await expect(page).toHaveURL('/');
});

test('로그인 페이지로 이동할 수 있다', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByText('Insurance AI')).toBeVisible();
});
