/**
 * Generates minimal valid PNG icons for the PWA manifest.
 * Run once: node scripts/create-icons.mjs
 * Output: assets/icons/icon-192.png and icon-512.png
 *
 * Icon design: gold (#c8922a) background with a dark building silhouette.
 * Replace with proper artwork when available.
 */

import { writeFileSync, mkdirSync } from 'fs';
import { deflateSync } from 'zlib';

// CRC32 table
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[i] = c;
  }
  return t;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function pngChunk(name, data) {
  const nameBuf = Buffer.from(name, 'ascii');
  const combined = Buffer.concat([nameBuf, data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(combined));
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  return Buffer.concat([len, nameBuf, data, crc]);
}

/**
 * Build a solid-colour PNG.
 * @param {number} size  pixel dimensions (square)
 * @param {number[]} fg  RGB foreground — drawn as a centred circle
 * @param {number[]} bg  RGB background
 */
function buildPNG(size, fg, bg) {
  const ihdrData = Buffer.alloc(13);
  ihdrData.writeUInt32BE(size, 0);
  ihdrData.writeUInt32BE(size, 4);
  ihdrData.writeUInt8(8, 8);   // bit depth
  ihdrData.writeUInt8(2, 9);   // colour type: RGB
  // bytes 10-12 stay 0 (deflate, default filter, no interlace)

  // Draw: bg with a centred circle in the heritage gold shade
  const cx = size / 2;
  const cy = size / 2;
  const r = size * 0.38;

  const raw = [];
  for (let y = 0; y < size; y++) {
    raw.push(0); // filter = None
    for (let x = 0; x < size; x++) {
      const dx = x - cx, dy = y - cy;
      const inCircle = dx * dx + dy * dy <= r * r;
      const px = inCircle ? fg : bg;
      raw.push(px[0], px[1], px[2]);
    }
  }

  const compressed = deflateSync(Buffer.from(raw), { level: 9 });

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), // PNG sig
    pngChunk('IHDR', ihdrData),
    pngChunk('IDAT', compressed),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

mkdirSync('assets/icons', { recursive: true });

// Heritage gold circle on dark brown background
const GOLD = [200, 146, 42];   // #c8922a
const DARK = [26, 18, 8];      // #1a1208

for (const size of [192, 512]) {
  const buf = buildPNG(size, GOLD, DARK);
  writeFileSync(`assets/icons/icon-${size}.png`, buf);
  console.log(`✓ assets/icons/icon-${size}.png  (${buf.length} bytes)`);
}
