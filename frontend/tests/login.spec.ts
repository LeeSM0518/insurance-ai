import { test, expect } from '@playwright/test';

test.describe('로그인 화면', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('서비스 로고와 이름이 표시된다', async ({ page }) => {
    await expect(page.getByText('Insurance AI')).toBeVisible();
    await expect(
      page.getByText('전 보험사 약관 AI 검색 서비스'),
    ).toBeVisible();
  });

  test('카카오 로그인 버튼이 표시된다', async ({ page }) => {
    const kakaoButton = page.getByRole('button', {
      name: '카카오 계정으로 로그인',
    });
    await expect(kakaoButton).toBeVisible();
    await expect(kakaoButton).toContainText('카카오로 시작하기');
  });

  test('카카오 로그인 버튼이 터치 타겟 크기(48px)를 충족한다', async ({
    page,
  }) => {
    const kakaoButton = page.getByRole('button', {
      name: '카카오 계정으로 로그인',
    });
    const box = await kakaoButton.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.height).toBeGreaterThanOrEqual(48);
  });

  test('약관 링크가 표시된다', async ({ page }) => {
    await expect(page.getByText('서비스 이용약관')).toBeVisible();
    await expect(page.getByText('개인정보처리방침')).toBeVisible();
  });
});

test.describe('404 화면', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/nonexistent');
  });

  test('안내 아이콘과 메시지가 표시된다', async ({ page }) => {
    await expect(page.getByText('404')).toBeVisible();
    await expect(page.getByText('페이지를 찾을 수 없습니다')).toBeVisible();
    await expect(
      page.getByText('주소가 올바른지 확인하거나'),
    ).toBeVisible();
  });

  test('메인으로 이동 버튼 클릭 시 비인증 상태에서는 /login으로 이동한다', async ({
    page,
  }) => {
    await page.getByRole('button', { name: '메인으로 이동' }).click();
    // 비인증 상태이므로 ProtectedRoute에 의해 /login으로 리다이렉트
    await expect(page).toHaveURL('/login');
  });
});
