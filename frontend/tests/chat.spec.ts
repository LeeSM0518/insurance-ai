import { test, expect } from '@playwright/test';

test.describe('AI 상담 통합 화면', () => {
  test.describe('데스크톱', () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto('/chat');
    });

    test('"AI 상담" 제목이 사이드바에 표시된다', async ({ page }) => {
      await expect(page.getByRole('heading', { name: 'AI 상담' })).toBeVisible();
    });

    test('"새 대화" 버튼이 표시된다', async ({ page }) => {
      await expect(page.getByRole('button', { name: /새 대화/ })).toBeVisible();
    });

    test('세션 목록이 사이드바에 렌더링된다', async ({ page }) => {
      await expect(page.getByText('삼성생명 암보험 면책기간')).toBeVisible();
      await expect(page.getByText('실손보험 4세대 보장범위')).toBeVisible();
    });

    test('세션 클릭 시 채팅 영역에 메시지가 표시된다', async ({ page }) => {
      await page.getByText('삼성생명 암보험 면책기간').click();
      await expect(
        page.getByText('삼성생명 암보험의 면책기간이 어떻게 되나요?'),
      ).toBeVisible();
    });

    test('채팅 헤더에 세션 제목이 표시된다', async ({ page }) => {
      await page.getByText('삼성생명 암보험 면책기간').click();
      // 헤더의 h1만 선택 (사이드바의 h3과 구분)
      await expect(page.locator('header h1', { hasText: '삼성생명 암보험 면책기간' })).toBeVisible();
    });

    test('사용자/AI 메시지가 구분되어 표시된다', async ({ page }) => {
      await page.getByText('삼성생명 암보험 면책기간').click();
      await expect(
        page.getByText('삼성생명 암보험의 면책기간이 어떻게 되나요?'),
      ).toBeVisible();
      await expect(page.getByText('면책기간에 대해 설명드리겠습니다')).toBeVisible();
    });

    test('AI 메시지에 마크다운이 렌더링된다', async ({ page }) => {
      await page.getByText('삼성생명 암보험 면책기간').click();
      const heading = page.locator('[role="log"] h2, [role="log"] h3');
      await expect(heading.first()).toBeVisible();
    });

    test('출처 카드가 표시된다', async ({ page }) => {
      await page.getByText('삼성생명 암보험 면책기간').click();
      await expect(page.getByText('참고 약관')).toBeVisible();
      const sourceCards = page.locator('button:has-text("삼성생명")');
      await expect(sourceCards.first()).toBeVisible();
    });

    test('추천 질문 칩이 표시된다', async ({ page }) => {
      await page.getByText('삼성생명 암보험 면책기간').click();
      await expect(page.getByText('소액암과 일반암의 보장 차이는 뭔가요?')).toBeVisible();
      await expect(page.getByText('면책기간 중 암 진단 시 어떻게 되나요?')).toBeVisible();
    });

    test('복사/공유 버튼이 표시된다', async ({ page }) => {
      await page.getByText('삼성생명 암보험 면책기간').click();
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

    test('세션이 없을 때 EmptyState가 표시된다', async ({ page }) => {
      // 세션이 있으므로 EmptyState는 숨겨져 있어야 함
      await expect(page.getByText('아직 대화가 없습니다')).not.toBeVisible();
    });
  });

  test.describe('모바일', () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto('/chat');
    });

    test('햄버거 클릭 시 사이드바(Sheet)가 열린다', async ({ page }) => {
      await page.getByRole('button', { name: '대화 목록' }).click();
      await expect(page.getByRole('heading', { name: 'AI 상담' })).toBeVisible();
      await expect(page.getByText('삼성생명 암보험 면책기간')).toBeVisible();
    });
  });
});
