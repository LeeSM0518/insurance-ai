import { test, expect } from '@playwright/test';
import { mockAuthenticatedUser } from './fixtures';

test.describe('ScrollToBottom + 자동 스크롤', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');
    // 기존 메시지가 있는 세션 선택
    await page.getByRole('complementary').getByRole('heading', { name: '삼성생명 암보험 면책기간' }).click();
  });

  test('초기 진입 시 ScrollToBottom 버튼이 숨겨져 있다', async ({ page }) => {
    const scrollBtn = page.getByLabel('최신 메시지로 이동');
    // 초기에는 하단에 있으므로 버튼이 보이지 않아야 함
    await expect(scrollBtn).toHaveClass(/opacity-0/);
  });

  test('새 메시지 전송 후 자동 스크롤이 동작한다', async ({ page }) => {
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('테스트 메시지');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // 메시지 전송 후 사용자 메시지가 표시됨 (자동 스크롤로 하단에 보임)
    await expect(page.getByText('테스트 메시지')).toBeVisible();
  });

  test('ScrollToBottom 버튼에 "최신 메시지" 텍스트가 있다', async ({ page }) => {
    const scrollBtn = page.getByLabel('최신 메시지로 이동');
    await expect(scrollBtn).toContainText('최신 메시지');
  });
});
