// CPU profile of a cold load of one signed-in route at 4x CPU throttling: the 25 functions with the most self time.
import { base, launch, signIn } from './browser.mjs';

const route = process.argv[2] ?? '/awards/new';
const browser = await launch();
const page = await browser.newPage();
await page.goto(`${base}/`);
await signIn(page);
const cdp = await page.createCDPSession();
await cdp.send('Profiler.enable');
await cdp.send('Profiler.setSamplingInterval', { interval: 200 });
await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
await cdp.send('Profiler.start');
await page.goto(`${base}${route}`, { waitUntil: 'networkidle0' });
const { profile } = await cdp.send('Profiler.stop');
const byId = new Map(profile.nodes.map((node) => [node.id, node]));
const self = new Map();
profile.samples.forEach((id, index) => {
  const frame = byId.get(id).callFrame;
  const key = `${frame.functionName || '(anon)'} ${frame.url.split('/').pop()}:${frame.lineNumber}:${frame.columnNumber}`;
  self.set(key, (self.get(key) ?? 0) + (profile.timeDeltas[index] ?? 0) / 1000);
});
[...self.entries()]
  .sort((a, b) => b[1] - a[1])
  .slice(0, 25)
  .forEach(([key, ms]) => console.log(String(Math.round(ms)).padStart(6), key));
await browser.close();
