import { test, expect } from '@playwright/test';
import { mockAuthenticatedUser } from './fixtures';

test.describe('SourceTooltip', () => {
  test.describe('데스크톱', () => {
    test.beforeEach(async ({ page }) => {
      await mockAuthenticatedUser(page);
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto('/');
      // 출처가 있는 세션 선택
      await page.getByRole('complementary').getByRole('heading', { name: '삼성생명 암보험 면책기간' }).click();
    });

    test('인라인 [1] 호버 시 Tooltip 표시 확인', async ({ page }) => {
      // 인라인 출처 버튼 [1] 찾기
      const sourceButton = page.locator('[data-source-index="1"]').first();
      await expect(sourceButton).toBeVisible();

      // 호버 시 Tooltip 표시
      await sourceButton.hover();
      await expect(page.getByText('무배당 삼성 암보험')).toBeVisible({ timeout: 3000 });
    });

    test('인라인 [1] 클릭 시 사이드패널 탭 열림 확인', async ({ page }) => {
      const sourceButton = page.locator('[data-source-index="1"]').first();
      await sourceButton.click();

      // 사이드패널에 약관 상세 탭 표시
      await expect(page.getByRole('tab', { name: /삼성생명·제5조/ })).toBeVisible();
    });

    test('Tooltip 내용이 출처 정보를 정확히 표시 확인', async ({ page }) => {
      // 두 번째 출처 [2] 호버
      const sourceButton = page.locator('[data-source-index="2"]').first();
      await sourceButton.hover();

      await expect(page.getByText('한화생명')).toBeVisible({ timeout: 3000 });
      await expect(page.getByText('한화 무배당 건강보험')).toBeVisible();
      await expect(page.getByText('제3조 보장 내용')).toBeVisible();
    });
  });

  test.describe('모바일', () => {
    test.beforeEach(async ({ page }) => {
      await mockAuthenticatedUser(page);
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto('/');
      // 모바일에서 세션 선택
      await page.getByRole('button', { name: '대화 목록' }).click();
      await page.getByRole('complementary').getByRole('heading', { name: '삼성생명 암보험 면책기간' }).click();
    });

    test('모바일 인라인 [1] 탭 시 바텀시트 표시 확인', async ({ page }) => {
      // 인라인 출처 버튼 [1] 클릭
      const sourceButton = page.locator('[data-source-index="1"]').first();
      await sourceButton.click();

      // 바텀시트에 약관 상세 표시 확인
      await expect(page.getByText('삼성생명 — 보장 제외')).toBeVisible({ timeout: 3000 });
    });
  });
});
