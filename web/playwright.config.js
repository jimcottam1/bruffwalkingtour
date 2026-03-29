import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 15_000,
  retries: 0,
  use: {
    baseURL: 'http://localhost:3000',
    // Block service workers so cached responses don't interfere between runs
    serviceWorkers: 'block',
    // Grant geolocation permission so browser doesn't reject our mock
    permissions: ['geolocation'],
    // Mobile viewport — this is a mobile-first PWA
    ...devices['Pixel 5'],
  },
  webServer: {
    command: 'npx serve . --listen 3000 --no-clipboard',
    url: 'http://localhost:3000',
    reuseExistingServer: true,
    timeout: 10_000,
  },
});
