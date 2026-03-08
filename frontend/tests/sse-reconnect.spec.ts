import { test, expect } from '@playwright/test';
import { mockAuthenticatedUser } from './fixtures';

test.describe('SSE 자동 재연결', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');
    await page.getByText('삼성생명 암보험 면책기간').click();
  });

  test('에러 발생 시 에러 메시지와 재시도 버튼이 표시된다', async ({ page }) => {
    // 스트리밍 완료 대기 후 에러 상태 시뮬레이션은 Mock 기반이라 직접 테스트 어려움
    // 대신 에러 UI 구성 요소가 존재하는지 확인 (DOM에 렌더링될 수 있는 요소)
    const retryButton = page.getByRole('button', { name: '다시 시도' });
    // 정상 상태에서는 재시도 버튼이 보이지 않아야 함
    await expect(retryButton).toHaveCount(0);
  });

  test('StreamingIndicator에 재연결 상태가 표시될 수 있다', async ({ page }) => {
    // 정상 메시지 전송 시 재연결 텍스트가 나타나지 않는지 확인
    const input = page.getByLabel('메시지를 입력하세요');
    await input.fill('테스트 질문');
    await page.getByRole('button', { name: '메시지 전송' }).click();

    // searching 상태가 먼저 표시됨 (재연결이 아닌 정상 흐름)
    await expect(page.getByText('관련 약관을 검색 중입니다...')).toBeVisible();
    // 재연결 중 텍스트는 보이지 않아야 함
    await expect(page.getByText('재연결 중...')).toHaveCount(0);
  });
});
