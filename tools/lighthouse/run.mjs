// Lighthouse (mobile profile, simulated throttling) on the sign-in page, the award form and the award list.
// --runs n measures n times and prints the median of every category (scores swing between runs).
import { mkdirSync, writeFileSync } from 'node:fs';
import { startFlow } from 'lighthouse';

import { base, launch, signIn } from './browser.mjs';

const PASSING = 0.9;
const runsArg = process.argv.indexOf('--runs');
const runs = runsArg > 0 ? Number(process.argv[runsArg + 1]) : 1;
const scores = new Map();
let result;
let report;

for (let run = 1; run <= runs; run++) {
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

    result = await flow.createFlowResult();
    if (runs > 1) {
        console.log(`run ${run}/${runs}`);
    }
    for (const step of result.steps) {
        if (step.lhr.runtimeError) {
            console.log(`${step.name} not measured: ${step.lhr.runtimeError.code}; run again`);
            continue;
        }
        for (const category of Object.values(step.lhr.categories)) {
            const key = `${step.name} ${category.id}`;
            scores.set(key, [...(scores.get(key) ?? []), Math.round(category.score * 100)]);
        }
        const line = Object.values(step.lhr.categories)
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
        console.log(`${step.name} ${line} (${step.lhr.finalDisplayedUrl})`);
        console.log(`  below ${PASSING}: ${failing.join(', ') || 'none'}`);
    }
    if (run === runs) {
        report = await flow.generateReport();
    }
    await browser.close();
}

if (runs > 1) {
    console.log(`median of ${runs}`);
    for (const [key, values] of scores) {
        const sorted = [...values].sort((a, b) => a - b);
        console.log(
            `  ${key} ${sorted[Math.floor((sorted.length - 1) / 2)]} (${values.join(', ')})`,
        );
    }
}
console.log(`lighthouse ${result.steps[0].lhr.lighthouseVersion}`);
const out = new URL('../../build/lighthouse/', import.meta.url);
mkdirSync(out, { recursive: true });
writeFileSync(new URL('report.html', out), report);
console.log(`report: build/lighthouse/report.html${runs > 1 ? ' (last run)' : ''}`);
