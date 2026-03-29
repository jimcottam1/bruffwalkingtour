import { test, expect } from '@playwright/test';
import { stubMapModule, injectGeoMock, seedTourState, COMPLETE_STATE } from './helpers.js';

// GPS coordinates used in tests
const INSIDE_BOUNDARY  = { lat: 52.481000, lon: -8.548776 }; // ~260 m north of TF, inside boundary
const OUTSIDE_BOUNDARY = { lat: 52.495871, lon: -8.548776 }; // 2 km north, outside boundary
const AT_TF_CENTRE     = { lat: 52.478869, lon: -8.548776 }; // 20 m from TF Centre (within 25 m)

test.describe('tour.html — map/navigation page', () => {
  test.beforeEach(async ({ page }) => {
    await stubMapModule(page);
    await injectGeoMock(page);
  });

  test('shows "Locating you" before first GPS fix', async ({ page }) => {
    await page.goto('/tour.html');
    await expect(page.locator('#navigation-instruction')).toContainText('Locating');
  });

  test('boundary warning is hidden on load', async ({ page }) => {
    await page.goto('/tour.html');
    await expect(page.locator('#boundary-warning')).toBeHidden();
  });

  test('arrival banner is hidden on load', async ({ page }) => {
    await page.goto('/tour.html');
    await expect(page.locator('#arrival-banner')).toBeHidden();
  });

  test('navigation instruction updates when GPS fix arrives (inside boundary)', async ({ page }) => {
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), INSIDE_BOUNDARY);
    await expect(page.locator('#navigation-instruction')).toContainText('Thomas Fitzgerald Centre');
  });

  test('distance info shows metres after GPS fix', async ({ page }) => {
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), INSIDE_BOUNDARY);
    await expect(page.locator('#distance-info')).toContainText('m');
  });

  test('boundary warning appears when GPS is outside tour area', async ({ page }) => {
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), OUTSIDE_BOUNDARY);
    await expect(page.locator('#boundary-warning')).toBeVisible();
    await expect(page.locator('#boundary-warning')).toContainText('Bruff');
  });

  test('boundary warning hides when user returns inside tour area', async ({ page }) => {
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), OUTSIDE_BOUNDARY);
    await expect(page.locator('#boundary-warning')).toBeVisible();
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), INSIDE_BOUNDARY);
    await expect(page.locator('#boundary-warning')).toBeHidden();
  });

  test('arrival banner appears when within 25 m of Thomas Fitzgerald Centre', async ({ page }) => {
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), AT_TF_CENTRE);
    await expect(page.locator('#arrival-banner')).toBeVisible();
  });

  test('arrival link names the waypoint and links to detail page', async ({ page }) => {
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), AT_TF_CENTRE);
    const link = page.locator('#arrival-link');
    await expect(link).toContainText('Thomas Fitzgerald Centre');
    await expect(link).toHaveAttribute('href', 'detail.html?id=thomas_fitzgerald_centre');
  });

  test('arrival banner only fires once (idempotent on repeated GPS fixes)', async ({ page }) => {
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), AT_TF_CENTRE);
    await expect(page.locator('#arrival-banner')).toBeVisible();

    // Hide the banner manually (simulates the 15 s timeout)
    await page.evaluate(() => { document.getElementById('arrival-banner').hidden = true; });
    await expect(page.locator('#arrival-banner')).toBeHidden();

    // Fire the same GPS fix again — banner must NOT reappear
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), AT_TF_CENTRE);
    await expect(page.locator('#arrival-banner')).toBeHidden();
  });

  test('redirects to complete.html when tour state is already complete', async ({ page }) => {
    await seedTourState(page, COMPLETE_STATE);
    await page.goto('/tour.html');
    await expect(page).toHaveURL(/complete\.html/);
  });
});
