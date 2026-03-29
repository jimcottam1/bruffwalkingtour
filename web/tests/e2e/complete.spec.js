import { test, expect } from '@playwright/test';
import { seedTourState, COMPLETE_STATE } from './helpers.js';

test.describe('complete.html — tour completion page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/complete.html');
  });

  test('shows "Tour Complete!" heading', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Tour Complete!' })).toBeVisible();
  });

  test('subtitle mentions all 4 sites', async ({ page }) => {
    await expect(page.locator('.completion-sub')).toContainText('4');
  });

  test('stat bar shows 4 sites visited', async ({ page }) => {
    await expect(page.locator('.stat-box').first().locator('.stat-value')).toHaveText('4');
  });

  test('visited list contains exactly 4 items', async ({ page }) => {
    await expect(page.locator('.completed-item')).toHaveCount(4);
  });

  test('visited list item 1 is Thomas Fitzgerald Centre', async ({ page }) => {
    await expect(page.locator('.completed-item').nth(0)).toContainText('Thomas Fitzgerald Centre');
  });

  test('visited list item 2 is Saints Peter and Paul Church', async ({ page }) => {
    await expect(page.locator('.completed-item').nth(1)).toContainText('Peter');
  });

  test('visited list item 3 is Sean Wall Monument', async ({ page }) => {
    await expect(page.locator('.completed-item').nth(2)).toContainText('Sean Wall');
  });

  test('visited list item 4 is Bruff GAA Grounds', async ({ page }) => {
    await expect(page.locator('.completed-item').nth(3)).toContainText('GAA');
  });

  test('all visited items show a checkmark', async ({ page }) => {
    const checks = page.locator('.check');
    await expect(checks).toHaveCount(4);
    for (let i = 0; i < 4; i++) {
      await expect(checks.nth(i)).toBeVisible();
    }
  });

  test('restart button is visible', async ({ page }) => {
    await expect(page.locator('#restart-btn')).toBeVisible();
    await expect(page.locator('#restart-btn')).toContainText('Take Tour Again');
  });

  test('share button is hidden when navigator.share is unavailable', async ({ page }) => {
    // Suppress navigator.share so initCompletePage() leaves the button hidden
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'share', { value: undefined, configurable: true, writable: true });
    });
    await page.reload();
    await expect(page.locator('#share-btn')).toBeHidden();
  });

  test('share button is visible when navigator.share is mocked', async ({ page }) => {
    // Must inject before navigation
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'share', { value: async () => {}, configurable: true });
    });
    await page.reload();
    await expect(page.locator('#share-btn')).toBeVisible();
  });

  test('clicking restart navigates to index.html', async ({ page }) => {
    await page.locator('#restart-btn').click();
    await expect(page).toHaveURL(/index\.html/);
  });

  test('clicking restart resets tour state to index 0', async ({ page }) => {
    // Seed a complete state first
    await seedTourState(page, COMPLETE_STATE);
    await page.reload();
    await page.locator('#restart-btn').click();
    await page.waitForURL(/index\.html/);
    const state = await page.evaluate(() =>
      JSON.parse(sessionStorage.getItem('bruff_tour_state'))
    );
    expect(state.currentIndex).toBe(0);
    expect(state.visitedIds).toHaveLength(0);
  });
});
