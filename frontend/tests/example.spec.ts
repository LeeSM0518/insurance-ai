import { test, expect } from '@playwright/test';

test('앱이 정상적으로 로딩된다', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Insurance AI/);
});
