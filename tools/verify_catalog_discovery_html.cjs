/* Render the shipped HTML against actual SQLite query responses. No prices or
 * offers are synthesized. This host adapter is test-only and not an APK asset. */
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const [modules, executablePath, baseURL, output] = process.argv.slice(2);
const { chromium } = require(path.join(modules, 'playwright'));

(async () => {
  const browser = await chromium.launch({ executablePath, headless: true });
  const page = await browser.newPage({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 1 });
  const errors = [];
  page.on('pageerror', error => errors.push(String(error)));
  const cases = [];
  try {
    await page.addInitScript(() => {
      let current = null;
      let request = null;
      let pageNumber = 0;
      let generation = 0;
      let cursors = [null];
      window.qaHost = { messages: [], errors: [], ready: false };
      const project = (result, previous) => ({
        mode: result.matches.length ? 'RESULTS' : 'NO_MATCHES',
        query: result.query, generation: ++generation,
        selectionId: request.selections[0] || null,
        page: pageNumber, previousPage: pageNumber ? pageNumber - 1 : null,
        hasMore: result.pagination.hasMore, nextPage: result.pagination.hasMore ? pageNumber + 1 : null,
        totalIdentityCount: result.total.sourceIdentityCount,
        clarification: previous || (result.clarification && {
          ...result.clarification,
          choices: result.clarification.choices.map(choice => ({ ...choice, label: choice.labelEs })),
        }),
        matches: result.matches.map(match => ({ ...match, matchLabel: 'provider identity match', offers: [] })),
      });
      window.ValuePilotHost = { postMessage(raw) {
        const message = JSON.parse(raw);
        window.qaHost.messages.push(message);
        if (message.type === 'OPEN_NATIVE_PRODUCT_DETAIL') {
          const match = current.matches.find(row => row.identityKey === message.identityKey);
          window.ValuePilotHtmlPreview.receiveNativeProductDetail({ match, query: current.query, generation });
          return;
        }
        if (!['SEARCH_VALUEPILOT', 'LOAD_MORE_VALUEPILOT', 'PREVIOUS_VALUEPILOT_PAGE', 'LOAD_MORE_VALUEPILOT_FACETS'].includes(message.type)) return;
        (async () => {
          const facet = message.type === 'LOAD_MORE_VALUEPILOT_FACETS';
          if (message.type === 'SEARCH_VALUEPILOT') {
            request = { query: message.query, language: message.language === 'en' ? 'en' : 'es',
              selections: message.selectionId ? [message.selectionId] : [], pageSize: 50, facetPageSize: 6 };
            pageNumber = 0;
            cursors = [null];
            current = null;
          } else if (!facet) pageNumber += message.type === 'PREVIOUS_VALUEPILOT_PAGE' ? -1 : 1;
          const query = { ...request, cursor: facet ? null : cursors[pageNumber],
            facetCursor: facet ? current.clarification.nextCursor : null };
          const response = await fetch('/qa/discovery', { method: 'POST', body: JSON.stringify(query) });
          if (!response.ok) throw new Error(await response.text());
          const result = await response.json();
          if (facet) {
            const projected = project(result);
            current = { ...current, generation: projected.generation, clarification: projected.clarification };
          } else {
            if (result.pagination.nextCursor) cursors[pageNumber + 1] = result.pagination.nextCursor;
            current = project(result, current?.clarification);
          }
          window.qaHost.current = current;
          window.qaHost.ready = true;
          window.ValuePilotHtmlPreview.receiveNativeSearch(current);
        })().catch(error => window.qaHost.errors.push(String(error)));
      }};
    });
    await page.goto(baseURL);
    await page.locator('#bottomNav [data-nav="search"]').click();
    const waitChange = async before => {
      await page.waitForFunction(before => window.qaHost.current?.generation > before || window.qaHost.errors.length, before);
      assert.deepEqual(await page.evaluate(() => window.qaHost.errors), []);
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    };
    const search = async (query, language) => {
      await page.evaluate(language => window.ValuePilotHtmlPreview.setLanguage(language), language);
      await page.locator('#searchQuery').fill(query);
      const before = await page.evaluate(() => window.qaHost.current?.generation || 0);
      await page.locator('#searchForm [type="submit"]').click();
      await waitChange(before);
    };
    for (const [language, query] of [['en', 'rice'], ['es-AR', 'arroz'], ['en', 'eggs'], ['es-AR', 'huevos'], ['en', 'strawberry'], ['es-AR', 'frutilla'], ['en', 'mango'], ['es-AR', 'zanahoria']]) {
      await search(query, language);
      const payload = await page.evaluate(() => window.qaHost.current);
      const names = await page.locator('.matrix-row .matrix-name').allTextContents();
      assert.equal(names.length, payload.matches.length);
      assert.deepEqual([...names].sort(), payload.matches.map(row => row.displayName).sort());
      assert.equal(await page.locator('.clarification-choice').count(), payload.clarification?.choices.length || 0);
      assert.ok(payload.clarification?.choices.length > 0, `${query}: no clarification`);
      if (query === 'rice' || query === 'arroz') {
        const before = payload.generation;
        const choice = page.locator('.clarification-choice[data-choice-id="form:rice:grain"]');
        await choice.click();
        await waitChange(before);
        const selected = await page.evaluate(() => window.qaHost.current);
        assert.equal(selected.selectionId, 'form:rice:grain');
        assert.ok(selected.matches.length > 0);
      }
      cases.push({ query, language, rowsRendered: names.length, totalIdentities: payload.totalIdentityCount,
        clarificationChoices: payload.clarification.choices.length, status: 'PASS' });
    }
    // A broad department has >6 choices and multiple product pages.
    await search('fruit', 'en');
    let payload = await page.evaluate(() => window.qaHost.current);
    assert.ok(payload.hasMore);
    const firstKeys = payload.matches.map(row => row.identityKey);
    if (payload.clarification?.hasMore) {
      await page.locator('[data-action="native-clarification-more"]').click();
      await waitChange(payload.generation);
      payload = await page.evaluate(() => window.qaHost.current);
      assert.deepEqual(payload.matches.map(row => row.identityKey), firstKeys);
      cases.push({ scenario: 'facet page preserves product rows', status: 'PASS' });
    } else throw new Error('fruit does not exercise >6 choices');
    await page.locator('[data-action="native-load-more"]').click();
    await waitChange(payload.generation);
    payload = await page.evaluate(() => window.qaHost.current);
    assert.equal(payload.page, 1);
    assert.ok(payload.matches.every(row => !firstKeys.includes(row.identityKey)));
    await page.locator('[data-action="native-previous-page"]').click();
    await waitChange(payload.generation);
    payload = await page.evaluate(() => window.qaHost.current);
    assert.equal(payload.page, 0);
    assert.deepEqual(payload.matches.map(row => row.identityKey), firstKeys);
    cases.push({ scenario: 'next and previous product pages', status: 'PASS' });
    // Table pan, detail identity, and host photo action preserve viewport.
    await search('rice', 'en');
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: path.join(output, 'clarification-light.jpg'), type: 'jpeg', quality: 90 });
    await page.locator('.clarification-choice[data-choice-id="form:rice:grain"]').click();
    await page.waitForFunction(() => window.qaHost.current.selectionId === 'form:rice:grain');
    await page.locator('#matrixScroll').evaluate(node => { node.scrollTop = 150; node.scrollLeft = 110; });
    const row = page.locator('.matrix-row').nth(3);
    const identity = await row.getAttribute('data-identity-key');
    const name = await row.locator('.matrix-name').textContent();
    const scroll = await page.locator('#matrixScroll').evaluate(node => ({ x: node.scrollLeft, y: node.scrollTop }));
    await row.press('Enter');
    await page.waitForFunction(() => window.ValuePilotHtmlPreview.page() === 'detail');
    assert.equal(await page.locator('.detail-title').textContent(), name);
    await page.locator('[data-action="photos"]').click();
    const photo = await page.evaluate(() => window.qaHost.messages.filter(row => row.type === 'OPEN_GOOGLE_IMAGES_NATIVE').at(-1));
    assert.equal(photo.name, name);
    await page.locator('[data-action="back"]').click();
    await page.waitForFunction(() => window.ValuePilotHtmlPreview.page() === 'search');
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const returned = await page.locator('#matrixScroll').evaluate(node => ({ x: node.scrollLeft, y: node.scrollTop }));
    assert.deepEqual(returned, scroll);
    assert.ok(await page.locator(`.matrix-row[data-identity-key="${identity}"]`).count());
    cases.push({ scenario: 'detail and photo host action return to exact identity and table viewport', status: 'PASS', identity });
    await page.locator('#matrixScroll').evaluate(node => { node.scrollTop = 0; node.scrollLeft = 0; });
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.locator('#toast').waitFor({ state: 'hidden' });
    await page.screenshot({ path: path.join(output, 'search-light.jpg'), type: 'jpeg', quality: 90 });
    await page.locator('#bottomNav [data-nav="home"]').click();
    await page.locator('#themeToggle').click();
    await page.locator('#bottomNav [data-nav="search"]').click();
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: path.join(output, 'search-dark.jpg'), type: 'jpeg', quality: 90 });
    await search('arroz', 'es-AR');
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: path.join(output, 'clarification-dark.jpg'), type: 'jpeg', quality: 90 });
    assert.deepEqual(errors, []);
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify({ status: 'PASS', ...JSON.parse(fs.readFileSync(path.join(output, 'input.json'))), cases, pageErrors: errors,
      limitations: ['Desktop browser host adapter; no Motorola connected.', 'External Custom Tab expansion and close require actual Android device.', 'Assigned semantics have no independent acceptance review.'] }, null, 2));
    console.log(`PASS: ${cases.length} rendered integration scenarios; JPGs in ${output}`);
  } catch (error) {
    await page.screenshot({ path: path.join(output, 'failure.jpg'), type: 'jpeg', quality: 85, fullPage: true });
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify({ status: 'FAIL', cases, error: String(error), pageErrors: errors }, null, 2));
    throw error;
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
