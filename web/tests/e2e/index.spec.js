import { test, expect } from '@playwright/test';

test.describe('index.html — intro page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/index.html');
  });

  test('page title is "Bruff Heritage Trail"', async ({ page }) => {
    await expect(page).toHaveTitle('Bruff Heritage Trail');
  });

  test('shows the app heading', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Bruff Heritage Trail' })).toBeVisible();
  });

  test('subtitle shows Co. Limerick', async ({ page }) => {
    await expect(page.locator('.app-subtitle')).toContainText('Limerick');
  });

  test('stat bar shows 90 minutes', async ({ page }) => {
    const boxes = page.locator('.stat-box');
    await expect(boxes.first().locator('.stat-value')).toHaveText('90');
    await expect(boxes.first().locator('.stat-label')).toContainText('Minutes');
  });

  test('stat bar shows 4 locations', async ({ page }) => {
    const boxes = page.locator('.stat-box');
    await expect(boxes.nth(1).locator('.stat-value')).toHaveText('4');
  });

  test('stat bar shows Easy difficulty', async ({ page }) => {
    const boxes = page.locator('.stat-box');
    await expect(boxes.nth(2).locator('.stat-value')).toContainText('Easy');
  });

  test('lists exactly 4 tour highlights', async ({ page }) => {
    await expect(page.locator('.highlight-item')).toHaveCount(4);
  });

  test('highlight 1 is Thomas Fitzgerald Centre', async ({ page }) => {
    await expect(page.locator('.highlight-item').nth(0)).toContainText('Thomas Fitzgerald Centre');
  });

  test('highlight 2 is Saints Peter & Paul Church', async ({ page }) => {
    await expect(page.locator('.highlight-item').nth(1)).toContainText('Peter');
  });

  test('highlight 3 is Sean Wall Monument', async ({ page }) => {
    await expect(page.locator('.highlight-item').nth(2)).toContainText('Sean Wall');
  });

  test('highlight 4 is Bruff GAA Grounds', async ({ page }) => {
    await expect(page.locator('.highlight-item').nth(3)).toContainText('GAA');
  });

  test('start button links to tour.html', async ({ page }) => {
    await expect(page.locator('#start-btn')).toHaveAttribute('href', 'tour.html');
  });

  test('clicking Start clears bruff_tour_state from sessionStorage', async ({ page }) => {
    // Pre-seed a saved state
    await page.evaluate(() =>
      sessionStorage.setItem('bruff_tour_state', JSON.stringify({ currentIndex: 2 }))
    );
    // Dispatch the click and read sessionStorage in the same evaluate so we
    // capture the value synchronously before the ensuing navigation tears down
    // the execution context.
    const state = await page.evaluate(() => {
      document.getElementById('start-btn').dispatchEvent(new MouseEvent('click', { bubbles: true }));
      return sessionStorage.getItem('bruff_tour_state');
    });
    expect(state).toBeNull();
  });
});
