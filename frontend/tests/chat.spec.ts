import { test, expect } from '@playwright/test';

test.describe('AI 채팅 화면', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/chat/session-001');
  });

  test('채팅 헤더에 뒤로가기 버튼과 세션 제목이 표시된다', async ({ page }) => {
    await expect(page.getByRole('button', { name: '뒤로가기' })).toBeVisible();
    await expect(page.getByText('삼성생명 암보험 면책기간')).toBeVisible();
  });

  test('사용자 메시지와 AI 메시지가 구분되어 표시된다', async ({ page }) => {
    // 사용자 메시지
    await expect(page.getByText('삼성생명 암보험의 면책기간이 어떻게 되나요?')).toBeVisible();
    // AI 메시지 (마크다운 렌더링 결과)
    await expect(page.getByText('면책기간에 대해 설명드리겠습니다')).toBeVisible();
  });

  test('AI 메시지에 마크다운이 렌더링된다', async ({ page }) => {
    // h2 또는 h3 태그 존재 확인
    const heading = page.locator('[role="log"] h2, [role="log"] h3');
    await expect(heading.first()).toBeVisible();
  });

  test('출처 카드가 표시된다 (번호, 보험사, 조문)', async ({ page }) => {
    await expect(page.getByText('참고 약관')).toBeVisible();
    // 출처 카드 내 보험사명
    const sourceCards = page.locator('button:has-text("삼성생명")');
    await expect(sourceCards.first()).toBeVisible();
  });

  test('추천 질문 칩이 표시된다', async ({ page }) => {
    await expect(page.getByText('소액암과 일반암의 보장 차이는 뭔가요?')).toBeVisible();
    await expect(page.getByText('면책기간 중 암 진단 시 어떻게 되나요?')).toBeVisible();
  });

  test('복사/공유 버튼이 AI 메시지에 표시된다', async ({ page }) => {
    await expect(page.getByRole('button', { name: '복사' }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: '공유' }).first()).toBeVisible();
  });

  test('면책 고지 문구가 표시된다', async ({ page }) => {
    await expect(page.getByText('AI 답변은 참고용이며')).toBeVisible();
  });

  test('입력창과 전송 버튼이 표시된다', async ({ page }) => {
    await expect(page.getByLabel('메시지를 입력하세요')).toBeVisible();
    await expect(page.getByRole('button', { name: '메시지 전송' })).toBeVisible();
  });

  test('빈 입력 시 전송 버튼이 비활성화된다', async ({ page }) => {
    const sendButton = page.getByRole('button', { name: '메시지 전송' });
    await expect(sendButton).toBeDisabled();
  });

  test('뒤로가기 버튼 클릭 시 세션 목록으로 이동한다', async ({ page }) => {
    await page.getByRole('button', { name: '뒤로가기' }).click();
    await expect(page).toHaveURL(/\/chat$/);
  });
});
