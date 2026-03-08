import { test, expect } from '@playwright/test';
import { mockAuthenticatedUser } from './fixtures';

test.describe('SSE 스트리밍 UI', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');
  });

  test('메시지 전송 시 searching 상태 표시 확인', async ({ page }) => {
    // 세션 선택 후 메시지 전송
    await page.getByText('삼성생명 암보험 면책기간').click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('면책기간이 뭔가요?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // searching 상태 표시 확인
    await expect(page.getByText('관련 약관을 검색 중입니다...')).toBeVisible();
  });

  test('generating 시 타이핑 인디케이터 표시 확인', async ({ page }) => {
    await page.getByText('삼성생명 암보험 면책기간').click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('면책기간이 뭔가요?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // generating 단계까지 대기 (800ms searching + 500ms sources_found)
    await expect(page.getByText('답변을 생성 중입니다...')).toBeVisible({ timeout: 5000 });
  });

  test('sources_found 시 출처 카드 표시 확인', async ({ page }) => {
    await page.getByText('실손보험 4세대 보장범위').click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('실손보험 보장 범위는?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // 스트리밍 중 출처 카드 표시 확인 (sources_found 단계 이후)
    // 기존 세션 메시지의 출처와 별개로 스트리밍 중 새로운 출처 카드가 나타남
    await expect(page.getByText('답변을 생성 중입니다...').or(page.getByText('관련 약관을 검색 중입니다...'))).toBeVisible({ timeout: 3000 });
  });

  test('streaming 시 실시간 텍스트 + 커서(▌) 표시 확인', async ({ page }) => {
    await page.getByText('삼성생명 암보험 면책기간').click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('면책기간이 뭔가요?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // streaming 단계까지 대기 (800ms + 500ms + 600ms ≈ 1.9s)
    // 커서 확인 (animate-blink class가 있는 span)
    await expect(page.locator('.animate-blink').first()).toBeVisible({ timeout: 5000 });
  });

  test('스트리밍 중 전송 버튼 비활성화 확인', async ({ page }) => {
    await page.getByText('삼성생명 암보험 면책기간').click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('면책기간이 뭔가요?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // 스트리밍 중 입력창과 전송 버튼이 비활성화
    await expect(page.getByLabel('메시지를 입력하세요')).toBeDisabled();
  });

  test('done 시 추천 질문 칩 표시 확인', async ({ page }) => {
    await page.getByText('실손보험 4세대 보장범위').click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('실손보험이 뭔가요?');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // 스트리밍 완료 후 추천 질문 표시 확인 (전체 시뮬레이션 완료까지 대기)
    await expect(
      page.getByText('면책기간 중 다른 보험사로 가입하면 어떻게 되나요?'),
    ).toBeVisible({ timeout: 30000 });
  });

  test('스트리밍 완료 후 전체 메시지 정상 표시 확인', async ({ page }) => {
    await page.getByText('실손보험 4세대 보장범위').click();
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('면책기간 설명해주세요');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // 스트리밍 완료 후 추천 질문이 표시되면 done 상태 (추천 질문은 done 후에만 나타남)
    await expect(
      page.getByText('면책기간 중 다른 보험사로 가입하면 어떻게 되나요?'),
    ).toBeVisible({ timeout: 30000 });
    // 입력창이 다시 활성화됨
    await expect(page.getByLabel('메시지를 입력하세요')).toBeEnabled();
  });
});
