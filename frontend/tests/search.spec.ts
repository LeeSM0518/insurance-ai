import { test, expect } from '@playwright/test';

test.describe('메인/검색 화면', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/search');
  });

  test('기본 렌더링: 히어로 텍스트, 검색바, 필터, 추천 검색어가 표시된다', async ({ page }) => {
    await expect(page.getByText('전 보험사 약관을 AI로 검색하세요')).toBeVisible();
    await expect(page.getByRole('search')).toBeVisible();
    await expect(page.getByText('필터')).toBeVisible();
    await expect(page.getByText('추천 검색어')).toBeVisible();
  });

  test('검색바에 자동 포커스된다', async ({ page }) => {
    const searchInput = page.getByLabel('약관 검색');
    await expect(searchInput).toBeFocused();
  });

  test('검색어 입력 후 Enter로 검색 페이지로 이동한다', async ({ page }) => {
    await page.getByLabel('약관 검색').fill('암보험 면책기간');
    await page.getByLabel('약관 검색').press('Enter');
    await expect(page).toHaveURL(/\/search\/results\?q=%EC%95%94%EB%B3%B4%ED%97%98/);
  });

  test('검색 버튼 클릭으로 검색 페이지로 이동한다', async ({ page }) => {
    await page.getByLabel('약관 검색').fill('실손보험');
    await page.getByRole('button', { name: '검색' }).click();
    await expect(page).toHaveURL(/\/search\/results\?q=/);
  });

  test('빈 검색어 제출 시 이동하지 않는다', async ({ page }) => {
    await page.getByLabel('약관 검색').press('Enter');
    await expect(page).toHaveURL('/search');
    await page.getByRole('button', { name: '검색' }).click();
    await expect(page).toHaveURL('/search');
  });

  test('AI에게 질문하기 클릭 시 (빈 검색바) /chat으로 이동한다', async ({ page }) => {
    await page.getByText('AI에게 바로 질문하기').click();
    await expect(page).toHaveURL('/chat');
  });

  test('AI에게 질문하기 클릭 시 (텍스트 있음) /chat?q=...로 이동한다', async ({ page }) => {
    await page.getByLabel('약관 검색').fill('암보험');
    await page.getByText('AI에게 바로 질문하기').click();
    await expect(page).toHaveURL(/\/chat\?q=%EC%95%94%EB%B3%B4%ED%97%98/);
  });

  test('필터 칩 토글 시 선택/해제 시각적 변경이 있다', async ({ page }) => {
    const chip = page.getByRole('checkbox', { name: '삼성생명' });
    await expect(chip).toHaveAttribute('aria-checked', 'false');
    await chip.click();
    await expect(chip).toHaveAttribute('aria-checked', 'true');
    await chip.click();
    await expect(chip).toHaveAttribute('aria-checked', 'false');
  });

  test('최근 검색어가 localStorage에서 표시된다', async ({ page }) => {
    // localStorage에 검색어 세팅
    await page.evaluate(() => {
      localStorage.setItem('recent-searches', JSON.stringify(['테스트 검색어', '실손보험']));
    });
    await page.reload();
    await expect(page.getByText('최근 검색')).toBeVisible();
    await expect(page.getByText('테스트 검색어')).toBeVisible();
    await expect(page.getByText('실손보험')).toBeVisible();
  });

  test('최근 검색어 삭제 시 해당 칩이 제거된다', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.setItem('recent-searches', JSON.stringify(['삭제대상', '유지대상']));
    });
    await page.reload();
    await page.getByLabel("검색어 '삭제대상' 삭제").click();
    await expect(page.getByText('삭제대상')).not.toBeVisible();
    await expect(page.getByText('유지대상')).toBeVisible();
  });

  test('최근 검색어 클릭 시 해당 키워드로 검색 이동한다', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.setItem('recent-searches', JSON.stringify(['클릭검색어']));
    });
    await page.reload();
    await page.getByText('클릭검색어').click();
    await expect(page).toHaveURL(/\/search\/results\?q=/);
  });

  test('추천 검색어 클릭 시 해당 키워드로 검색 이동한다', async ({ page }) => {
    await page.getByText('#암보험 면책기간').click();
    await expect(page).toHaveURL(/\/search\/results\?q=/);
  });

  test('최근 검색 없을 때 최근 검색 섹션이 숨겨진다', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.removeItem('recent-searches');
    });
    await page.reload();
    await expect(page.getByText('최근 검색')).not.toBeVisible();
  });
});
