import { existsSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import puppeteer from 'puppeteer-core';

export const base = process.env.LH_BASE_URL ?? 'http://localhost';
const email = process.env.LH_EMAIL ?? 'employee.fmi@chnu.edu.ua';
const password = process.env.LH_PASSWORD ?? 'Passw0rd-demo';

/** The newest Chromium of the Playwright browser folder, unless CHROME_PATH names another browser. */
function chromePath() {
  if (process.env.CHROME_PATH) {
    return process.env.CHROME_PATH;
  }
  const folder = process.env.PLAYWRIGHT_BROWSERS_PATH ?? 'D:/Tools/ms-playwright';
  const builds = readdirSync(folder)
    .filter((name) => /^chromium-\d+$/.test(name))
    .sort((a, b) => Number(b.split('-')[1]) - Number(a.split('-')[1]));
  const found = builds
    .map((name) => join(folder, name, 'chrome-win64', 'chrome.exe'))
    .find((path) => existsSync(path));
  if (!found) {
    throw new Error(`No Chromium under ${folder}; set CHROME_PATH`);
  }
  return found;
}

export function launch() {
  return puppeteer.launch({ executablePath: chromePath(), headless: true, args: ['--no-sandbox'] });
}

export async function signIn(page) {
  await page.waitForSelector('#username');
  await page.type('#username', email);
  await page.type('#password', password);
  await Promise.all([page.waitForNavigation(), page.click('button[type="submit"]')]);
  await page.waitForSelector('[data-testid="nav-awards"]', { timeout: 30000 });
}
