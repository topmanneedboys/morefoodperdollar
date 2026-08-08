import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
require('../local-ai-model.js');
require('../local-ai.js');
const E = require('../engine.js');

test('mass normalization: kg, lb, oz', () => {
  assert.equal(E.parseQuantity('Apples 1.5 kg').grams, 1500);
  assert.ok(Math.abs(E.parseQuantity('Apples 3 lb').grams - 1360.77711) < 0.001);
  assert.ok(Math.abs(E.parseQuantity('Steak 16 oz').grams - 453.59237) < 0.001);
});

test('multipack volume totals correctly', () => {
  const q = E.parseQuantity('Sparkling water 12 × 355 mL');
  assert.equal(q.kind, 'volume');
  assert.equal(q.ml, 4260);
});

test('grocery $/kg ranking', () => {
  const a = E.analyzeItem('Honeycrisp apples\n3 lb\n$5.99');
  const b = E.analyzeItem('Gala apples\n1.5 kg\n$4.49');
  const ranked = E.rankItems([a,b], 'mass');
  assert.equal(ranked[0].name, 'Gala apples');
  assert.ok(ranked[0].metrics.pricePerKg < ranked[1].metrics.pricePerKg);
});

test('BOGO doubles value', () => {
  const x = E.analyzeItem('Whopper\n660 cal\n$8.99\nBuy one get one free');
  assert.equal(x.promotion.type, 'bogo');
  assert.ok(Math.abs(x.metrics.caloriesPerDollar - (1320/8.99)) < 0.01);
});

test('buy 2 get 1 gives 1.5x received multiplier', () => {
  const x = E.analyzeItem('Protein bar 60 g $3.00 Buy 2 get 1 free');
  assert.equal(x.promotion.receivedMultiplier, 1.5);
  assert.ok(Math.abs(x.metrics.pricePerKg - 33.3333333) < 0.01);
});

test('bundle 2 for $5 uses bundle-unit effective value', () => {
  const x = E.analyzeItem('Avocados 1 count 2 for $5');
  assert.equal(x.promotion.type, 'bundle');
  assert.equal(x.promotion.minPaidUnits, 2);
  assert.ok(Math.abs(x.metrics.pricePerUnit - 2.5) < 0.001);
});

test('calorie value ranking', () => {
  const a = E.analyzeItem('Whopper\n660 calories\n$8.99');
  const b = E.analyzeItem('Double Whopper\n920 calories\n$10.49');
  const c = E.analyzeItem('Whopper BOGO\n660 calories\n$8.99\nBOGO');
  const ranked = E.rankItems([a,b,c], 'calorie');
  assert.equal(ranked[0].promotion.type, 'bogo');
});

test('pizza area uses diameter, not diameter linearly', () => {
  const p12 = E.analyzeItem('12 inch pizza $12.00');
  const p14 = E.analyzeItem('14 inch pizza $14.00');
  const ratio = p14.quantity.areaSqIn / p12.quantity.areaSqIn;
  assert.ok(Math.abs(ratio - (196/144)) < 0.001);
  const ranked = E.rankItems([p12,p14], 'pizza');
  assert.equal(ranked[0].name.startsWith('14 inch pizza'), true);
});

test('does not double-apply percent-off text', () => {
  const x = E.analyzeItem('Cereal 500 g Sale 20% off $4.00');
  assert.equal(x.promotion.type, 'percent-off-shown');
  assert.equal(x.price, 4);
  assert.ok(Math.abs(x.metrics.pricePerKg - 8) < 0.001);
});

test('dedupe identical products', () => {
  const a = E.analyzeItem('Milk 2 L $5.00');
  const b = E.analyzeItem('Milk\n2 L\n$5.00\nAdd to cart');
  assert.equal(E.dedupeItems([a,b]).length, 1);
});


test('savings amount is not mistaken for current sale price', () => {
  const x = E.analyzeItem('Cereal 500 g Save $2 Now $8.99 Regular $10.99');
  assert.equal(x.price, 8.99);
});

test('BOGO 50% off is not treated as free', () => {
  const x = E.analyzeItem('Burger 700 cal $10 BOGO 50% off');
  assert.equal(x.promotion.type, 'bogo-percent');
  assert.ok(Math.abs(x.promotion.receivedMultiplier - (2/1.5)) < 0.001);
});

test('portion estimate provides fallback for price-only food', () => {
  const a = E.analyzeItem('Small fries $3.00');
  const b = E.analyzeItem('Large fries $4.00');
  assert.ok(a.metrics.portionPointsPerDollar > 0);
  assert.ok(b.metrics.portionPointsPerDollar > 0);
  const ranked = E.rankItems([a,b], 'portion');
  assert.equal(ranked[0].name.startsWith('Large fries'), true);
});

test('currency prefixes are not collapsed into ambiguous dollars', () => {
  assert.equal(E.analyzeItem('Milk 2 L C$5.49').currency, 'CAD');
  assert.equal(E.analyzeItem('Milk 2 L US$4.19').currency, 'USD');
  assert.equal(E.analyzeItem('Milk 2 L A$6.10').currency, 'AUD');
});

test('international and thousands-formatted prices parse safely', () => {
  assert.equal(E.parseNumber('1,299.50'), 1299.5);
  assert.equal(E.parseNumber('1.299,50'), 1299.5);
  assert.equal(E.analyzeItem('Catering feast $1,299.50').price, 1299.5);
});

test('expanded volume units and dozens normalize', () => {
  assert.ok(Math.abs(E.parseQuantity('Juice 2 qt').ml - 1892.705892) < 0.001);
  assert.equal(E.parseQuantity('Eggs one dozen').count, 12);
  assert.equal(E.parseQuantity('Eggs half-dozen').count, 6);
});

test('second-item discounts report the real minimum spend', () => {
  const x = E.analyzeItem('Protein bar 60 g $4.00 2nd item 50% off');
  assert.equal(x.promotion.type, 'bogo-percent');
  assert.equal(x.promotion.minimumSpend, 6);
  assert.ok(Math.abs(x.metrics.pricePerKg - 50) < 0.001);
});

test('budget filter uses promotion minimum spend', () => {
  const affordable = E.analyzeItem('Burger $9.00 BOGO');
  const tooMuch = E.analyzeItem('Steak $12.00 2nd item 50% off');
  assert.deepEqual(E.filterItems([affordable, tooMuch], { maxPrice: 15 }).map(x => x.name), [affordable.name]);
});

test('local AI adds bounded food and meat estimates only as fallbacks', () => {
  const chicken = E.analyzeItem('Grilled chicken breast dinner $14.00');
  assert.equal(chicken.ai.category, 'chicken');
  assert.equal(chicken.portion.source, 'local-ai');
  assert.ok(chicken.metrics.meatPointsPerDollar > 0);
  const measured = E.analyzeItem('Chicken breast 500 g $8.00');
  assert.equal(measured.quantity.kind, 'mass');
  assert.equal(E.metricDescriptor(measured, 'smart').key, 'pricePerKg');
});

test('food and pork filters use the local model without network calls', () => {
  const headphones = E.analyzeItem('Wireless bluetooth earbuds $29.99');
  const pork = E.analyzeItem('Pork chop dinner $15.00');
  const chicken = E.analyzeItem('Chicken dinner $14.00');
  assert.deepEqual(E.filterItems([headphones, pork, chicken], { foodOnly: true, excludePork: true }).map(x => x.ai.category), ['chicken']);
});

test('name extraction skips ratings and action labels', () => {
  const item = E.analyzeItem('4.8 stars\nAdd to cart\nChicken biryani bowl\n$13.99');
  assert.equal(item.name, 'Chicken biryani bowl');
});

test('unicode product names retain stable dedupe keys', () => {
  const a = E.analyzeItem('Crème brûlée $7.00');
  const b = E.analyzeItem('Crème brûlée\n$7.00\nAdd');
  assert.equal(E.dedupeItems([a, b]).length, 1);
});
