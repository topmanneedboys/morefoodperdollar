import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
require('../local-ai-model.js');
const AI = require('../local-ai.js');

test('local AI classifies common restaurant foods', () => {
  assert.equal(AI.predict('Family pepperoni pizza').category, 'pizza');
  assert.equal(AI.predict('Grilled ribeye steak dinner').category, 'beef');
  assert.equal(AI.predict('Large pad thai noodles').category, 'pasta');
});

test('local AI rejects obvious non-food products', () => {
  const result = AI.predict('Wireless Bluetooth earbuds with charging cable');
  assert.equal(result.category, 'nonfood');
  assert.ok(result.foodConfidence < 0.35);
});

test('local AI estimates meat signal without claiming exact weight', () => {
  const chicken = AI.predict('Grilled chicken breast dinner');
  const salad = AI.predict('House green salad');
  assert.ok(chicken.meatRatio > salad.meatRatio);
  assert.ok(chicken.basePortionPoints > 0);
});

test('local AI is deterministic', () => {
  assert.deepEqual(AI.predict('Chicken biryani rice bowl'), AI.predict('Chicken biryani rice bowl'));
});
