import { test, expect } from '@playwright/test';
import { stubMapModule, injectGeoMock, startTourAt, seedTourState, COMPLETE_STATE } from './helpers.js';

// GPS coordinates used in tests
const INSIDE_BOUNDARY  = { lat: 52.481000, lon: -8.548776 }; // ~260 m north of TF, inside boundary
const OUTSIDE_BOUNDARY = { lat: 52.495871, lon: -8.548776 }; // 2 km north, outside boundary
const AT_TF_CENTRE     = { lat: 52.478869, lon: -8.548776 }; // 20 m from TF Centre (within 25 m)

test.describe('tour.html — boundary gate', () => {
  test.beforeEach(async ({ page }) => {
    await stubMapModule(page);
    await injectGeoMock(page);
  });

  test('shows "Locating you" before first GPS fix', async ({ page }) => {
    await page.goto('/tour.html');
    await expect(page.locator('#gate-status')).toContainText('Locating');
  });

  test('gate is visible on load, hiding the map/nav underneath', async ({ page }) => {
    await page.goto('/tour.html');
    await expect(page.locator('#boundary-gate')).toBeVisible();
  });

  test('Start Tour button is hidden until a fix lands inside the boundary', async ({ page }) => {
    await page.goto('/tour.html');
    await expect(page.locator('#start-tour-btn')).toBeHidden();
  });

  test('GPS fix outside the boundary shows the outside message and distance, no Start button', async ({ page }) => {
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), OUTSIDE_BOUNDARY);
    await expect(page.locator('#gate-status')).toContainText('outside Bruff');
    await expect(page.locator('#gate-distance')).toContainText('away from Bruff town centre');
    await expect(page.locator('#start-tour-btn')).toBeHidden();
  });

  test('automatically returns to index.html after a few seconds if still outside the boundary', async ({ page }) => {
    await page.clock.install();
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), OUTSIDE_BOUNDARY);
    await expect(page.locator('#gate-status')).toContainText('outside Bruff');

    await page.clock.fastForward(6000);
    await expect(page).toHaveURL(/index\.html/);
  });

  test('does not auto-return if the user moves inside the boundary before the timeout', async ({ page }) => {
    await page.clock.install();
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), OUTSIDE_BOUNDARY);
    await expect(page.locator('#gate-status')).toContainText('outside Bruff');

    // Comes back inside before the auto-return would fire — timer must be cancelled
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), INSIDE_BOUNDARY);
    await expect(page.locator('#start-tour-btn')).toBeVisible();

    await page.clock.fastForward(6000);
    await expect(page).toHaveURL(/tour\.html/);
  });

  test('GPS fix inside the boundary shows the ready message and Start button', async ({ page }) => {
    await page.goto('/tour.html');
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), INSIDE_BOUNDARY);
    await expect(page.locator('#gate-status')).toContainText("You're in Bruff");
    await expect(page.locator('#start-tour-btn')).toBeVisible();
  });

  test('clicking Start Tour dismisses the gate', async ({ page }) => {
    await page.goto('/tour.html');
    await startTourAt(page, INSIDE_BOUNDARY);
    await expect(page.locator('#boundary-gate')).toBeHidden();
  });

  test('redirects to complete.html when tour state is already complete (before the gate ever shows)', async ({ page }) => {
    await seedTourState(page, COMPLETE_STATE);
    await page.goto('/tour.html');
    await expect(page).toHaveURL(/complete\.html/);
  });
});

test.describe('tour.html — map/navigation page (after Start Tour)', () => {
  test.beforeEach(async ({ page }) => {
    await stubMapModule(page);
    await injectGeoMock(page);
  });

  test('boundary warning is hidden on load', async ({ page }) => {
    await page.goto('/tour.html');
    await expect(page.locator('#boundary-warning')).toBeHidden();
  });

  test('arrival banner is hidden on load', async ({ page }) => {
    await page.goto('/tour.html');
    await expect(page.locator('#arrival-banner')).toBeHidden();
  });

  test('navigation instruction renders immediately from the fix that started the tour', async ({ page }) => {
    await page.goto('/tour.html');
    await startTourAt(page, INSIDE_BOUNDARY);
    await expect(page.locator('#navigation-instruction')).toContainText('Thomas Fitzgerald Centre');
  });

  test('distance info shows metres after starting the tour', async ({ page }) => {
    await page.goto('/tour.html');
    await startTourAt(page, INSIDE_BOUNDARY);
    await expect(page.locator('#distance-info')).toContainText('m');
  });

  test('boundary warning appears if the user wanders outside during the live tour', async ({ page }) => {
    await page.goto('/tour.html');
    await startTourAt(page, INSIDE_BOUNDARY);
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), OUTSIDE_BOUNDARY);
    await expect(page.locator('#boundary-warning')).toBeVisible();
    await expect(page.locator('#boundary-warning')).toContainText('Bruff');
  });

  test('boundary warning hides when user returns inside tour area', async ({ page }) => {
    await page.goto('/tour.html');
    await startTourAt(page, INSIDE_BOUNDARY);
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), OUTSIDE_BOUNDARY);
    await expect(page.locator('#boundary-warning')).toBeVisible();
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), INSIDE_BOUNDARY);
    await expect(page.locator('#boundary-warning')).toBeHidden();
  });

  test('arrival banner appears when the tour is started within 25 m of Thomas Fitzgerald Centre', async ({ page }) => {
    await page.goto('/tour.html');
    await startTourAt(page, AT_TF_CENTRE);
    await expect(page.locator('#arrival-banner')).toBeVisible();
  });

  test('arrival link names the waypoint and links to detail page', async ({ page }) => {
    await page.goto('/tour.html');
    await startTourAt(page, AT_TF_CENTRE);
    const link = page.locator('#arrival-link');
    await expect(link).toContainText('Thomas Fitzgerald Centre');
    await expect(link).toHaveAttribute('href', 'detail.html?id=thomas_fitzgerald_centre');
  });

  test('arrival banner only fires once (idempotent on repeated GPS fixes)', async ({ page }) => {
    await page.goto('/tour.html');
    await startTourAt(page, AT_TF_CENTRE);
    await expect(page.locator('#arrival-banner')).toBeVisible();

    // Hide the banner manually (simulates the 15 s timeout)
    await page.evaluate(() => { document.getElementById('arrival-banner').hidden = true; });
    await expect(page.locator('#arrival-banner')).toBeHidden();

    // Fire the same GPS fix again — banner must NOT reappear
    await page.evaluate(({ lat, lon }) => window.simulatePosition(lat, lon), AT_TF_CENTRE);
    await expect(page.locator('#arrival-banner')).toBeHidden();
  });
});
