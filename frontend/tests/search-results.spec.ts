import { test, expect } from '@playwright/test';

test.describe('검색 결과 화면', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/search?q=암보험');
  });

  test('검색어가 URL에서 검색바에 표시된다', async ({ page }) => {
    const searchInput = page.getByLabel('약관 검색');
    await expect(searchInput).toHaveValue('암보험');
  });

  test('검색 결과 카운트가 표시된다', async ({ page }) => {
    await expect(page.getByRole('status')).toContainText('건의 약관 조문이 검색되었습니다');
  });

  test('검색 결과 카드가 렌더링된다 (보험사, 상품명, 조문 제목)', async ({ page }) => {
    await expect(page.getByText('삼성생명')).toBeVisible();
    await expect(page.getByText('무배당 삼성 암보험')).toBeVisible();
    await expect(page.getByText('제5조 보장 제외')).toBeVisible();
  });

  test('키워드 하이라이트 (mark 태그)가 동작한다', async ({ page }) => {
    const marks = page.locator('mark');
    await expect(marks.first()).toBeVisible();
  });

  test('관련도 퍼센트가 표시된다', async ({ page }) => {
    await expect(page.getByText('관련도 92%')).toBeVisible();
  });

  test('보충 데이터 뱃지가 표시된다', async ({ page }) => {
    await expect(page.getByText('환급금').first()).toBeVisible();
    await expect(page.getByText('보험료').first()).toBeVisible();
  });

  test('카드 클릭 시 약관 상세로 이동한다', async ({ page }) => {
    await page.getByText('제5조 보장 제외').click();
    await expect(page).toHaveURL(/\/policies\/art-001/);
  });

  test('AI 질문하기 버튼이 표시된다', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'AI에게 질문하기' })).toBeVisible();
  });

  test('데스크톱 뷰포트에서 2컬럼 그리드로 표시된다', async ({ page }) => {
    await page.setViewportSize({ width: 1200, height: 800 });
    await page.goto('/search?q=암보험');
    const grid = page.locator('.grid');
    await expect(grid).toHaveClass(/desktop:grid-cols-2/);
  });
});
