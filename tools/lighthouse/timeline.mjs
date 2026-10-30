// Cold load of one signed-in route at 4x CPU throttling: long tasks with the scripts they spent their time in, and
// every request with its timing relative to the navigation.
import { base, launch, signIn } from './browser.mjs';

const LONG_TASK_MS = 50;
const route = process.argv[2] ?? '/awards';
const browser = await launch();
const page = await browser.newPage();
await page.goto(`${base}/`);
await signIn(page);
await page.goto('about:blank');
const cdp = await page.createCDPSession();
await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });

const requests = [];
page.on('requestfinished', async (request) => {
    const response = request.response();
    const timing = response?.timing();
    const length = await response?.buffer().then(
        (body) => body.length,
        () => 0,
    );
    requests.push({
        url: request.url(),
        type: request.resourceType(),
        status: response?.status(),
        timing,
        length,
    });
});
await page.tracing.start({
    categories: ['devtools.timeline', 'disabled-by-default-devtools.timeline', 'v8'],
});
const started = Date.now();
await page.goto(`${base}${route}`, { waitUntil: 'networkidle0' });
const elapsed = Date.now() - started;
const events = JSON.parse(new TextDecoder().decode(await page.tracing.stop())).traceEvents;

const mainThreads = events.filter(
    (event) => event.name === 'thread_name' && event.args.name === 'CrRendererMain',
);
const busiest = mainThreads
    .map(({ pid, tid }) => ({
        pid,
        tid,
        busy: events
            .filter((event) => event.pid === pid && event.tid === tid && event.name === 'RunTask')
            .reduce((sum, event) => sum + (event.dur ?? 0), 0),
    }))
    .sort((a, b) => b.busy - a.busy)[0];
const onMain = events
    .filter((event) => event.pid === busiest.pid && event.tid === busiest.tid && event.ph === 'X')
    .sort((a, b) => a.ts - b.ts);
const origin = onMain[0]?.ts ?? 0;
const ms = (micros) => Math.round(micros / 1000);
const short = (url) => url.replace(base, '').split('?')[0] || url;

const longTasks = onMain.filter(
    (event) => event.name === 'RunTask' && event.dur / 1000 >= LONG_TASK_MS,
);
console.log(
    `${route}: loaded in ${elapsed} ms, ${longTasks.length} long tasks (>= ${LONG_TASK_MS} ms)`,
);
for (const task of longTasks) {
    const byScript = new Map();
    const byName = new Map();
    let coveredUntil = 0;
    for (const event of onMain) {
        if (event.ts <= task.ts || event.ts >= task.ts + task.dur) {
            continue;
        }
        byName.set(event.name, (byName.get(event.name) ?? 0) + (event.dur ?? 0));
        if (event.ts < coveredUntil) {
            continue;
        }
        const url = event.args?.data?.url;
        if (
            url &&
            ['EvaluateScript', 'FunctionCall', 'v8.compile', 'v8.compileModule'].includes(
                event.name,
            )
        ) {
            byScript.set(short(url), (byScript.get(short(url)) ?? 0) + event.dur);
            coveredUntil = event.ts + event.dur;
        }
    }
    const scripts = [...byScript.entries()]
        .sort((a, b) => b[1] - a[1])
        .slice(0, 4)
        .map(([url, micros]) => `${url} ${ms(micros)}`)
        .join(', ');
    const activities = [...byName.entries()]
        .sort((a, b) => b[1] - a[1])
        .slice(0, 3)
        .map(([name, micros]) => `${name} ${ms(micros)}`)
        .join(', ');
    console.log(
        `  at ${String(ms(task.ts - origin)).padStart(6)} ms ${String(ms(task.dur)).padStart(5)} ms  ` +
            `${scripts || `(no script: ${activities})`}`,
    );
}

const timed = requests
    .filter((request) => request.timing)
    .sort((a, b) => a.timing.requestTime - b.timing.requestTime);
const first = timed[0]?.timing.requestTime ?? 0;
console.log(`requests: ${requests.length} (start, headers after, size)`);
timed.forEach((request) => {
    const start = Math.round((request.timing.requestTime - first) * 1000);
    console.log(
        `  ${String(start).padStart(6)} ms ${String(Math.round(request.timing.receiveHeadersEnd)).padStart(5)} ms ` +
            `${String(Math.round(request.length / 1024)).padStart(5)} kB  ${String(request.status).padEnd(4)} ` +
            `${request.type.padEnd(10)} ${short(request.url)}`,
    );
});
await browser.close();
