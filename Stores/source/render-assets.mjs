// Génère les PNG de Stores/assets à partir de shots.html.
// Usage (depuis la racine du dépôt) : node Stores/source/render-assets.mjs
import { chromium } from 'playwright';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, '../assets');
const res = resolve(here, '../../app/src/main/res/drawable');

function vectorPaths(file) {
  const xml = readFileSync(join(res, file), 'utf8');
  return [...xml.matchAll(/android:fillColor="(#[0-9A-Fa-f]+)"\s+android:pathData="([^"]+)"/g)].map(m => ({ fill: m[1], d: m[2] }));
}

function save(name, dataUrl) {
  const file = join(out, `${name}.png`);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, Buffer.from(dataUrl.split(',')[1], 'base64'));
  console.log('✓', file);
}

const browser = await chromium.launch();
const page = await browser.newPage();
await page.goto(pathToFileURL(join(here, 'shots.html')).href);

for (const name of await page.evaluate(() => window.shotNames())) {
  save(name, await page.evaluate(n => window.renderShot(n), name));
}
// Icônes : celle de la fiche (Crabe Océan, hors couleur de marque) et la classique de l'appli.
save('icone/icon-512', await page.evaluate(a => window.renderShot('icon', a), { paths: vectorPaths('ic_mascot_crab_ocean_foreground.xml') }));
save('icone/icon-512-classique', await page.evaluate(a => window.renderShot('icon', a), { paths: vectorPaths('ic_launcher_foreground.xml') }));

await browser.close();
