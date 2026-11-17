// Loads every module the dev server serves, starting at index.html, so that the Vite dependency optimizer
// discovers all dependencies before the first test. A dependency it finds late answers "504 Outdated Optimize Dep"
// for as long as that server runs (the Angular chunks keep the old dependency hash), so lazy routes fail to load;
// the optimizer has written the new dependency to its cache by then, and a restarted server serves it.
// Usage: node tools/e2e-warmup.mjs [baseUrl]; exit 0 when no module answered 504, 1 when the server needs a restart.

const base = process.argv[2] ?? 'http://localhost:4200';
const settle = 5000;
const importPattern = /(?:\bfrom\s*|\bimport\s*\(?\s*)["']([^"']+)["']|<script[^>]+src=["']([^"']+)["']/g;
const origin = new URL(base).origin;
const seen = new Set();
const queue = [new URL('/', base).href];
const outdated = [];

while (queue.length) {
  const url = queue.shift();
  if (seen.has(url)) {
    continue;
  }
  seen.add(url);
  const response = await fetch(url);
  if (response.status === 504) {
    outdated.push(url);
    continue;
  }
  if (!response.ok) {
    continue;
  }
  const text = await response.text();
  for (const match of text.matchAll(importPattern)) {
    const target = new URL(match[1] ?? match[2], url);
    if (target.origin === origin && !seen.has(target.href)) {
      queue.push(target.href);
    }
  }
}

if (!outdated.length) {
  console.log(`Dev server warm: ${seen.size} modules loaded without an outdated dependency.`);
  process.exit(0);
}
for (const url of outdated) {
  console.log(`Outdated dependency: ${url.replace(/^.*\/deps\//, '')}`);
}
await new Promise((resolve) => setTimeout(resolve, settle));
process.exit(1);
