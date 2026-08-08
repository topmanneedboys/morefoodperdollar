import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const scripts = ['local-ai-model.js', 'local-ai.js', 'engine.js', 'content.js']
  .map(name => fs.readFileSync(path.join(root, name), 'utf8'));

const tick = ms => new Promise(resolve => setTimeout(resolve, ms));

test('content UI scans, filters, and applies a budget locally', async () => {
  const dom = new JSDOM(`<!doctype html><html><body>
    <main>
      <article class="product-card">Milk\n2 L\nC$5.49</article>
      <article class="product-card">Grilled chicken breast dinner\n$14.00</article>
      <article class="product-card">Pork chop dinner\n$15.00</article>
      <article class="product-card">Wireless Bluetooth earbuds\n$29.99</article>
    </main>
  </body></html>`, {
    url: 'https://shop.example/menu',
    runScripts: 'outside-only',
    pretendToBeVisual: true,
  });
  const stored = {};
  dom.window.chrome = {
    runtime: { lastError: null },
    storage: {
      local: {
        get(key, callback) { callback({ [key]: stored[key] }); },
        set(value, callback) { Object.assign(stored, value); callback?.(); },
      },
    },
  };

  for (const source of scripts) dom.window.eval(source);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await tick(30);

  const host = dom.window.document.querySelector('#valuepilot-root');
  assert.ok(host?.shadowRoot, 'ValuePilot shadow UI should be mounted');
  const shadow = host.shadowRoot;
  shadow.querySelector('#vp-fab').click();
  await tick(20);
  assert.match(shadow.querySelector('#vp-list').textContent, /Grilled chicken breast dinner/);
  assert.doesNotMatch(shadow.querySelector('#vp-list').textContent, /Wireless Bluetooth earbuds/);

  const noPork = shadow.querySelector('#vp-no-pork');
  noPork.checked = true;
  noPork.dispatchEvent(new dom.window.Event('change'));
  assert.doesNotMatch(shadow.querySelector('#vp-list').textContent, /Pork chop dinner/);

  const budget = shadow.querySelector('#vp-budget');
  budget.value = '6';
  budget.dispatchEvent(new dom.window.Event('input'));
  assert.match(shadow.querySelector('#vp-list').textContent, /Milk/);
  assert.doesNotMatch(shadow.querySelector('#vp-list').textContent, /Grilled chicken breast dinner/);
  assert.equal(stored.valuePilotSettingsV101.maxPrice, 6);
  dom.window.close();
});
