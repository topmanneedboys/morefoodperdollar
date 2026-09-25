import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dist = path.join(root, 'dist');
const sharedFiles = [
  'local-ai-model.js',
  'local-ai.js',
  'engine.js',
  'content.js',
  'popup.html',
  'popup.css',
];

function copyFile(relative, targetRoot, targetName = relative) {
  const destination = path.join(targetRoot, targetName);
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.copyFileSync(path.join(root, relative), destination);
}

fs.rmSync(dist, { recursive: true, force: true });
for (const browser of ['chromium', 'firefox']) {
  const target = path.join(dist, browser);
  fs.mkdirSync(target, { recursive: true });
  for (const file of sharedFiles) copyFile(file, target);
  fs.cpSync(path.join(root, 'icons'), path.join(target, 'icons'), { recursive: true });
  copyFile(browser === 'firefox' ? 'manifest-firefox.json' : 'manifest.json', target, 'manifest.json');
}

console.log('Prepared Chromium and Firefox extension directories.');
