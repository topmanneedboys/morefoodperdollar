(() => {
  'use strict';
  if (globalThis.__VALUEPILOT_CONTENT_V101__) return;
  globalThis.__VALUEPILOT_CONTENT_V101__ = true;

  const E = globalThis.ValuePilotEngine;
  if (!E) return;

  const state = {
    items: [],
    itemMap: new Map(),
    scanning: false,
    stopRequested: false,
    autoRefresh: true,
    mode: 'smart',
    lastScanAt: 0,
    panelOpen: false,
    mutationTimer: null,
    settings: {
      autoRefresh: true,
      maxItems: 500,
      autoScanDelayMs: 260,
      maxPrice: null,
      foodOnly: true,
      excludePork: false,
    },
    lastUrl: location.href,
  };

  const PRICE_TEXT_RE = /(?:\b(?:CA\$|C\$|US\$|A\$)|[$€£₹৳])\s*(?:\d{1,3}(?:[ ,]\d{3})+|\d{1,6})(?:[.,]\d{1,2})?|\b(?:\d{1,3}(?:[ ,]\d{3})+|\d{1,6})(?:[.,]\d{1,2})?\s*(?:CAD|USD|EUR|GBP|INR|BDT|AUD)\b/i;
  const SETTINGS_KEY = 'valuePilotSettingsV101';

  function safeText(el) {
    if (!el) return '';
    return E.normalizeSpace(el.innerText || el.textContent || '');
  }

  function elementRectOkay(el) {
    try {
      const r = el.getBoundingClientRect();
      return r.width >= 90 && r.height >= 28 && r.width <= Math.max(innerWidth * 1.5, 1200) && r.height <= Math.max(innerHeight * 1.8, 1600);
    } catch { return false; }
  }

  function cardScore(el, text) {
    if (!el || el === document.body || el === document.documentElement) return -999;
    const role = el.getAttribute?.('role') || '';
    const cls = `${el.className || ''} ${el.id || ''} ${el.getAttribute?.('data-testid') || ''}`.toLowerCase();
    let s = 0;
    const len = text.length;
    if (len >= 12 && len <= 700) s += 4;
    else if (len <= 1200) s += 1;
    else s -= 5;
    if (/article|listitem|option|menuitem/.test(role)) s += 4;
    if (/item|product|card|tile|menu|offer|catalog/.test(cls)) s += 3;
    if (el.querySelector?.('img, picture')) s += 2;
    if (el.querySelector?.('button, [role="button"]')) s += 1;
    const priceCount = E.extractPrices(text).length;
    if (priceCount === 1) s += 3;
    else if (priceCount <= 3) s += 1;
    else s -= 4;
    if (elementRectOkay(el)) s += 2;
    return s;
  }

  function findCardForPriceNode(node) {
    let el = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
    let best = null;
    let bestScore = -999;
    let depth = 0;
    while (el && depth < 9 && el !== document.body && el !== document.documentElement) {
      const text = safeText(el);
      if (text && PRICE_TEXT_RE.test(text)) {
        const score = cardScore(el, text) - depth * 0.12;
        if (score > bestScore) { best = el; bestScore = score; }
      }
      el = el.parentElement;
      depth++;
    }
    return bestScore >= 3 ? best : null;
  }

  function candidateElements(root = document) {
    const set = new Set();
    const likelySelectors = [
      '[data-testid*="item" i]', '[data-testid*="product" i]', '[data-testid*="menu" i]',
      '[class*="product" i]', '[class*="item" i]', '[class*="card" i]', '[role="listitem"]', 'article'
    ];
    try {
      root.querySelectorAll(likelySelectors.join(',')).forEach(el => {
        const text = safeText(el);
        if (text && PRICE_TEXT_RE.test(text) && text.length < 1500) set.add(el);
      });
    } catch {}

    const walker = document.createTreeWalker(root instanceof Document ? root.body : root, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        const t = node.nodeValue?.trim();
        if (!t || t.length > 150 || !PRICE_TEXT_RE.test(t)) return NodeFilter.FILTER_REJECT;
        const p = node.parentElement;
        if (!p || p.closest('#valuepilot-root, script, style, noscript, textarea, input')) return NodeFilter.FILTER_REJECT;
        return NodeFilter.FILTER_ACCEPT;
      }
    });
    let n;
    let count = 0;
    while ((n = walker.nextNode()) && count < 1000) {
      const card = findCardForPriceNode(n);
      if (card) set.add(card);
      count++;
    }
    return [...set];
  }

  function parseJsonLd() {
    const raws = [];
    document.querySelectorAll('script[type="application/ld+json"]').forEach(script => {
      try {
        const data = JSON.parse(script.textContent);
        const stack = Array.isArray(data) ? [...data] : [data];
        while (stack.length) {
          const obj = stack.shift();
          if (!obj || typeof obj !== 'object') continue;
          if (Array.isArray(obj)) { stack.push(...obj); continue; }
          const types = (Array.isArray(obj['@type']) ? obj['@type'] : [obj['@type']]).filter(Boolean).map(x => String(x).toLowerCase());
          if (types.some(type => type === 'product' || type === 'menuitem' || type === 'offer')) {
            const offers = obj.offers || obj;
            const offer = Array.isArray(offers) ? offers[0] : offers;
            const price = E.parseNumber(offer?.price ?? obj.price);
            const name = obj.name || offer?.name;
            if (price > 0 && name) {
              const extra = [obj.description, obj.weight, obj.size, obj.calories, offer?.description].filter(Boolean).join(' ');
              raws.push({ name, price, currency: offer?.priceCurrency || 'UNKNOWN', text: `${name}\n${extra}\n${price}`, sourceUrl: location.href });
            }
          }
          for (const v of Object.values(obj)) if (v && typeof v === 'object') stack.push(v);
        }
      } catch {}
    });
    return raws;
  }

  function ingestRaw(raw, source = 'dom') {
    const analyzed = E.analyzeItem(typeof raw === 'string' ? { text: raw, sourceUrl: location.href } : { ...raw, sourceUrl: raw.sourceUrl || location.href });
    if (!analyzed || analyzed.price <= 0 || analyzed.price > 100000) return;
    const text = analyzed.text.toLowerCase();
    // Suppress obvious cart/order totals that are not product cards.
    if (/\b(subtotal|order total|estimated total|tax|service fee|delivery fee|tip|checkout)\b/.test(text) && !analyzed.quantity && !analyzed.calories) return;
    const q = analyzed.quantity;
    const qk = q?.kind === 'mass' ? Math.round(q.grams) : q?.kind === 'volume' ? Math.round(q.ml) : q?.kind === 'count' ? q.count : q?.kind === 'pizza-area' ? Math.round(q.areaSqIn) : '';
    const key = `${E.canonicalName(analyzed.name)}|${analyzed.price.toFixed(2)}|${q?.kind || ''}|${qk}|${analyzed.promotion?.type || ''}`;
    const prev = state.itemMap.get(key);
    if (!prev || analyzed.confidence > prev.confidence || analyzed.text.length > prev.text.length) {
      state.itemMap.set(key, { ...analyzed, source });
    }
    if (state.itemMap.size > state.settings.maxItems * 2) {
      const keep = [...state.itemMap.entries()].slice(-state.settings.maxItems);
      state.itemMap.clear();
      keep.forEach(([k, value]) => state.itemMap.set(k, value));
    }
  }

  function scanLoaded(root = document, render = true) {
    if (location.href !== state.lastUrl) {
      state.itemMap.clear(); state.items = []; state.lastUrl = location.href;
    }
    const candidates = candidateElements(root);
    for (const el of candidates) {
      const text = safeText(el);
      if (text) ingestRaw({ text, sourceUrl: location.href }, 'dom');
    }
    for (const raw of parseJsonLd()) ingestRaw(raw, 'jsonld');
    state.items = E.dedupeItems([...state.itemMap.values()]).slice(0, state.settings.maxItems);
    state.lastScanAt = Date.now();
    if (render) renderPanel();
    updateFabCount();
    return state.items;
  }

  function discoverScrollers() {
    const out = [];
    const all = [...document.querySelectorAll('main, [role="main"], [role="feed"], [role="list"], div, section')];
    for (const el of all) {
      try {
        const cs = getComputedStyle(el);
        const scrollable = /(auto|scroll)/.test(cs.overflowY) && el.scrollHeight > el.clientHeight * 1.25 && el.clientHeight > 180;
        if (scrollable) out.push(el);
      } catch {}
    }
    const uniq = [...new Set(out)].sort((a, b) => (b.scrollHeight - b.clientHeight) - (a.scrollHeight - a.clientHeight));
    return uniq.slice(0, 3);
  }

  function wait(ms) { return new Promise(r => setTimeout(r, ms)); }

  async function loadSettings() {
    try {
      let stored = null;
      if (globalThis.browser?.storage?.local) {
        stored = (await globalThis.browser.storage.local.get(SETTINGS_KEY))?.[SETTINGS_KEY];
      } else if (globalThis.chrome?.storage?.local) {
        stored = await new Promise(resolve => globalThis.chrome.storage.local.get(SETTINGS_KEY, result => resolve(result?.[SETTINGS_KEY])));
      }
      if (!stored || typeof stored !== 'object') return;
      state.settings.maxPrice = Number(stored.maxPrice) > 0 ? Number(stored.maxPrice) : null;
      state.settings.foodOnly = stored.foodOnly !== false;
      state.settings.excludePork = stored.excludePork === true;
    } catch (error) {
      console.warn('[ValuePilot] Could not load local settings', error);
    }
  }

  async function saveSettings() {
    const value = {
      maxPrice: state.settings.maxPrice,
      foodOnly: state.settings.foodOnly,
      excludePork: state.settings.excludePork,
    };
    try {
      if (globalThis.browser?.storage?.local) await globalThis.browser.storage.local.set({ [SETTINGS_KEY]: value });
      else if (globalThis.chrome?.storage?.local) {
        await new Promise((resolve, reject) => globalThis.chrome.storage.local.set({ [SETTINGS_KEY]: value }, () => {
          const error = globalThis.chrome.runtime?.lastError;
          if (error) reject(error); else resolve();
        }));
      }
    } catch (error) {
      console.warn('[ValuePilot] Could not save local settings', error);
    }
  }

  async function scrollScanOne(target, progressCb) {
    const isDoc = target === document.scrollingElement || target === document.documentElement || target === document.body;
    const getTop = () => isDoc ? window.scrollY : target.scrollTop;
    const setTop = y => isDoc ? window.scrollTo({ top: y, behavior: 'instant' }) : (target.scrollTop = y);
    const maxScroll = () => isDoc ? Math.max(0, document.documentElement.scrollHeight - innerHeight) : Math.max(0, target.scrollHeight - target.clientHeight);
    const original = getTop();
    let noGrowthRounds = 0;
    let lastMax = maxScroll();
    let pos = 0;
    let steps = 0;

    try {
      setTop(0);
      await wait(120);
      scanLoaded(document, false);
      while (steps < 140 && !state.stopRequested) {
        const max = maxScroll();
        const viewport = isDoc ? innerHeight : target.clientHeight;
        const step = Math.max(180, Math.floor(viewport * 0.78));
        if (pos >= max - 5) {
          await wait(Math.max(160, state.settings.autoScanDelayMs));
          const grown = maxScroll();
          if (grown > lastMax + 100) {
            lastMax = grown;
            noGrowthRounds = 0;
          } else {
            noGrowthRounds++;
            if (noGrowthRounds >= 3) break;
          }
        }
        pos = Math.min(pos + step, maxScroll());
        setTop(pos);
        await wait(state.settings.autoScanDelayMs);
        scanLoaded(document, false);
        steps++;
        progressCb?.({ pos, max: maxScroll(), steps });
      }
    } finally {
      setTop(original);
      await wait(80);
    }
  }

  async function scanAll() {
    if (state.scanning) {
      state.stopRequested = true;
      setStatus('Stopping after this step; restoring your position…');
      return;
    }
    state.scanning = true;
    state.stopRequested = false;
    setStatus('Scanning loaded + lazy content…');
    const btn = shadow?.querySelector('#vp-scan-all');
    if (btn) btn.textContent = 'Stop';
    try {
      scanLoaded(document, false);
      const scrollRoot = document.scrollingElement || document.documentElement;
      const targets = [scrollRoot, ...discoverScrollers()].filter((x, i, a) => x && a.indexOf(x) === i).slice(0, 4);
      for (let i = 0; i < targets.length && !state.stopRequested; i++) {
        await scrollScanOne(targets[i], ({ pos, max }) => {
          const pct = max > 0 ? Math.min(100, Math.round(pos / max * 100)) : 100;
          setStatus(`Scanning section ${i + 1}/${targets.length} · ${pct}% · ${state.itemMap.size} items`);
        });
      }
      state.items = E.dedupeItems([...state.itemMap.values()]).slice(0, state.settings.maxItems);
      setStatus(`${state.stopRequested ? 'Scan stopped' : 'Scan complete'} · ${state.items.length} comparable items found`);
    } catch (err) {
      console.warn('[ValuePilot] scanAll failed', err);
      setStatus('Scan stopped; showing everything collected so far.');
    } finally {
      state.scanning = false;
      state.stopRequested = false;
      if (btn) btn.textContent = 'Scan all';
      renderPanel();
      updateFabCount();
    }
  }

  function setStatus(msg) {
    const el = shadow?.querySelector('#vp-status');
    if (el) el.textContent = msg;
  }

  let root = null;
  let shadow = null;

  function createUI() {
    root = document.createElement('div');
    root.id = 'valuepilot-root';
    root.style.all = 'initial';
    root.style.position = 'fixed';
    root.style.zIndex = '2147483647';
    root.style.right = '16px';
    root.style.bottom = '16px';
    root.style.fontFamily = 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
    shadow = root.attachShadow({ mode: 'open' });
    // eslint-disable-next-line no-unsanitized/property -- this template is static, extension-owned markup.
    shadow.innerHTML = `
      <style>
        :host { all: initial; }
        * { box-sizing: border-box; }
        button, select, input { font: inherit; }
        #vp-fab { width: 58px; height: 58px; border: 0; border-radius: 999px; background:#111827; color:#fff; box-shadow:0 8px 28px rgba(0,0,0,.28); cursor:pointer; font-weight:800; font-size:17px; position:relative; }
        #vp-count { position:absolute; right:-4px; top:-5px; background:#fff; color:#111827; min-width:22px; height:22px; border-radius:999px; border:2px solid #111827; display:flex; align-items:center; justify-content:center; padding:0 4px; font-size:10px; }
        #vp-panel { display:none; position:absolute; right:0; bottom:70px; width:min(470px, calc(100vw - 24px)); max-height:min(720px, calc(100vh - 100px)); overflow:hidden; background:#fff; color:#111827; border:1px solid rgba(0,0,0,.12); border-radius:18px; box-shadow:0 16px 50px rgba(0,0,0,.28); }
        #vp-panel.open { display:flex; flex-direction:column; }
        .head { padding:14px 14px 10px; border-bottom:1px solid #e5e7eb; background:linear-gradient(180deg,#fff,#f9fafb); }
        .titleRow { display:flex; align-items:center; gap:10px; }
        .title { font-weight:800; font-size:17px; flex:1; }
        .badge { font-size:10px; border:1px solid #d1d5db; padding:3px 6px; border-radius:999px; color:#4b5563; }
        .controls { display:flex; flex-wrap:wrap; gap:7px; margin-top:10px; align-items:center; }
        .controls button, .controls select, .controls input { border:1px solid #d1d5db; background:#fff; color:#111827; border-radius:10px; padding:7px 9px; font-size:12px; min-height:34px; }
        .controls button { cursor:pointer; }
        .controls button.primary { background:#111827; color:white; border-color:#111827; }
        .controls button:disabled { opacity:.55; cursor:not-allowed; }
        #vp-budget { width:92px; }
        .toggle { display:flex; align-items:center; gap:4px; font-size:11px; color:#4b5563; white-space:nowrap; }
        .toggle input { min-height:auto; accent-color:#111827; }
        #vp-status { margin-top:8px; font-size:11px; color:#6b7280; min-height:16px; }
        #vp-list { overflow:auto; padding:8px; }
        .item { border:1px solid #e5e7eb; border-radius:13px; padding:10px 11px; margin:7px 0; background:#fff; }
        .item.best { border-color:#111827; box-shadow:0 0 0 1px #111827 inset; }
        .row { display:flex; gap:9px; align-items:flex-start; }
        .rank { flex:0 0 28px; height:28px; border-radius:9px; background:#f3f4f6; display:flex; align-items:center; justify-content:center; font-size:12px; font-weight:800; }
        .name { flex:1; font-weight:700; font-size:13px; line-height:1.25; }
        .metric { margin-top:5px; font-size:12px; font-weight:700; }
        .meta { margin-top:4px; font-size:11px; line-height:1.35; color:#6b7280; }
        .promo { display:inline-block; margin-left:5px; padding:2px 5px; border-radius:6px; background:#f3f4f6; color:#374151; font-size:10px; font-weight:700; }
        .empty { padding:22px 14px; color:#6b7280; font-size:12px; line-height:1.5; text-align:center; }
        .footer { padding:8px 12px 10px; border-top:1px solid #e5e7eb; color:#6b7280; font-size:10px; line-height:1.35; }
        @media (prefers-color-scheme: dark) {
          #vp-panel,.item,.controls button,.controls select,.controls input { background:#111827; color:#f9fafb; border-color:#374151; }
          .head { background:#111827; border-color:#374151; } .title,.name {color:#f9fafb;} .meta,#vp-status,.footer {color:#9ca3af;} #vp-list {background:#0b1220;} .rank,.promo{background:#1f2937;color:#e5e7eb;} .item.best{border-color:#e5e7eb;box-shadow:0 0 0 1px #e5e7eb inset;}
        }
      </style>
      <button id="vp-fab" title="ValuePilot" aria-expanded="false" aria-controls="vp-panel">VP<span id="vp-count">0</span></button>
      <section id="vp-panel" aria-label="ValuePilot value rankings">
        <div class="head">
          <div class="titleRow"><div class="title">ValuePilot</div><span class="badge">private local AI · v101</span></div>
          <div class="controls">
            <select id="vp-mode" title="Ranking method">
              <option value="smart">Smart</option>
              <option value="mass">$/kg</option>
              <option value="volume">$/L</option>
              <option value="calorie">Calories/$</option>
              <option value="pizza">Pizza area/$</option>
              <option value="unit">$/unit</option>
              <option value="portion">AI food/$</option>
              <option value="meat">AI meat/$</option>
            </select>
            <button id="vp-scan" type="button">Scan loaded</button>
            <button class="primary" id="vp-scan-all" type="button">Scan all</button>
            <button id="vp-clear" type="button">Clear</button>
            <input id="vp-budget" type="number" inputmode="decimal" min="0" step="0.01" placeholder="$ budget" aria-label="Maximum total spend">
            <label class="toggle"><input id="vp-food-only" type="checkbox">Food only</label>
            <label class="toggle"><input id="vp-no-pork" type="checkbox">No pork</label>
          </div>
          <div id="vp-status" role="status" aria-live="polite">Ready</div>
        </div>
        <div id="vp-list"></div>
        <div class="footer">Exact measurements always beat AI estimates. AI food/meat scores are local, relative, and visibly labeled—not claimed grams. Scan all briefly scrolls lazy lists and restores your position.</div>
      </section>`;
    document.documentElement.appendChild(root);

    const budgetInput = shadow.querySelector('#vp-budget');
    const foodOnlyInput = shadow.querySelector('#vp-food-only');
    const noPorkInput = shadow.querySelector('#vp-no-pork');
    budgetInput.value = state.settings.maxPrice ?? '';
    foodOnlyInput.checked = state.settings.foodOnly;
    noPorkInput.checked = state.settings.excludePork;

    shadow.querySelector('#vp-fab').addEventListener('click', () => {
      state.panelOpen = !state.panelOpen;
      shadow.querySelector('#vp-panel').classList.toggle('open', state.panelOpen);
      shadow.querySelector('#vp-fab').setAttribute('aria-expanded', String(state.panelOpen));
      if (state.panelOpen) { scanLoaded(document, false); renderPanel(); }
    });
    shadow.querySelector('#vp-scan').addEventListener('click', () => { scanLoaded(); setStatus(`Loaded scan · ${state.items.length} items`); });
    shadow.querySelector('#vp-scan-all').addEventListener('click', scanAll);
    shadow.querySelector('#vp-clear').addEventListener('click', () => { state.itemMap.clear(); state.items = []; renderPanel(); updateFabCount(); setStatus('Cleared'); });
    shadow.querySelector('#vp-mode').addEventListener('change', e => { state.mode = e.target.value; renderPanel(); });
    budgetInput.addEventListener('input', () => {
      const value = Number(budgetInput.value);
      state.settings.maxPrice = Number.isFinite(value) && value > 0 ? value : null;
      renderPanel(); updateFabCount(); void saveSettings();
    });
    foodOnlyInput.addEventListener('change', () => {
      state.settings.foodOnly = foodOnlyInput.checked;
      renderPanel(); updateFabCount(); void saveSettings();
    });
    noPorkInput.addEventListener('change', () => {
      state.settings.excludePork = noPorkInput.checked;
      renderPanel(); updateFabCount(); void saveSettings();
    });
  }

  function updateFabCount() {
    const el = shadow?.querySelector('#vp-count');
    if (el) el.textContent = String(Math.min(999, filteredItems().length));
  }

  function filteredItems() {
    return E.filterItems(state.items, {
      maxPrice: state.settings.maxPrice,
      foodOnly: state.settings.foodOnly,
      excludePork: state.settings.excludePork,
    });
  }

  function makeElement(tag, { className, text } = {}) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
  }

  function renderPanel() {
    if (!shadow) return;
    const list = shadow.querySelector('#vp-list');
    const visible = filteredItems();
    const ranked = E.rankItems(visible, state.mode);
    list.replaceChildren();
    if (!ranked.length) {
      const filtered = state.items.length > 0;
      list.append(makeElement('div', {
        className: 'empty',
        text: filtered
          ? 'No items match the current budget or food filters.'
          : 'No comparable product cards found yet. Open a store or menu page, then choose Scan all.',
      }));
      setStatus(filtered ? `${state.items.length} collected · 0 match filters` : 'Ready');
      return;
    }
    const validCount = ranked.filter(x => x.rankingLabel !== 'price only').length;
    const modeName = ranked[0]?.rankingMode || state.mode;
    const hidden = Math.max(0, state.items.length - ranked.length);
    setStatus(`${ranked.length} shown${hidden ? ` · ${hidden} filtered` : ''} · ${validCount} comparable by ${modeName}`);
    ranked.slice(0, 100).forEach((item, i) => {
      const q = item.quantity?.display || 'quantity unknown';
      const cal = item.calories ? `${Math.round(item.calories)} cal` : '';
      const ai = item.ai?.confidence >= 0.26 ? ` · local AI: ${item.ai.label} ${Math.round(item.ai.confidence * 100)}%` : '';
      const spend = E.minimumSpend(item);
      const dealSpend = spend > item.price + 0.005 ? ` · deal spend ${E.money(spend, item.currency)}` : '';
      const itemBox = makeElement('div', {
        className: `item${i === 0 && item.rankingLabel !== 'price only' ? ' best' : ''}`,
      });
      const row = makeElement('div', { className: 'row' });
      row.append(
        makeElement('div', { className: 'rank', text: String(i + 1) }),
        makeElement('div', { className: 'name', text: item.name || 'Unnamed item' }),
      );
      const metric = makeElement('div', { className: 'metric', text: item.rankingLabel });
      if (item.promotion?.type !== 'none') {
        metric.append(makeElement('span', { className: 'promo', text: item.promotion.label }));
      }
      const meta = makeElement('div', {
        className: 'meta',
        text: `${E.money(item.price, item.currency)}${dealSpend} · ${q}${cal ? ` · ${cal}` : ''} · parse ${Math.round(item.confidence * 100)}%${ai}`,
      });
      itemBox.append(row, metric, meta);
      list.append(itemBox);
    });
  }

  function setupObserver() {
    const observer = new MutationObserver(mutations => {
      if (!state.settings.autoRefresh || state.scanning) return;
      let relevant = false;
      for (const m of mutations) {
        if (m.addedNodes?.length || m.type === 'characterData') { relevant = true; break; }
      }
      if (!relevant) return;
      clearTimeout(state.mutationTimer);
      state.mutationTimer = setTimeout(() => {
        if (Date.now() - state.lastScanAt > 600) scanLoaded(document, state.panelOpen);
      }, 450);
    });
    observer.observe(document.documentElement, { subtree: true, childList: true, characterData: true });
  }

  async function init() {
    if (!document.documentElement) return;
    await loadSettings();
    createUI();
    scanLoaded(document, false);
    setupObserver();
    updateFabCount();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => { void init(); }, { once: true });
  else void init();
})();
