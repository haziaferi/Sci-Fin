// Runs the prototype's seeding headlessly and writes the parity fixture the Kotlin engine is
// checked against (Stage 1, step 3). The prototype is the second oracle, not the source: where
// the two disagree the spec decides and §13.4 records it.
//
// Node has no DOM, so the module runs against a permissive stub: every element is a Proxy that
// accepts any property, returns itself for any method, and stores innerHTML/textContent. That is
// enough for render() to run to completion; nothing about the figures depends on layout.
//
//   node tools/export_fixture.mjs            -> native/shared/src/desktopTest/resources/prototype-seed.json
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const html = fs.readFileSync(path.join(root, 'sci-fin-prototype.html'), 'utf8');
const src = /<script type="module">([\s\S]*?)<\/script>/.exec(html)[1];

function makeElement(tag = 'div') {
  const store = { innerHTML: '', textContent: '', value: '', className: '', children: [], attrs: {} };
  const classes = new Set();
  const el = new Proxy(function () {}, {
    get(_, k) {
      if (k === 'innerHTML' || k === 'textContent' || k === 'innerText' || k === 'value' || k === 'className') return store[k];
      if (k === 'style') return store.style ||= new Proxy({}, { get: (_, p) => (p === 'setProperty' || p === 'removeProperty' || p === 'getPropertyValue') ? () => '' : '', set: () => true });
      if (k === 'dataset') return store.dataset ||= {};
      if (k === 'classList') return { add: (...c) => c.forEach(x => classes.add(x)), remove: (...c) => c.forEach(x => classes.delete(x)), contains: c => classes.has(c), toggle: c => classes.has(c) ? (classes.delete(c), false) : (classes.add(c), true) };
      if (k === 'children' || k === 'childNodes') return [];
      if (k === 'querySelector' || k === 'closest' || k === 'getAttribute') return () => null;
      if (k === 'querySelectorAll' || k === 'getElementsByClassName') return () => [];
      if (k === 'getBoundingClientRect') return () => ({ top: 0, left: 0, width: 0, height: 0, right: 0, bottom: 0 });
      if (k === 'matches') return () => false;
      if (k === 'hasAttribute') return () => false;
      if (k === 'tagName') return tag.toUpperCase();
      if (k === 'nodeType') return 1;
      if (k === 'ownerDocument') return document;
      if (k === Symbol.toPrimitive) return () => '';
      if (typeof k === 'symbol') return undefined;
      return (..._) => el;                    // any method: chainable no-op
    },
    set(_, k, v) { store[k] = v; return true; },
    has() { return true; },
  });
  return el;
}

const elements = new Map();
const document = {
  getElementById: id => elements.get(id) || (elements.set(id, makeElement()), elements.get(id)),
  querySelector: () => null,
  querySelectorAll: () => [],
  createElement: tag => makeElement(tag),
  addEventListener() {},
  removeEventListener() {},
  body: makeElement('body'),
  documentElement: makeElement('html'),
  activeElement: null,
  hidden: true,
  visibilityState: 'hidden',
};
const window = {
  document,
  addEventListener() {},
  removeEventListener() {},
  matchMedia: () => ({ matches: false, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {} }),
  requestAnimationFrame: () => 0,
  cancelAnimationFrame() {},
  setTimeout, clearTimeout, setInterval, clearInterval,
  innerWidth: 400, innerHeight: 800,
  location: { href: 'file://prototype', reload() {} },
  navigator: { language: 'en-GB', userAgent: 'node' },
  localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
  URL: { createObjectURL: () => 'blob:', revokeObjectURL() {} },
  Blob: class { constructor(parts) { this.parts = parts; } },
  CSS: { escape: s => s },
  console,
};
window.window = window;
window.globalThis = window;
const context = vm.createContext(Object.assign(window, { Intl, Date, Math, JSON, Number, String, Array, Object, Map, Set, Promise, Symbol, Error, parseFloat, parseInt, isNaN, isFinite, RegExp, FileReader: class {}, KeyboardEvent: class {}, Event: class {}, HTMLAnchorElement: class {}, HTMLElement: class {},
  ResizeObserver: class { observe() {} disconnect() {} }, MutationObserver: class { observe() {} disconnect() {} }, getComputedStyle: () => new Proxy({}, { get: () => '' }) }));

// The module's top-level `const`/`let`/function declarations are module-scoped; append an
// export of what the fixture needs so it is reachable from the context afterwards.
const script = src + `
;globalThis.__fixture = () => ({
  simDate: isoDay(state.simDate),
  savings: state.savings,
  transactions: state.transactions,
  goals: state.goals, categories: state.categories,
  recurringExpenses: state.recurringExpenses, accounts: state.accounts,
  settings: { incomeEnabled: state.incomeEnabled, income: state.income, incomeCadence: state.incomeCadence,
              incomePayDate: state.incomePayDate, incomeEnabledDate: state.incomeEnabledDate },
  figures: {
    incomeReceivedThisMonth: incomeReceivedThisMonth(),
    ageOfMoney: computeAgeOfMoney(today()),
    ageOfMoneyTrend: ageOfMoneyTrend(6).map(p => p.days),
    savingsVsOwedNow: savingsVsOwedNow(),
    savingsVsOwedTrend: savingsVsOwedTrend(6).map(p => p.value),
    spending: spendingComposition(),
    buffer: coverageDaysOrNull(),
    trailingSpend: (w => ({ start: isoDay(w.start), end: isoDay(w.end), days: w.days }))(trailingSpendWindow()),
    pairEligibility: [...computePairEligibility().entries()],
  },
});`;

new vm.Script(script, { filename: 'sci-fin-prototype.html' }).runInContext(context);
const fixture = context.__fixture();
const out = path.join(root, 'native', 'shared', 'src', 'desktopTest', 'resources', 'prototype-seed.json');
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, JSON.stringify(fixture, null, 2) + '\n');
console.log(`fixture: ${fixture.transactions.length} transactions, savings ${fixture.savings}, simDate ${fixture.simDate} -> ${path.relative(root, out)}`);
