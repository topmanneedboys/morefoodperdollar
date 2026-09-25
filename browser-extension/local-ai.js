/* ValuePilot compact local semantic model runtime. No network or remote model calls. */
(() => {
  'use strict';

  let model = globalThis.ValuePilotLocalAIModel;
  if (!model && typeof require === 'function') {
    try { model = require('./local-ai-model.js'); } catch {}
  }

  const vocabulary = new Set(model?.vocabulary || []);
  const stopWords = new Set(['and', 'with', 'the', 'for', 'from', 'style', 'fresh', 'classic', 'large', 'small', 'medium']);

  function tokenize(value) {
    const words = String(value ?? '')
      .normalize('NFKD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/&/g, ' and ')
      .match(/[a-z][a-z'-]{1,30}/g) || [];
    const clean = words
      .map(w => w.replace(/^'+|'+$/g, ''))
      .filter(w => w.length > 1 && !stopWords.has(w));
    const features = new Set(clean.map(w => `u:${w}`));
    for (let i = 0; i + 1 < clean.length; i++) features.add(`b:${clean[i]}_${clean[i + 1]}`);
    return [...features];
  }

  function emptyPrediction() {
    return {
      available: false,
      modelVersion: model?.modelVersion || 'unavailable',
      category: 'unknown',
      label: 'unknown',
      confidence: 0,
      foodConfidence: 0,
      porkConfidence: 0,
      meatRatio: 0,
      portionEligible: false,
      basePortionPoints: null,
      evidence: []
    };
  }

  function predict(text) {
    if (!model?.classes || !vocabulary.size) return emptyPrediction();
    const features = tokenize(text).filter(token => vocabulary.has(token));
    if (!features.length) return { ...emptyPrediction(), available: true };

    const rows = Object.entries(model.classes).map(([name, config]) => {
      let score = Number(config.logPrior || 0);
      for (const token of features) score += config.tokens[token] ?? config.unknownLogProbability;
      return { name, config, score };
    });
    const temperature = Number(model.temperature || 1);
    const maxScore = Math.max(...rows.map(row => row.score / temperature));
    let total = 0;
    for (const row of rows) {
      row.probability = Math.exp(row.score / temperature - maxScore);
      total += row.probability;
    }
    for (const row of rows) row.probability /= total || 1;
    rows.sort((a, b) => b.probability - a.probability || a.name.localeCompare(b.name));

    const top = rows[0];
    const evidenceStrength = Math.min(1, 0.52 + features.length * 0.16);
    const calibrated = top.probability * evidenceStrength;
    const strongestFoodProbability = Math.max(...rows.filter(row => row.config.food).map(row => row.probability), 0);
    const nonFoodProbability = rows.find(row => row.name === 'nonfood')?.probability || 0;
    const foodProbability = (strongestFoodProbability / Math.max(1e-9, strongestFoodProbability + nonFoodProbability)) * evidenceStrength;
    const porkProbability = (rows.find(row => row.name === 'pork')?.probability || 0) * evidenceStrength;
    const meatRatio = top.name === 'nonfood' ? 0 : Number(top.config.meatRatio || 0) * Math.min(1, 0.55 + calibrated);
    const evidence = features
      .filter(token => Object.hasOwn(top.config.tokens, token))
      .sort((a, b) => (top.config.tokens[b] - top.config.unknownLogProbability) - (top.config.tokens[a] - top.config.unknownLogProbability))
      .slice(0, 4)
      .map(token => token.slice(2).replace(/_/g, ' '));

    return {
      available: true,
      modelVersion: model.modelVersion,
      category: top.name,
      label: top.config.label,
      confidence: Number(calibrated.toFixed(4)),
      foodConfidence: Number(foodProbability.toFixed(4)),
      porkConfidence: Number(porkProbability.toFixed(4)),
      meatRatio: Number(meatRatio.toFixed(4)),
      portionEligible: Boolean(top.config.portionEligible && calibrated >= 0.26),
      basePortionPoints: top.config.portionEligible && calibrated >= 0.26
        ? Number(top.config.basePortionPoints)
        : null,
      evidence
    };
  }

  const api = { tokenize, predict, modelVersion: model?.modelVersion || 'unavailable' };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  globalThis.ValuePilotLocalAI = api;
})();
