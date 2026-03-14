import { test, expect } from '@playwright/test';
import { mockAuthenticatedUser } from './fixtures';

test.describe('피드백 카테고리 UI', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedUser(page);
    // 기존 세션의 메시지를 로드하기 위해 세션 클릭
    await page.goto('/');
    await page.getByRole('complementary').getByRole('heading', { name: '삼성생명 암보험 면책기간' }).click();
    // AI 메시지가 렌더링될 때까지 대기
    await expect(page.getByText('면책기간은 계약일로부터')).toBeVisible();
  });

  test('👍 클릭 시 활성화되고 API 호출됨', async ({ page }) => {
    const putRequest = page.waitForRequest(
      (req) => req.url().includes('/feedback') && req.method() === 'PUT',
    );

    const thumbsUp = page.getByLabel('좋아요').first();
    await thumbsUp.click();

    const req = await putRequest;
    const body = req.postDataJSON();
    expect(body.feedbackType).toBe('POSITIVE');
    await expect(thumbsUp).toHaveAttribute('aria-pressed', 'true');
  });

  test('👍 재클릭 시 해제되고 DELETE API 호출됨', async ({ page }) => {
    const thumbsUp = page.getByLabel('좋아요').first();

    // 먼저 활성화
    await thumbsUp.click();
    await expect(thumbsUp).toHaveAttribute('aria-pressed', 'true');

    // 재클릭으로 해제
    const deleteRequest = page.waitForRequest(
      (req) => req.url().includes('/feedback') && req.method() === 'DELETE',
    );
    await thumbsUp.click();
    await deleteRequest;
    await expect(thumbsUp).toHaveAttribute('aria-pressed', 'false');
  });

  test('👎 클릭 시 카테고리 Popover 표시', async ({ page }) => {
    const thumbsDown = page.getByLabel('싫어요').first();
    await thumbsDown.click();

    await expect(page.getByText('어떤 점이 불만족스러우셨나요?')).toBeVisible();
    await expect(page.getByText('정보가 부정확해요')).toBeVisible();
    await expect(page.getByText('질문을 이해하지 못했어요')).toBeVisible();
    await expect(page.getByText('정보가 부족해요')).toBeVisible();
    await expect(page.getByText('출처가 잘못되었어요')).toBeVisible();
    await expect(page.getByText('기타', { exact: true })).toBeVisible();
  });

  test('카테고리 선택 + 제출 시 Popover 닫히고 👎 활성화', async ({ page }) => {
    const thumbsDown = page.getByLabel('싫어요').first();
    await thumbsDown.click();

    // 카테고리 선택
    await page.getByText('정보가 부정확해요').click();

    // 제출
    const putRequest = page.waitForRequest(
      (req) => req.url().includes('/feedback') && req.method() === 'PUT',
    );
    await page.getByRole('button', { name: '제출' }).click();

    const req = await putRequest;
    const body = req.postDataJSON();
    expect(body.feedbackType).toBe('NEGATIVE');
    expect(body.category).toBe('INACCURATE');

    // Popover 닫힘
    await expect(page.getByText('어떤 점이 불만족스러우셨나요?')).not.toBeVisible();
    // 👎 활성화
    await expect(thumbsDown).toHaveAttribute('aria-pressed', 'true');
  });

  test('OTHER 선택 시 textarea 표시 + 입력 후 제출', async ({ page }) => {
    const thumbsDown = page.getByLabel('싫어요').first();
    await thumbsDown.click();

    // 기타 선택
    await page.getByText('기타', { exact: true }).click();

    // textarea 표시 확인
    const textarea = page.getByPlaceholder('의견을 입력해주세요...');
    await expect(textarea).toBeVisible();

    // 의견 입력
    await textarea.fill('응답이 너무 길어요');

    // 제출
    const putRequest = page.waitForRequest(
      (req) => req.url().includes('/feedback') && req.method() === 'PUT',
    );
    await page.getByRole('button', { name: '제출' }).click();

    const req = await putRequest;
    const body = req.postDataJSON();
    expect(body.feedbackType).toBe('NEGATIVE');
    expect(body.category).toBe('OTHER');
    expect(body.comment).toBe('응답이 너무 길어요');
  });

  test('카테고리 미선택 시 제출 버튼 비활성화', async ({ page }) => {
    const thumbsDown = page.getByLabel('싫어요').first();
    await thumbsDown.click();

    const submitButton = page.getByRole('button', { name: '제출' });
    await expect(submitButton).toBeDisabled();
  });

  test('👎 재클릭 시 피드백 해제', async ({ page }) => {
    const thumbsDown = page.getByLabel('싫어요').first();

    // 먼저 부정 피드백 제출
    await thumbsDown.click();
    await page.getByText('정보가 부족해요').click();
    await page.getByRole('button', { name: '제출' }).click();
    await expect(thumbsDown).toHaveAttribute('aria-pressed', 'true');

    // 재클릭으로 해제
    const deleteRequest = page.waitForRequest(
      (req) => req.url().includes('/feedback') && req.method() === 'DELETE',
    );
    await thumbsDown.click();
    await deleteRequest;
    await expect(thumbsDown).toHaveAttribute('aria-pressed', 'false');
  });

  test('👍 → 👎 전환 시 기존 피드백 교체', async ({ page }) => {
    const thumbsUp = page.getByLabel('좋아요').first();
    const thumbsDown = page.getByLabel('싫어요').first();

    // 먼저 👍 클릭
    await thumbsUp.click();
    await expect(thumbsUp).toHaveAttribute('aria-pressed', 'true');

    // 👎 클릭 → Popover 열림
    await thumbsDown.click();
    await expect(page.getByText('어떤 점이 불만족스러우셨나요?')).toBeVisible();

    // 카테고리 선택 + 제출
    await page.getByText('출처가 잘못되었어요').click();
    const putRequest = page.waitForRequest(
      (req) => req.url().includes('/feedback') && req.method() === 'PUT',
    );
    await page.getByRole('button', { name: '제출' }).click();

    const req = await putRequest;
    const body = req.postDataJSON();
    expect(body.feedbackType).toBe('NEGATIVE');
    expect(body.category).toBe('WRONG_SOURCE');

    // 👍 해제, 👎 활성화
    await expect(thumbsUp).toHaveAttribute('aria-pressed', 'false');
    await expect(thumbsDown).toHaveAttribute('aria-pressed', 'true');
  });
});
