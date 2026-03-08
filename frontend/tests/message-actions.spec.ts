import { test, expect } from '@playwright/test';
import { mockAuthenticatedUser } from './fixtures';

test.describe('메시지 액션 버튼', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');
    // 기존 메시지가 있는 세션 선택
    await page.getByText('삼성생명 암보험 면책기간').click();
  });

  test('AI 메시지에 복사/공유/좋아요/싫어요 버튼이 표시된다', async ({ page }) => {
    // AI 메시지에 4개 액션 버튼 확인
    const assistantMessage = page.locator('[class*="bg-chat-ai-bg"]').first();
    await expect(assistantMessage.getByLabel('복사')).toBeVisible();
    await expect(assistantMessage.getByLabel('공유')).toBeVisible();
    await expect(assistantMessage.getByLabel('좋아요')).toBeVisible();
    await expect(assistantMessage.getByLabel('싫어요')).toBeVisible();
  });

  test('USER 메시지에는 액션 버튼이 표시되지 않는다', async ({ page }) => {
    const userMessage = page.locator('[class*="bg-chat-user-bg"]').first();
    await expect(userMessage.getByLabel('복사')).toHaveCount(0);
    await expect(userMessage.getByLabel('좋아요')).toHaveCount(0);
  });

  test('좋아요 클릭 시 활성 스타일이 적용된다', async ({ page }) => {
    const thumbsUp = page.locator('[class*="bg-chat-ai-bg"]').first().getByLabel('좋아요');
    await thumbsUp.click();
    await expect(thumbsUp).toHaveAttribute('aria-pressed', 'true');
  });

  test('싫어요 클릭 시 좋아요가 해제된다', async ({ page }) => {
    const assistantMessage = page.locator('[class*="bg-chat-ai-bg"]').first();
    const thumbsUp = assistantMessage.getByLabel('좋아요');
    const thumbsDown = assistantMessage.getByLabel('싫어요');

    // 좋아요 클릭
    await thumbsUp.click();
    await expect(thumbsUp).toHaveAttribute('aria-pressed', 'true');

    // 싫어요 클릭 → 좋아요 해제
    await thumbsDown.click();
    await expect(thumbsDown).toHaveAttribute('aria-pressed', 'true');
    await expect(thumbsUp).toHaveAttribute('aria-pressed', 'false');
  });

  test('같은 피드백 재클릭 시 해제된다', async ({ page }) => {
    const thumbsUp = page.locator('[class*="bg-chat-ai-bg"]').first().getByLabel('좋아요');

    // 클릭 → 활성
    await thumbsUp.click();
    await expect(thumbsUp).toHaveAttribute('aria-pressed', 'true');

    // 재클릭 → 해제
    await thumbsUp.click();
    await expect(thumbsUp).toHaveAttribute('aria-pressed', 'false');
  });
});
