import { test, expect } from '@playwright/test';
import { mockAuthenticatedUser } from './fixtures';

test.describe('약관 상세 화면', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.goto('/policies/art-001');
  });

  test('약관 메타 정보가 표시된다 (보험사, 상품명, 내용유형)', async ({ page }) => {
    await expect(page.getByText('삼성생명')).toBeVisible();
    await expect(page.getByText('무배당 삼성 암보험')).toBeVisible();
    await expect(page.getByText('면책사항')).toBeVisible();
  });

  test('조문 본문이 표시된다', async ({ page }) => {
    await expect(page.getByText('보장개시일은 계약일로부터')).toBeVisible();
  });

  test('보충 정보 아코디언이 표시된다', async ({ page }) => {
    await expect(page.getByText('보충 정보')).toBeVisible();
    await expect(page.getByText('해약환급금 예시')).toBeVisible();
  });

  test('아코디언 클릭 시 테이블이 펼쳐진다', async ({ page }) => {
    await page.getByText('해약환급금 예시').click();
    // 테이블이 펼쳐지면 경과기간 데이터가 보여야 함
    await expect(page.getByText('12개월')).toBeVisible();
  });

  test('관련 조문 링크가 표시된다', async ({ page }) => {
    await expect(page.getByText('관련 조문')).toBeVisible();
    await expect(page.getByText('제3조 보장 내용')).toBeVisible();
  });

  test('AI 질문하기 버튼이 표시된다', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'AI에게 질문하기' })).toBeVisible();
  });

  test('복사/공유 버튼이 표시된다', async ({ page }) => {
    await expect(page.getByRole('button', { name: '복사' })).toBeVisible();
    await expect(page.getByRole('button', { name: '공유' })).toBeVisible();
  });

  test('데스크톱에서 PDF 열기 시 2컬럼 레이아웃이 적용된다', async ({ page }) => {
    await page.setViewportSize({ width: 1200, height: 800 });
    await page.goto('/policies/art-001');
    // PDF 열기 전에는 aside가 없음
    await expect(page.locator('aside')).not.toBeVisible();
    // PDF 열면 aside 사이드패널 표시
    await page.getByRole('button', { name: '원문 PDF 보기' }).click();
    await expect(page.locator('aside')).toBeVisible();
  });

  test('hasPdfSource=true일 때 "원문 PDF 보기" 버튼이 표시된다', async ({ page }) => {
    await page.setViewportSize({ width: 1200, height: 800 });
    await page.goto('/policies/art-001');
    await expect(page.getByRole('button', { name: '원문 PDF 보기' })).toBeVisible();
  });

  test('"원문 PDF 보기" 버튼 클릭 시 사이드바에 PDF가 표시된다', async ({ page }) => {
    await page.setViewportSize({ width: 1200, height: 800 });
    await page.goto('/policies/art-001');
    await page.getByRole('button', { name: '원문 PDF 보기' }).click();
    // 사이드바에 "원본 PDF" 헤더와 닫기 버튼이 표시됨
    await expect(page.getByText('원본 PDF')).toBeVisible();
    await expect(page.getByRole('button', { name: 'PDF 닫기', exact: true })).toBeVisible();
  });

  test('"원문 PDF 닫기" 버튼 클릭 시 사이드바가 사라진다', async ({ page }) => {
    await page.setViewportSize({ width: 1200, height: 800 });
    await page.goto('/policies/art-001');
    // PDF 열기
    await page.getByRole('button', { name: '원문 PDF 보기' }).click();
    await expect(page.getByText('원본 PDF')).toBeVisible();
    // PDF 닫기 (사이드바 X 버튼)
    await page.getByRole('button', { name: 'PDF 닫기', exact: true }).click();
    // aside가 사라짐
    await expect(page.locator('aside')).not.toBeVisible();
  });

  test('조문 내용은 PDF 열림 여부와 관계없이 항상 표시된다', async ({ page }) => {
    await page.setViewportSize({ width: 1200, height: 800 });
    await page.goto('/policies/art-001');
    await expect(page.getByText('보장개시일은 계약일로부터')).toBeVisible();
    // PDF 열어도 조문 내용은 계속 표시
    await page.getByRole('button', { name: '원문 PDF 보기' }).click();
    await expect(page.getByText('보장개시일은 계약일로부터')).toBeVisible();
  });

  test('모바일에서 PDF 버튼 클릭 시 바텀시트가 표시된다', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/policies/art-001');
    await page.getByRole('button', { name: '원문 PDF 보기' }).click();
    await expect(page.getByText('원본 PDF 열기')).toBeVisible();
  });
});
