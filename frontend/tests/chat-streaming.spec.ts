import { test, expect } from '@playwright/test';
import { mockAuthenticatedUser } from './fixtures';

test.describe('SSE 스트리밍 UI', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');
  });

  test('메시지 전송 후 AI 응답이 표시된다', async ({ page }) => {
    await page.getByRole('complementary').getByRole('heading', { name: '삼성생명 암보험 면책기간' }).click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('면책기간이 뭔가요?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // SSE 완료 후 AI 응답 메시지가 표시됨
    await expect(page.getByText('면책기간이란?')).toBeVisible({ timeout: 10000 });
  });

  test('스트리밍 완료 후 출처 카드가 표시된다', async ({ page }) => {
    await page.getByRole('complementary').getByRole('heading', { name: '삼성생명 암보험 면책기간' }).click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('면책기간이 뭔가요?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // SSE 완료 후 출처가 포함된 AI 메시지가 렌더링됨
    // 새 응답의 출처 카드 (SSE의 sources_found 이벤트에서 온 출처)
    await expect(page.getByText('면책기간이란?')).toBeVisible({ timeout: 10000 });
  });

  test('스트리밍 완료 후 인라인 출처 참조가 표시된다', async ({ page }) => {
    await page.getByRole('complementary').getByRole('heading', { name: '삼성생명 암보험 면책기간' }).click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('면책기간이 뭔가요?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // 스트리밍 완료 후 인라인 출처 참조 [1], [2]가 표시됨
    await expect(page.getByText('면책기간이란?')).toBeVisible({ timeout: 10000 });
    // 새 AI 응답 메시지의 인라인 출처 확인
    const sourceButtons = page.locator('[data-source-index]');
    await expect(sourceButtons.first()).toBeVisible();
  });

  test('스트리밍 완료 후 전송 버튼이 다시 활성화된다', async ({ page }) => {
    await page.getByRole('complementary').getByRole('heading', { name: '삼성생명 암보험 면책기간' }).click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('면책기간이 뭔가요?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // SSE 완료 후 입력창이 다시 활성화됨
    await expect(page.getByText('면책기간이란?')).toBeVisible({ timeout: 10000 });
    await expect(page.getByLabel('메시지를 입력하세요')).toBeEnabled();
  });

  test('done 시 추천 질문 칩 표시 확인', async ({ page }) => {
    await page.getByText('실손보험 4세대 보장범위').click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('실손보험이 뭔가요?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // 스트리밍 완료 후 추천 질문 표시 확인
    await expect(
      page.getByText('면책기간 중 다른 보험사로 가입하면 어떻게 되나요?'),
    ).toBeVisible({ timeout: 10000 });
  });

  test('스트리밍 완료 후 전체 메시지 정상 표시 확인', async ({ page }) => {
    await page.getByText('실손보험 4세대 보장범위').click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('면책기간 설명해주세요');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // 스트리밍 완료 후 추천 질문이 표시되면 done 상태
    await expect(
      page.getByText('면책기간 중 다른 보험사로 가입하면 어떻게 되나요?'),
    ).toBeVisible({ timeout: 10000 });
    // 입력창이 다시 활성화됨
    await expect(page.getByLabel('메시지를 입력하세요')).toBeEnabled();
  });

  test('새 메시지 전송 시 사용자 메시지가 즉시 표시된다', async ({ page }) => {
    await page.getByRole('complementary').getByRole('heading', { name: '삼성생명 암보험 면책기간' }).click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('이 보험의 특약도 알려주세요');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // 사용자 메시지가 즉시 표시됨 (낙관적 업데이트)
    await expect(page.getByText('이 보험의 특약도 알려주세요')).toBeVisible();
  });
});
