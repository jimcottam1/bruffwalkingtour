import { test, expect } from '@playwright/test';
import { stubMapModule, injectGeoMock } from './helpers.js';
import { WAYPOINTS } from '../../js/data.js';

/**
 * Full end-to-end walkthrough: start the tour, walk to each of the 4
 * waypoints in turn (simulated via GPS fixes at each waypoint's exact
 * coordinates), confirm the arrival banner names the correct site at each
 * stop, confirm the detail page shows the correct content, and confirm the
 * tour ends on complete.html with all 4 sites listed as visited.
 */
test.describe('full tour walkthrough — visits all 4 waypoints in order', () => {
  test.beforeEach(async ({ page }) => {
    await stubMapModule(page);
    await injectGeoMock(page);
  });

  test('walking to each waypoint shows the correct arrival banner and detail page', async ({ page }) => {
    await page.goto('/tour.html');

    for (let i = 0; i < WAYPOINTS.length; i++) {
      const wp = WAYPOINTS[i];
      const isLast = i === WAYPOINTS.length - 1;

      // Before arrival, navigation instruction should reference this waypoint by name
      await page.evaluate(
        ({ lat, lon }) => window.simulatePosition(lat, lon),
        { lat: wp.latitude + 0.01, lon: wp.longitude }, // ~1.1km away, still inside boundary
      );
      await expect(page.locator('#navigation-instruction')).toContainText(wp.name);

      // Walk right up to the waypoint's exact coordinates — well within its proximityRadius
      await page.evaluate(
        ({ lat, lon }) => window.simulatePosition(lat, lon),
        { lat: wp.latitude, lon: wp.longitude },
      );

      // Arrival banner must name this specific waypoint and link to its detail page
      await expect(page.locator('#arrival-banner')).toBeVisible();
      const link = page.locator('#arrival-link');
      await expect(link).toContainText(wp.name);
      await expect(link).toHaveAttribute('href', `detail.html?id=${wp.id}`);

      // Follow the link to the detail page and confirm it shows the right site
      await link.click();
      await expect(page).toHaveURL(new RegExp(`detail\\.html\\?id=${wp.id}`));
      await expect(page.locator('#waypoint-name')).toContainText(wp.name);

      const continueBtn = page.locator('#continue-btn');
      if (isLast) {
        await expect(continueBtn).toContainText('Complete Your Bruff Adventure');
      } else {
        await expect(continueBtn).toContainText('Continue Your Bruff Journey');
      }

      // Confirm and move on
      await continueBtn.click();
      if (isLast) {
        await expect(page).toHaveURL(/complete\.html/);
      } else {
        // injectGeoMock's addInitScript re-runs automatically on this navigation
        await expect(page).toHaveURL(/tour\.html/);
      }
    }

    // Tour completion page lists all 4 sites as visited, in order
    await expect(page.locator('.completed-item')).toHaveCount(WAYPOINTS.length);
    for (let i = 0; i < WAYPOINTS.length; i++) {
      await expect(page.locator('.completed-item').nth(i)).toContainText(WAYPOINTS[i].name);
    }
  });
});
