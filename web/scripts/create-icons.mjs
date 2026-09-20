/**
 * Generates the PWA manifest icons (church on an ink background), matching the
 * Android launcher icon (app/src/main/res/drawable/ic_launcher_*.xml).
 * Run: node scripts/create-icons.mjs   (needs Playwright's Chromium, which the
 * e2e tests already use)
 * Output: assets/icons/icon-192.png and icon-512.png
 *
 * If you change the church shape, change it in the Android drawables too.
 */

import { mkdirSync } from 'fs';
import { chromium } from '@playwright/test';

const CHURCH =
  'M54,31 L62,50 L62,62 L76,70 L76,84 L32,84 L32,70 L46,62 L46,50 Z ' +
  'M53,21 L55,21 L55,24 L57,24 L57,26 L55,26 L55,31 L53,31 L53,26 L51,26 L51,24 L53,24 Z ' +
  'M53,56 L55,56 L55,65 L53,65 Z ' +
  'M51,84 L51,76 Q54,72 57,76 L57,84 Z ' +
  'M37,74 L39.6,74 L39.6,80 L37,80 Z M68.4,74 L71,74 L71,80 L68.4,80 Z';
const PLINTH = 'M28,84 L80,84 L80,88 L28,88 Z';

// Full-bleed square (the manifest marks the icon "maskable"), church kept
// inside the central safe zone.
const svg = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="100%" height="100%">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#241A10"/><stop offset="1" stop-color="#160F09"/>
    </linearGradient>
    <linearGradient id="gold" x1="32" y1="0" x2="76" y2="0" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#F0C572"/><stop offset="0.55" stop-color="#C8922A"/>
      <stop offset="1" stop-color="#A2701D"/>
    </linearGradient>
  </defs>
  <rect width="108" height="108" fill="url(#bg)"/>
  <g transform="translate(54 56) scale(1.1) translate(-54 -56)">
    <path fill="url(#gold)" fill-rule="evenodd" d="${CHURCH}"/>
    <path fill="#B4801F" d="${PLINTH}"/>
  </g>
</svg>`;

mkdirSync('assets/icons', { recursive: true });
const browser = await chromium.launch();
for (const size of [192, 512]) {
  const page = await browser.newPage({ viewport: { width: size, height: size } });
  await page.setContent(
    `<html><body style="margin:0;background:#160F09">${svg}</body></html>`,
  );
  await page.screenshot({ path: `assets/icons/icon-${size}.png` });
  console.log(`✓ assets/icons/icon-${size}.png`);
  await page.close();
}
await browser.close();
