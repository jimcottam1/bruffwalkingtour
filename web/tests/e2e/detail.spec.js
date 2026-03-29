import { test, expect } from '@playwright/test';
import { seedTourState, AT_GAA_STATE } from './helpers.js';

test.describe('detail.html — waypoint detail page', () => {

  test('shows "Waypoint not found" when id param is missing', async ({ page }) => {
    await page.goto('/detail.html');
    await expect(page.locator('#waypoint-name')).toContainText('not found');
    await expect(page.locator('#continue-btn')).toBeDisabled();
  });

  test('shows "Waypoint not found" for an unknown id', async ({ page }) => {
    await page.goto('/detail.html?id=fake_waypoint');
    await expect(page.locator('#waypoint-name')).toContainText('not found');
  });

  // ---- Thomas Fitzgerald Centre -------------------------------------------

  test.describe('Thomas Fitzgerald Centre (waypoint 1)', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/detail.html?id=thomas_fitzgerald_centre');
    });

    test('page title includes waypoint name', async ({ page }) => {
      await expect(page).toHaveTitle(/Thomas Fitzgerald/);
    });

    test('waypoint name is shown', async ({ page }) => {
      await expect(page.locator('#waypoint-name')).toContainText('Thomas Fitzgerald Centre');
    });

    test('description is not empty', async ({ page }) => {
      const text = await page.locator('#waypoint-description').innerText();
      expect(text.trim().length).toBeGreaterThan(20);
    });

    test('historical info is not empty', async ({ page }) => {
      const text = await page.locator('#waypoint-history').innerText();
      expect(text.trim().length).toBeGreaterThan(20);
    });

    test('historical info mentions JFK', async ({ page }) => {
      await expect(page.locator('#waypoint-history')).toContainText('Kennedy');
    });

    test('continue button says "Continue Your Bruff Journey" (not last WP)', async ({ page }) => {
      await expect(page.locator('#continue-btn')).toContainText('Continue Your Bruff Journey');
    });

    test('back link points to tour.html', async ({ page }) => {
      await expect(page.locator('.detail-back')).toHaveAttribute('href', 'tour.html');
    });

    test('clicking continue navigates to tour.html', async ({ page }) => {
      await page.locator('#continue-btn').click();
      await expect(page).toHaveURL(/tour\.html/);
    });
  });

  // ---- Church -------------------------------------------------------------

  test.describe("Saints Peter and Paul Church (waypoint 2)", () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/detail.html?id=bruff_catholic_church');
    });

    test('shows church name', async ({ page }) => {
      await expect(page.locator('#waypoint-name')).toContainText('Peter and Paul');
    });

    test('description mentions Gothic Revival', async ({ page }) => {
      await expect(page.locator('#waypoint-description')).toContainText('Gothic');
    });
  });

  // ---- Sean Wall Monument -------------------------------------------------

  test.describe('Sean Wall Monument (waypoint 3)', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/detail.html?id=sean_wall_monument');
    });

    test('shows monument name', async ({ page }) => {
      await expect(page.locator('#waypoint-name')).toContainText('Sean Wall Monument');
    });

    test('historical info mentions 1952', async ({ page }) => {
      await expect(page.locator('#waypoint-history')).toContainText('1952');
    });
  });

  // ---- GAA Grounds — final waypoint ---------------------------------------

  test.describe('Bruff GAA Grounds (waypoint 4 — final)', () => {
    test.beforeEach(async ({ page }) => {
      await seedTourState(page, AT_GAA_STATE);
      await page.goto('/detail.html?id=bruff_gaa_grounds');
    });

    test('shows GAA Grounds name', async ({ page }) => {
      await expect(page.locator('#waypoint-name')).toContainText('GAA Grounds');
    });

    test('continue button says "Complete Your Bruff Adventure" on last WP', async ({ page }) => {
      await expect(page.locator('#continue-btn')).toContainText('Complete Your Bruff Adventure');
    });

    test('clicking complete navigates to complete.html', async ({ page }) => {
      await page.locator('#continue-btn').click();
      await expect(page).toHaveURL(/complete\.html/);
    });

    test('sessionStorage currentIndex is 4 after clicking complete', async ({ page }) => {
      await page.locator('#continue-btn').click();
      await page.waitForURL(/complete\.html/);
      const state = await page.evaluate(() =>
        JSON.parse(sessionStorage.getItem('bruff_tour_state'))
      );
      expect(state.currentIndex).toBe(4);
    });
  });
});
