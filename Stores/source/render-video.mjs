// Exporte video.html en MP4 (1920 x 1080, 30 i/s) avec sa musique chiptune (music.py).
// Usage (depuis la racine du dépôt) : node Stores/source/render-video.mjs [fr|en]   — nécessite ffmpeg et python3.
// Sans argument, les deux langues : français dans Stores/assets/video, anglais dans Stores/assets/en/video.
import { chromium } from 'playwright';
import { spawn, execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const work = join(here, 'frames');
mkdirSync(work, { recursive: true });
const FPS = 30;

const browser = await chromium.launch();
for (const lang of process.argv[2] ? [process.argv[2]] : ['fr', 'en']) await render(lang);
await browser.close();

async function render(lang) {
  const outDir = resolve(here, lang === 'fr' ? '../assets/video' : `../assets/${lang}/video`);
  mkdirSync(outDir, { recursive: true });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  await page.goto(pathToFileURL(join(here, 'video.html')).href + `?render&lang=${lang}`);
  await page.evaluate(() => window.ready);
  const { duration, cues } = await page.evaluate(() => ({ duration: window.DURATION, cues: window.CUES }));

  // Musique et bruitages calés sur les cues de la vidéo.
  const cuesFile = join(work, 'cues.json'), wav = join(work, 'music.wav');
  writeFileSync(cuesFile, JSON.stringify({ duration, cues }));
  execFileSync('python3', ['-I', join(here, 'music.py'), cuesFile, wav], { stdio: 'inherit' });

  const mp4 = join(outDir, 'agentchi-companion.mp4');
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-', '-i', wav,
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '160k', '-shortest', '-movflags', '+faststart', mp4],
    { stdio: ['pipe', 'inherit', 'inherit'] });

  const total = Math.round(duration * FPS);
  for (let f = 0; f < total; f++) {
    const url = await page.evaluate(t => { window.frame(t); return document.getElementById('c').toDataURL('image/jpeg', 0.95); }, f / FPS);
    const buf = Buffer.from(url.split(',')[1], 'base64');
    if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
    if (f % 150 === 0) console.log(`image ${f}/${total}`);
  }
  ff.stdin.end();
  await new Promise((r, j) => ff.on('close', c => (c ? j(new Error('ffmpeg ' + c)) : r())));

  // Vignette (poster) pour YouTube et le Galaxy Store.
  const poster = await page.evaluate(() => { window.frame(26.5); return document.getElementById('c').toDataURL('image/png'); });
  writeFileSync(join(outDir, 'poster.png'), Buffer.from(poster.split(',')[1], 'base64'));
  await page.close();
  console.log('✓', mp4);
}
