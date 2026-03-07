import { test, expect } from '@playwright/test';

test('메인 페이지에 Header가 표시된다', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('banner')).toBeVisible();
  await expect(page.getByText('Insurance AI')).toBeVisible();
});

test('메인 페이지에 하단 탭바가 표시된다 (모바일)', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/');
  await expect(page.getByRole('navigation', { name: '하단 탭' })).toBeVisible();
  await expect(page.getByRole('link', { name: '검색' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'AI상담' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'MY' })).toBeVisible();
});

test('데스크톱에서 하단 탭바가 숨겨진다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/');
  await expect(page.getByRole('navigation', { name: '하단 탭' })).toBeHidden();
});

test('데스크톱 Header에 네비게이션 탭이 표시된다', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/');
  const header = page.getByRole('banner');
  await expect(header.getByRole('link', { name: '검색' })).toBeVisible();
  await expect(header.getByRole('link', { name: 'AI상담' })).toBeVisible();
  await expect(header.getByRole('link', { name: 'MY' })).toBeVisible();
});

test('하단 탭으로 페이지 간 이동이 가능하다', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/');

  // AI상담 탭 클릭
  await page.getByRole('link', { name: 'AI상담' }).click();
  await expect(page).toHaveURL('/chat');

  // MY 탭 클릭
  await page.getByRole('link', { name: 'MY' }).click();
  await expect(page).toHaveURL('/my');

  // 검색 탭 클릭 (홈으로)
  await page.getByRole('link', { name: '검색' }).click();
  await expect(page).toHaveURL('/');
});

test('현재 탭이 활성 상태로 표시된다', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/');

  // 검색 탭이 active
  const searchTab = page.getByRole('link', { name: '검색' });
  await expect(searchTab).toHaveAttribute('aria-current', 'page');
});

test('로그인 페이지에는 레이아웃(Header/TabBar)이 없다', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('banner')).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: '하단 탭' })).toHaveCount(0);
});
