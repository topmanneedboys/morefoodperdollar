/* ValuePilot Core Engine - deterministic measurements plus bounded local AI fallbacks. */
(() => {
  'use strict';

  let LocalAI = globalThis.ValuePilotLocalAI;
  if (!LocalAI && typeof require === 'function') {
    try { LocalAI = require('./local-ai.js'); } catch {}
  }

  const MASS_TO_G = {
    mcg: 0.000001,
    'µg': 0.000001,
    'μg': 0.000001,
    mg: 0.001,
    g: 1,
    gram: 1,
    grams: 1,
    kg: 1000,
    kilogram: 1000,
    kilograms: 1000,
    oz: 28.349523125,
    ounce: 28.349523125,
    ounces: 28.349523125,
    lb: 453.59237,
    lbs: 453.59237,
    pound: 453.59237,
    pounds: 453.59237,
  };

  const VOL_TO_ML = {
    ml: 1,
    milliliter: 1,
    milliliters: 1,
    millilitre: 1,
    millilitres: 1,
    l: 1000,
    litre: 1000,
    litres: 1000,
    liter: 1000,
    liters: 1000,
    cl: 10,
    dl: 100,
    'fl oz': 29.5735295625,
    floz: 29.5735295625,
    cup: 236.5882365,
    cups: 236.5882365,
    pt: 473.176473,
    pint: 473.176473,
    pints: 473.176473,
    qt: 946.352946,
    quart: 946.352946,
    quarts: 946.352946,
    gal: 3785.411784,
    gallon: 3785.411784,
    gallons: 3785.411784,
  };

  const CURRENCY_SYMBOLS = {
    'CA$': 'CAD',
    'US$': 'USD',
    'A$': 'AUD',
    'C$': 'CAD',
    '€': 'EUR',
    '£': 'GBP',
    '₹': 'INR',
    '৳': 'BDT',
    '$': 'USD/CAD',
  };

  const PRICE_NUMBER_SOURCE = String.raw`(?:\d{1,3}(?:[ ,.]\d{3})+|\d{1,6})(?:[.,]\d{1,2})?`;
  const PRICE_RE = new RegExp(String.raw`(?:\b(?:CA\$|C\$|US\$|A\$)|[$€£₹৳])\s*${PRICE_NUMBER_SOURCE}|\b${PRICE_NUMBER_SOURCE}\s*(?:CAD|USD|EUR|GBP|INR|BDT|AUD)\b`, 'gi');
  const PRICE_NUMBER_RE = new RegExp(PRICE_NUMBER_SOURCE);
  const CAL_RE = /\b(\d{2,5}(?:[.,]\d+)?)\s*(?:k?cal(?:ories?)?)\b/i;

  function normalizeSpace(s) {
    return String(s ?? '').replace(/\u00a0/g, ' ').replace(/[\t\r ]+/g, ' ').replace(/\n{3,}/g, '\n\n').trim();
  }

  function parseNumber(s) {
    if (typeof s === 'number') return Number.isFinite(s) ? s : null;
    const raw = String(s ?? '').trim().replace(/[ '\u00a0]/g, '');
    if (!raw) return null;
    const lastComma = raw.lastIndexOf(',');
    const lastDot = raw.lastIndexOf('.');
    let normalized = raw;
    if (lastComma >= 0 && lastDot >= 0) {
      normalized = lastComma > lastDot
        ? raw.replace(/\./g, '').replace(',', '.')
        : raw.replace(/,/g, '');
    } else if (lastComma >= 0) {
      normalized = /^\d{1,3}(?:,\d{3})+$/.test(raw) ? raw.replace(/,/g, '') : raw.replace(',', '.');
    } else if (lastDot >= 0 && /^\d{1,3}(?:\.\d{3})+$/.test(raw)) {
      normalized = raw.replace(/\./g, '');
    }
    const n = Number.parseFloat(normalized);
    return Number.isFinite(n) ? n : null;
  }

  function extractPrices(text) {
    const t = normalizeSpace(text);
    const out = [];
    for (const match of t.matchAll(PRICE_RE)) {
      const raw = match[0];
      const amountMatch = raw.match(PRICE_NUMBER_RE);
      if (!amountMatch) continue;
      const amount = parseNumber(amountMatch[0]);
      if (!(amount > 0)) continue;
      let currency = 'UNKNOWN';
      for (const [sym, code] of Object.entries(CURRENCY_SYMBOLS)) {
        if (raw.includes(sym)) { currency = code; break; }
      }
      if (/\bCAD\b/i.test(raw)) currency = 'CAD';
      else if (/\bUSD\b/i.test(raw)) currency = 'USD';
      else if (/\bEUR\b/i.test(raw)) currency = 'EUR';
      else if (/\bGBP\b/i.test(raw)) currency = 'GBP';
      else if (/\bAUD\b/i.test(raw)) currency = 'AUD';
      out.push({ amount, currency, raw, index: match.index ?? -1 });
    }
    return out;
  }

  function chooseLikelyPrice(text, prices) {
    if (!prices?.length) return null;
    const t = normalizeSpace(text);
    // Bundle phrases such as "2 for $5" should use the explicit bundle price.
    const bundle = t.match(/\b(\d{1,2})\s*(?:for|\/|at)\s*((?:CA\$|C\$|US\$|A\$|[$€£₹৳])\s*\d+(?:[.,]\d{1,2})?)/i);
    if (bundle) {
      const p = extractPrices(bundle[2])[0];
      if (p) return { ...p, source: 'bundle', bundleUnits: Number(bundle[1]) };
    }

    // Prefer explicit current-price language and ignore savings amounts such as "Save $2".
    if (prices.length > 1) {
      const currentMatch = t.match(/\b(?:now|sale(?:\s+price)?|member(?:\s+price)?|deal(?:\s+price)?)\s*[:\-]?\s*((?:CA\$|C\$|US\$|A\$|[$€£₹৳])\s*\d+(?:[.,]\d{1,2})?)/i);
      if (currentMatch) {
        const p = extractPrices(currentMatch[1])[0];
        if (p) return { ...p, source: 'explicit-current' };
      }
      const usable = prices.filter(p => {
        const prefix = t.slice(Math.max(0, p.index - 14), p.index);
        return !/\b(?:save|saving|discount|off)\s*$/.test(prefix.toLowerCase());
      });
      const saleHints = /\b(sale|now|deal|member|promo|offer|discount|save|regular|reg\.?|was)\b/i.test(t);
      if (saleHints && usable.length) {
        const min = usable.reduce((a, b) => b.amount < a.amount ? b : a, usable[0]);
        return { ...min, source: 'sale-min' };
      }
    }
    return { ...prices[0], source: 'first' };
  }

  function detectPromotion(text, price) {
    const t = normalizeSpace(text).toLowerCase();
    const promo = {
      type: 'none',
      label: '',
      receivedMultiplier: 1,
      minPaidUnits: 1,
      minimumSpend: price ?? null,
      effectivePrice: price ?? null,
      confidence: 1,
    };

    const bogoPercent = t.match(/\b(?:bogo|buy\s*one\s*(?:,|&|and)?\s*get\s*(?:one|1))\s*(\d{1,2})\s*%\s*off\b/i);
    if (bogoPercent) {
      const pct = Math.min(99, Number(bogoPercent[1])) / 100;
      const paidForTwo = 2 - pct;
      promo.type = 'bogo-percent';
      promo.label = `Buy 1, get 1 ${Math.round(pct * 100)}% off`;
      promo.receivedMultiplier = 2 / paidForTwo;
      promo.minPaidUnits = 2;
      if (price) {
        promo.effectivePrice = price / promo.receivedMultiplier;
        promo.minimumSpend = price * paidForTwo;
      }
      promo.confidence = 0.9;
      return promo;
    }

    const secondPercent = t.match(/\b(?:second|2nd)\s+(?:item\s+)?(?:is\s+)?(\d{1,2})\s*%\s*off\b/i);
    if (secondPercent) {
      const pct = Math.min(99, Number(secondPercent[1])) / 100;
      const paidForTwo = 2 - pct;
      promo.type = 'bogo-percent';
      promo.label = `2nd item ${Math.round(pct * 100)}% off`;
      promo.receivedMultiplier = 2 / paidForTwo;
      promo.minPaidUnits = 2;
      if (price) {
        promo.effectivePrice = price / promo.receivedMultiplier;
        promo.minimumSpend = price * paidForTwo;
      }
      promo.confidence = 0.88;
      return promo;
    }

    if (/\b(?:bogo(?!\s*\d+\s*%\s*off)|buy\s*one\s*(?:,|&|and)?\s*get\s*(?:one|1)(?:\s*free)?(?!\s*\d+\s*%\s*off)|2\s*for\s*1|two\s*for\s*one)\b/i.test(t)) {
      promo.type = 'bogo';
      promo.label = 'Buy 1, get 1';
      promo.receivedMultiplier = 2;
      promo.minPaidUnits = 1;
      if (price) {
        promo.effectivePrice = price / 2;
        promo.minimumSpend = price;
      }
      return promo;
    }

    const bxgy = t.match(/\bbuy\s+(\d+)\s+(?:and\s+)?get\s+(\d+)\s+(?:free|at\s+no\s+cost)\b/i);
    if (bxgy) {
      const buy = Number(bxgy[1]);
      const get = Number(bxgy[2]);
      if (buy > 0 && get > 0) {
        promo.type = 'buy-x-get-y';
        promo.label = `Buy ${buy}, get ${get}`;
        promo.receivedMultiplier = (buy + get) / buy;
        promo.minPaidUnits = buy;
        if (price) {
          promo.effectivePrice = price / promo.receivedMultiplier;
          promo.minimumSpend = price * buy;
        }
        return promo;
      }
    }

    const bundle = t.match(/\b(\d{1,2})\s*(?:for|\/)\s*(?:ca\$|c\$|us\$|a\$|[$€£₹৳])\s*(\d+(?:[.,]\d{1,2})?)/i);
    if (bundle) {
      const units = Number(bundle[1]);
      const total = parseNumber(bundle[2]);
      if (units > 1 && total > 0) {
        promo.type = 'bundle';
        promo.label = `${units} for ${total}`;
        promo.receivedMultiplier = units;
        promo.minPaidUnits = units;
        promo.effectivePrice = total / units;
        promo.minimumSpend = total;
        promo.bundleTotalPrice = total;
        return promo;
      }
    }

    const percent = t.match(/\b(\d{1,2})\s*%\s*off\b/);
    if (percent) {
      promo.type = 'percent-off-shown';
      promo.label = `${percent[1]}% off`;
      // Do not discount again because most commerce pages already display the reduced price.
      promo.confidence = 0.8;
      return promo;
    }

    if (/\bfree\s+delivery\b/.test(t)) {
      promo.type = 'free-delivery';
      promo.label = 'Free delivery';
      promo.confidence = 0.7;
    }
    return promo;
  }

  function normalizeUnit(u) {
    const normalized = String(u ?? '').toLowerCase().replace(/\./g, '').replace(/\s+/g, ' ').trim();
    if (/^fluid ounces?$/.test(normalized)) return 'fl oz';
    if (normalized === 'floz') return 'fl oz';
    return normalized;
  }

  const MASS_UNIT_SOURCE = String.raw`mcg|[µμ]g|mg|grams?|g|kilograms?|kg|ounces?|oz|pounds?|lbs?|lb`;
  const VOLUME_UNIT_SOURCE = String.raw`millilit(?:er|re)s?|ml|lit(?:er|re)s?|l|cl|dl|fl\s*oz|floz|fluid\s*ounces?|cups?|pints?|pt|quarts?|qt|gallons?|gal`;
  const QUANTITY_UNIT_SOURCE = `${VOLUME_UNIT_SOURCE}|${MASS_UNIT_SOURCE}`;

  function parseQuantity(text) {
    const t = normalizeSpace(text).toLowerCase();
    const candidates = [];

    // Multipacks like 6 x 355 mL, 12×330ml, 2 x 4 oz.
    const multiRe = new RegExp(String.raw`\b(\d{1,3})\s*[x×]\s*(\d+(?:[.,]\d+)?)\s*(${QUANTITY_UNIT_SOURCE})\b`, 'gi');
    for (const m of t.matchAll(multiRe)) {
      const count = Number(m[1]);
      const each = parseNumber(m[2]);
      const unit = normalizeUnit(m[3]);
      if (!(count > 0 && each > 0)) continue;
      if (unit in MASS_TO_G) candidates.push({ kind: 'mass', grams: count * each * MASS_TO_G[unit], display: `${count} × ${m[2]} ${m[3]}`, confidence: 1, packCount: count });
      else if (unit in VOL_TO_ML) candidates.push({ kind: 'volume', ml: count * each * VOL_TO_ML[unit], display: `${count} × ${m[2]} ${m[3]}`, confidence: 1, packCount: count });
    }

    // "Pack of 6, 355 mL each".
    const packEach = t.match(new RegExp(String.raw`\b(?:pack|case|box)\s+of\s+(\d{1,3}).{0,40}?(\d+(?:[.,]\d+)?)\s*(${QUANTITY_UNIT_SOURCE})\s*(?:each|ea)?\b`, 'i'));
    if (packEach) {
      const count = Number(packEach[1]);
      const each = parseNumber(packEach[2]);
      const unit = normalizeUnit(packEach[3]);
      if (count > 0 && each > 0) {
        if (unit in MASS_TO_G) candidates.push({ kind: 'mass', grams: count * each * MASS_TO_G[unit], display: `${count} × ${packEach[2]} ${packEach[3]}`, confidence: 0.95, packCount: count });
        else if (unit in VOL_TO_ML) candidates.push({ kind: 'volume', ml: count * each * VOL_TO_ML[unit], display: `${count} × ${packEach[2]} ${packEach[3]}`, confidence: 0.95, packCount: count });
      }
    }

    // Ranges: 2-3 lb. Use midpoint but mark as approximate.
    const rangeRe = new RegExp(String.raw`\b(\d+(?:[.,]\d+)?)\s*[-–]\s*(\d+(?:[.,]\d+)?)\s*(${QUANTITY_UNIT_SOURCE})\b`, 'gi');
    for (const m of t.matchAll(rangeRe)) {
      const a = parseNumber(m[1]), b = parseNumber(m[2]);
      const unit = normalizeUnit(m[3]);
      if (!(a > 0 && b > 0)) continue;
      const mid = (a + b) / 2;
      if (unit in MASS_TO_G) candidates.push({ kind: 'mass', grams: mid * MASS_TO_G[unit], display: `${m[1]}–${m[2]} ${m[3]} avg`, confidence: 0.7, range: [a, b] });
      else if (unit in VOL_TO_ML) candidates.push({ kind: 'volume', ml: mid * VOL_TO_ML[unit], display: `${m[1]}–${m[2]} ${m[3]} avg`, confidence: 0.7, range: [a, b] });
    }

    // Single quantity. Negative look-behind avoids consuming the right side of x where practical.
    const qtyRe = new RegExp(String.raw`\b(\d+(?:[.,]\d+)?)\s*(${QUANTITY_UNIT_SOURCE})\b`, 'gi');
    for (const m of t.matchAll(qtyRe)) {
      // Skip if clearly part of a multipack already counted (e.g. "6 x 355 ml").
      const prefix = t.slice(Math.max(0, (m.index ?? 0) - 8), m.index ?? 0);
      if (/\d+\s*[x×]\s*$/.test(prefix)) continue;
      const value = parseNumber(m[1]);
      const unit = normalizeUnit(m[2]);
      if (!(value > 0)) continue;
      if (unit in MASS_TO_G) candidates.push({ kind: 'mass', grams: value * MASS_TO_G[unit], display: `${m[1]} ${m[2]}`, confidence: 0.98 });
      else if (unit in VOL_TO_ML) candidates.push({ kind: 'volume', ml: value * VOL_TO_ML[unit], display: `${m[1]} ${m[2]}`, confidence: 0.98 });
    }

    // Count/pack quantities.
    const countPatterns = [
      /\b(\d{1,4})\s*(?:count|ct|pieces?|pcs|pack|pk|units?|ea)\b/i,
      /\b(?:pack|box|case|set)\s+of\s+(\d{1,4})\b/i,
    ];
    for (const re of countPatterns) {
      const m = t.match(re);
      if (m) {
        const count = Number(m[1]);
        if (count > 0) candidates.push({ kind: 'count', count, display: `${count} count`, confidence: 0.9 });
      }
    }

    const dozen = t.match(/\b(half[ -]?)?dozen\b/i);
    if (dozen) candidates.push({ kind: 'count', count: dozen[1] ? 6 : 12, display: dozen[1] ? 'half-dozen' : 'dozen', confidence: 0.92 });

    // Pizza diameter. Require contextual pizza language to avoid confusing shoe sizes etc.
    if (/\b(pizza|pie|flatbread)\b/i.test(t)) {
      const inch = t.match(/\b(\d{1,2}(?:[.,]\d+)?)\s*(?:in(?:ch(?:es)?)?|\")\b/i);
      const cm = t.match(/\b(\d{2,3}(?:[.,]\d+)?)\s*cm\b/i);
      let diameterIn = null;
      if (inch) diameterIn = parseNumber(inch[1]);
      else if (cm) diameterIn = parseNumber(cm[1]) / 2.54;
      if (diameterIn && diameterIn >= 5 && diameterIn <= 30) {
        candidates.push({
          kind: 'pizza-area',
          diameterIn,
          areaSqIn: Math.PI * Math.pow(diameterIn / 2, 2),
          display: `${Number(diameterIn.toFixed(1))}\" pizza`,
          confidence: 0.9,
        });
      }
    }

    // Prefer exact multipack totals, then strongest direct measurement.
    const priority = { mass: 4, volume: 3, 'pizza-area': 2, count: 1 };
    candidates.sort((a, b) => ((b.packCount ? 2 : 0) + priority[b.kind] + b.confidence) - ((a.packCount ? 2 : 0) + priority[a.kind] + a.confidence));
    return candidates[0] ?? null;
  }

  function parseCalories(text) {
    const m = normalizeSpace(text).match(CAL_RE);
    if (!m) return null;
    const kcal = parseNumber(m[1]);
    return kcal > 0 ? kcal : null;
  }

  function extractName(text) {
    const lines = String(text ?? '').split(/\n+/).map(s => normalizeSpace(s)).filter(Boolean).slice(0, 40);
    const bad = /^(?:add|customize|choose|select|popular|sponsored|deal|sale|save|free delivery|buy one|get one|from\s+[$€£₹৳]|[$€£₹৳]\s*\d|view cart|checkout|order now)\b/i;
    const quantityOnly = new RegExp(String.raw`^\d+(?:[.,]\d+)?\s*(?:${QUANTITY_UNIT_SOURCE}|cal|kcal|calories?|ct|count|pieces?|pcs|pack|pk|units?|ea)$`, 'i');
    const ratingOrTime = /^(?:\d(?:[.,]\d)?\s*(?:stars?|★)|\d+\s*(?:min|mins|minutes?|hours?|reviews?|ratings?))\b/i;
    let best = null;
    let bestScore = -Infinity;
    lines.forEach((line, index) => {
      const withoutPrice = line.replace(PRICE_RE, ' ').replace(/\s+/g, ' ').trim();
      if (withoutPrice.length < 2 || withoutPrice.length > 160) return;
      let score = 0;
      if (/\p{L}/u.test(withoutPrice)) score += 4;
      if (withoutPrice.length >= 3 && withoutPrice.length <= 80) score += 3;
      if (!PRICE_RE.test(line)) score += 1;
      PRICE_RE.lastIndex = 0;
      if (bad.test(withoutPrice)) score -= 9;
      if (quantityOnly.test(withoutPrice) || ratingOrTime.test(withoutPrice)) score -= 8;
      if (/\b(?:subtotal|total|delivery fee|service fee|tax|tip|add to cart)\b/i.test(withoutPrice)) score -= 7;
      const ai = LocalAI?.predict?.(withoutPrice);
      if (ai?.foodConfidence >= 0.35) score += 2 + ai.foodConfidence;
      score -= index * 0.05;
      if (score > bestScore) { bestScore = score; best = withoutPrice; }
    });
    if (best && bestScore > -1) return best.slice(0, 120);
    const oneLine = normalizeSpace(text).replace(PRICE_RE, ' ').replace(/\s+/g, ' ').trim();
    return oneLine.slice(0, 120) || 'Unnamed item';
  }


  function estimatePortionPoints(text, ai = null) {
    const t = normalizeSpace(text).toLowerCase();
    let points = null;
    let confidence = 0.45;
    let basis = '';
    let source = 'explicit';

    const foodCount = t.match(/\b(\d{1,3})\s*(?:piece|pieces|pc|pcs|wings?|nuggets?|tenders?|patties|tacos?|burgers?|sandwiches?|slices?)\b/i);
    if (foodCount) {
      const n = Number(foodCount[1]);
      if (n > 0 && n <= 100) { points = n; confidence = 0.72; basis = `${n} food units`; }
    }

    if (points == null) {
      if (/\btriple\b/.test(t)) { points = 3; confidence = 0.66; basis = 'triple'; }
      else if (/\bdouble\b/.test(t)) { points = 2; confidence = 0.64; basis = 'double'; }
      else if (/\bsingle\b/.test(t)) { points = 1; confidence = 0.6; basis = 'single'; }
    }

    if (points == null && ai?.portionEligible && ai.basePortionPoints > 0 && ai.foodConfidence >= 0.34) {
      points = ai.basePortionPoints;
      confidence = Math.min(0.62, 0.28 + ai.confidence * 0.5);
      basis = `local AI: ${ai.label}`;
      source = 'local-ai';
    }

    let sizeFactor = null;
    let sizeBasis = '';
    if (/\b(?:party|feast)\b/.test(t)) { sizeFactor = 2.2; sizeBasis = 'party'; }
    else if (/\b(?:family|sharing)\b/.test(t)) { sizeFactor = 1.8; sizeBasis = 'family'; }
    else if (/\b(?:extra[ -]?large|x[- ]?large|xl)\b/.test(t)) { sizeFactor = 1.55; sizeBasis = 'extra large'; }
    else if (/\blarge\b/.test(t)) { sizeFactor = 1.3; sizeBasis = 'large'; }
    else if (/\bmedium\b/.test(t)) { sizeFactor = 1.0; sizeBasis = 'medium'; }
    else if (/\bsmall\b/.test(t)) { sizeFactor = 0.8; sizeBasis = 'small'; }
    else if (/\b(?:kid|kids|junior)\b/.test(t)) { sizeFactor = 0.65; sizeBasis = 'kids/junior'; }

    if (sizeFactor != null) {
      points = (points ?? 1) * sizeFactor;
      confidence = Math.max(confidence, 0.55);
      basis = basis ? `${basis} + ${sizeBasis}` : sizeBasis;
    }
    if (/\b(?:combo|meal)\b/.test(t)) {
      points = (points ?? 1) * 1.35;
      confidence = Math.max(confidence, 0.5);
      basis = basis ? `${basis} + meal` : 'meal';
    }
    return points != null ? { points, confidence, basis, source } : null;
  }

  function classify(text, qty, calories, ai = null) {
    const t = normalizeSpace(text).toLowerCase();
    if (ai?.confidence >= 0.26 && ai?.foodConfidence >= 0.32) return ai.category;
    if (qty?.kind === 'mass' || qty?.kind === 'volume') {
      if (/\b(apple|banana|orange|milk|water|beef|chicken|fish|rice|flour|cereal|yogurt|cheese|produce|grocery|grocer)\b/.test(t)) return 'grocery';
    }
    if (calories || /\b(burger|pizza|sandwich|fries|meal|combo|wrap|restaurant|wings?|nuggets?|pasta|bowl)\b/.test(t)) return 'food';
    if (qty) return 'measured';
    return 'unknown';
  }

  function analyzeItem(raw) {
    const text = normalizeSpace(typeof raw === 'string' ? raw : raw?.text ?? '');
    const explicitName = typeof raw === 'object' ? normalizeSpace(raw?.name) : '';
    const prices = extractPrices(text);
    const selectedPrice = (typeof raw === 'object' && raw?.price > 0)
      ? { amount: Number(raw.price), currency: raw.currency ?? 'UNKNOWN', raw: String(raw.price), source: 'explicit' }
      : chooseLikelyPrice(text, prices);
    if (!selectedPrice?.amount) return null;

    const promo = detectPromotion(text, selectedPrice.amount);
    const qty = parseQuantity(text);
    const calories = parseCalories(text);
    const ai = LocalAI?.predict?.(`${explicitName}\n${text}`) ?? null;
    const portion = estimatePortionPoints(text, ai);
    const price = selectedPrice.amount;
    const effectiveUnitPrice = promo.effectivePrice ?? price;
    const receivedMultiplier = promo.receivedMultiplier || 1;

    const metrics = {};
    if (qty?.kind === 'mass' && qty.grams > 0) {
      metrics.gramsPerDollar = (qty.grams * receivedMultiplier) / price;
      metrics.pricePerKg = price / ((qty.grams * receivedMultiplier) / 1000);
    }
    if (qty?.kind === 'volume' && qty.ml > 0) {
      metrics.mlPerDollar = (qty.ml * receivedMultiplier) / price;
      metrics.pricePerL = price / ((qty.ml * receivedMultiplier) / 1000);
    }
    if (qty?.kind === 'count' && qty.count > 0) {
      metrics.unitsPerDollar = (qty.count * receivedMultiplier) / price;
      metrics.pricePerUnit = price / (qty.count * receivedMultiplier);
    }
    if (qty?.kind === 'pizza-area' && qty.areaSqIn > 0) {
      metrics.areaPerDollar = (qty.areaSqIn * receivedMultiplier) / price;
      metrics.pricePerSqIn = price / (qty.areaSqIn * receivedMultiplier);
    }
    if (calories) {
      metrics.caloriesPerDollar = (calories * receivedMultiplier) / price;
      metrics.pricePer100Cal = price / ((calories * receivedMultiplier) / 100);
    }
    if (portion?.points > 0) {
      metrics.portionPointsPerDollar = (portion.points * receivedMultiplier) / price;
    }
    if (portion?.points > 0 && ai?.meatRatio > 0.08 && ai.confidence >= 0.3) {
      metrics.meatPointsPerDollar = (portion.points * ai.meatRatio * receivedMultiplier) / price;
    }

    const category = classify(text, qty, calories, ai);
    return {
      id: raw?.id ?? null,
      name: explicitName || extractName(text),
      text,
      price,
      currency: selectedPrice.currency,
      priceSource: selectedPrice.source,
      quantity: qty,
      calories,
      portion,
      promotion: promo,
      metrics,
      category,
      ai,
      sourceUrl: raw?.sourceUrl ?? null,
      confidence: computeConfidence({ qty, calories, priceSource: selectedPrice.source, text, ai }),
    };
  }

  function computeConfidence({ qty, calories, priceSource, text, ai }) {
    let c = 0.55;
    if (priceSource === 'explicit') c += 0.2;
    else if (priceSource === 'first' || priceSource === 'bundle') c += 0.12;
    if (qty) c += 0.18 * (qty.confidence ?? 0.8);
    if (calories) c += 0.08;
    if (/\b(?:bogo|buy\s+\d+|get\s+\d+|\d+\s+for\s+[$€£₹৳])\b/i.test(text)) c += 0.03;
    if (ai?.foodConfidence >= 0.5) c += Math.min(0.04, ai.confidence * 0.04);
    return Math.min(0.99, Number(c.toFixed(2)));
  }

  function metricDescriptor(item, mode = 'smart') {
    const m = item.metrics || {};
    const candidates = {
      mass: m.pricePerKg != null ? { key: 'pricePerKg', lowerBetter: true, value: m.pricePerKg, label: `${money(m.pricePerKg, item.currency)}/kg` } : null,
      volume: m.pricePerL != null ? { key: 'pricePerL', lowerBetter: true, value: m.pricePerL, label: `${money(m.pricePerL, item.currency)}/L` } : null,
      unit: m.pricePerUnit != null ? { key: 'pricePerUnit', lowerBetter: true, value: m.pricePerUnit, label: `${money(m.pricePerUnit, item.currency)}/unit` } : null,
      calorie: m.caloriesPerDollar != null ? { key: 'caloriesPerDollar', lowerBetter: false, value: m.caloriesPerDollar, label: `${fmt(m.caloriesPerDollar, 1)} cal/$` } : null,
      pizza: m.areaPerDollar != null ? { key: 'areaPerDollar', lowerBetter: false, value: m.areaPerDollar, label: `${fmt(m.areaPerDollar, 1)} in²/$` } : null,
      portion: m.portionPointsPerDollar != null ? { key: 'portionPointsPerDollar', lowerBetter: false, value: m.portionPointsPerDollar, label: `${fmt(m.portionPointsPerDollar, 2)} ${item.portion?.source === 'local-ai' ? 'AI est.' : 'est.'} food/$` } : null,
      meat: m.meatPointsPerDollar != null ? { key: 'meatPointsPerDollar', lowerBetter: false, value: m.meatPointsPerDollar, label: `${fmt(m.meatPointsPerDollar, 2)} AI est. meat/$` } : null,
    };

    if (mode !== 'smart') return candidates[mode] ?? null;
    if (item.quantity?.kind === 'mass') return candidates.mass;
    if (item.quantity?.kind === 'volume') return candidates.volume;
    if (item.quantity?.kind === 'pizza-area') return candidates.pizza || candidates.calorie;
    if (item.calories) return candidates.calorie;
    if (item.quantity?.kind === 'count') return candidates.unit;
    if (item.portion) return candidates.portion;
    return null;
  }

  function determineSmartFamily(items) {
    const counts = { mass: 0, volume: 0, calorie: 0, pizza: 0, unit: 0, portion: 0, meat: 0 };
    for (const item of items) {
      if (item.metrics?.pricePerKg != null) counts.mass++;
      if (item.metrics?.pricePerL != null) counts.volume++;
      if (item.metrics?.caloriesPerDollar != null) counts.calorie++;
      if (item.metrics?.areaPerDollar != null) counts.pizza++;
      if (item.metrics?.pricePerUnit != null) counts.unit++;
      if (item.metrics?.portionPointsPerDollar != null) counts.portion++;
      if (item.metrics?.meatPointsPerDollar != null) counts.meat++;
    }
    // Prefer a family shared by at least two items. Mass/volume are direct measures; calories are a proxy for restaurant value.
    const order = ['mass', 'volume', 'calorie', 'pizza', 'unit', 'portion'];
    const shared = order.filter(k => counts[k] >= 2).sort((a, b) => counts[b] - counts[a] || order.indexOf(a) - order.indexOf(b));
    return shared[0] ?? order.sort((a, b) => counts[b] - counts[a])[0];
  }

  function rankItems(items, mode = 'smart') {
    const clean = items.filter(Boolean);
    const resolvedMode = mode === 'smart' ? determineSmartFamily(clean) : mode;
    return clean
      .map((item, originalIndex) => ({ item, originalIndex, descriptor: metricDescriptor(item, resolvedMode) }))
      .sort((a, b) => {
        if (!!a.descriptor !== !!b.descriptor) return a.descriptor ? -1 : 1;
        if (!a.descriptor && !b.descriptor) return a.item.price - b.item.price || a.originalIndex - b.originalIndex;
        const da = a.descriptor, db = b.descriptor;
        const cmp = da.lowerBetter ? da.value - db.value : db.value - da.value;
        return cmp || b.item.confidence - a.item.confidence || a.item.price - b.item.price || a.originalIndex - b.originalIndex;
      })
      .map((x, i) => ({ ...x.item, rank: i + 1, rankingMode: resolvedMode, rankingLabel: x.descriptor?.label ?? 'price only' }));
  }

  function minimumSpend(item) {
    return Number(item?.promotion?.minimumSpend ?? item?.price ?? Infinity);
  }

  function filterItems(items, options = {}) {
    const maxPrice = Number(options.maxPrice);
    const hasBudget = Number.isFinite(maxPrice) && maxPrice > 0;
    return items.filter(item => {
      if (!item) return false;
      if (hasBudget && minimumSpend(item) > maxPrice) return false;
      if (options.excludePork && (item.ai?.category === 'pork' || item.ai?.porkConfidence >= 0.36)) return false;
      if (options.foodOnly && item.ai?.available && item.ai.foodConfidence < 0.22 && item.ai.category === 'nonfood') return false;
      return true;
    });
  }


  function canonicalName(name) {
    let s = normalizeSpace(name).normalize('NFKC').toLowerCase();
    s = s.replace(PRICE_RE, ' ')
      .replace(/\b\d+(?:[.,]\d+)?\s*(?:mg|g|grams?|kg|kilograms?|oz|ounces?|lb|lbs|pounds?|ml|milliliters?|l|litres?|liters?|fl\s*oz|cal|kcal|calories?|ct|count|pieces?|pcs|pack|pk|units?|ea)\b/gi, ' ')
      .replace(/\b(?:buy\s+one\s+get\s+one(?:\s+free)?|bogo|sale|deal|save\s+\d+%|\d+%\s*off|add to cart|customize)\b/gi, ' ')
      .replace(/[^\p{L}\p{N}]+/gu, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    return s || normalizeSpace(name).toLowerCase().slice(0, 80);
  }

  function dedupeItems(items) {
    const seen = new Map();
    for (const item of items.filter(Boolean)) {
      const q = item.quantity;
      const qKey = q?.kind === 'mass' ? Math.round(q.grams) : q?.kind === 'volume' ? Math.round(q.ml) : q?.kind === 'count' ? q.count : q?.kind === 'pizza-area' ? Math.round(q.areaSqIn) : '';
      const key = `${canonicalName(item.name)}|${item.price.toFixed(2)}|${q?.kind ?? ''}|${qKey}|${item.promotion?.type ?? ''}`;
      const prev = seen.get(key);
      if (!prev || item.text.length > prev.text.length || item.confidence > prev.confidence) seen.set(key, item);
    }
    return [...seen.values()];
  }

  function money(n, currency = 'UNKNOWN') {
    const symbol = currency === 'EUR' ? '€' : currency === 'GBP' ? '£' : currency === 'INR' ? '₹' : currency === 'BDT' ? '৳' : '$';
    return `${symbol}${Number(n).toFixed(2)}`;
  }

  function fmt(n, digits = 2) {
    return Number(n).toLocaleString(undefined, { maximumFractionDigits: digits });
  }

  function summarize(item) {
    const pieces = [money(item.price, item.currency)];
    if (item.quantity?.display) pieces.push(item.quantity.display);
    if (item.calories) pieces.push(`${fmt(item.calories, 0)} cal`);
    if (item.promotion?.type && item.promotion.type !== 'none') pieces.push(item.promotion.label);
    const d = metricDescriptor(item, 'smart');
    if (d) pieces.push(d.label);
    return pieces.join(' · ');
  }

  const api = {
    normalizeSpace,
    parseNumber,
    extractPrices,
    chooseLikelyPrice,
    detectPromotion,
    parseQuantity,
    parseCalories,
    estimatePortionPoints,
    extractName,
    analyzeItem,
    rankItems,
    filterItems,
    minimumSpend,
    dedupeItems,
    canonicalName,
    metricDescriptor,
    determineSmartFamily,
    money,
    summarize,
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  globalThis.ValuePilotEngine = api;
})();
