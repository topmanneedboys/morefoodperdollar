import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
require('../local-ai.js');
const engine = require('../engine.js');
const fixture = JSON.parse(readFileSync(new URL('../../shared-fixtures/valuepilot-golden-v1.json', import.meta.url), 'utf8'));

test('golden schema and exact money arithmetic are explicit', () => {
  assert.equal(fixture.schema, 'valuepilot.golden.contract');
  assert.equal(fixture.schemaVersion, 2);
  assert.equal(fixture.moneyArithmetic.minorOperands.reduce((sum, value) => sum + value, 0), fixture.moneyArithmetic.expectedMinor);
});

test('canonical parsing fixture matches browser engine', () => {
  for (const expected of fixture.parsing) {
    const item = engine.analyzeItem(expected.rawText);
    assert.ok(item, expected.id);
    assert.equal(item.name, expected.name, expected.id);
    assert.equal(item.price, expected.currentPrice, expected.id);
    assert.doesNotMatch(item.name, /member|previous price/i, expected.id);
    if (expected.quantityKind) {
      const browserKind = { COUNT: 'count', VOLUME_ML: 'volume', MASS_G: 'mass' }[expected.quantityKind];
      assert.equal(item.quantity?.kind, browserKind, expected.id);
      const amountBase = item.quantity.count ?? item.quantity.ml ?? item.quantity.grams;
      assert.ok(Math.abs(amountBase - expected.quantityBase) < 0.0002, expected.id);
    }
    if (expected.promotionReceivedMultiplier !== undefined) assert.equal(item.promotion.receivedMultiplier, expected.promotionReceivedMultiplier, expected.id);
  }
});

test('canonical ranking fixture has deterministic order', () => {
  const ranked = engine.rankItems(fixture.ranking.map(value => engine.analyzeItem(value.rawText)), 'unit');
  for (const expected of fixture.ranking) {
    const name = engine.analyzeItem(expected.rawText).name;
    assert.equal(ranked.find(value => value.name === name).rank, expected.expectedRank);
  }
});
