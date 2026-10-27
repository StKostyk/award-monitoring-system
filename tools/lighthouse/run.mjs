// Lighthouse (mobile profile, simulated throttling) on the sign-in page, the award form and the award list.
import { mkdirSync, writeFileSync } from 'node:fs';
import { startFlow } from 'lighthouse';

import { base, launch, signIn } from './browser.mjs';

const PASSING = 0.9;
const browser = await launch();
const page = await browser.newPage();
const flow = await startFlow(page, {
  name: 'Phone',
  config: {
    extends: 'lighthouse:default',
    settings: {
      onlyCategories: ['performance', 'accessibility', 'best-practices'],
      disableStorageReset: true,
    },
  },
});

await flow.navigate(`${base}/login`, { name: '/login' });
await signIn(page);
await flow.navigate(`${base}/awards/new`, { name: '/awards/new' });
await flow.navigate(`${base}/awards`, { name: '/awards' });

const result = await flow.createFlowResult();
for (const step of result.steps) {
  if (step.lhr.runtimeError) {
    console.log(`${step.name} not measured: ${step.lhr.runtimeError.code}; run again`);
    continue;
  }
  const scores = Object.values(step.lhr.categories)
    .map((category) => `${category.id}=${Math.round(category.score * 100)}`)
    .join(' ');
  const failing = Object.values(step.lhr.audits)
    .filter(
      (audit) =>
        audit.score !== null &&
        audit.score < PASSING &&
        !['informative', 'manual'].includes(audit.scoreDisplayMode),
    )
    .map((audit) => `${audit.id}:${audit.score}`);
  console.log(`${step.name} ${scores} (${step.lhr.finalDisplayedUrl})`);
  console.log(`  below ${PASSING}: ${failing.join(', ') || 'none'}`);
}
console.log(`lighthouse ${result.steps[0].lhr.lighthouseVersion}`);
const out = new URL('../../build/lighthouse/', import.meta.url);
mkdirSync(out, { recursive: true });
writeFileSync(new URL('report.html', out), await flow.generateReport());
console.log('report: build/lighthouse/report.html');
await browser.close();
