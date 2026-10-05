import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';

import { kyivDay, pastDay, shownDay, signIn, signedIn, uniqueToken } from './helpers';

const demo = 'Passw0rd-demo';
const employee = 'employee.fmi@chnu.edu.ua';
const dean = 'dean.fmi@chnu.edu.ua';
const megabyte = 1024 * 1024;
/** The frontend nginx in front of the local backend, started by `tools/e2e.ps1`. */
const nginx = process.env['E2E_NGINX_URL'];
/** The EICAR anti-virus test file: harmless, detected by every scanner. */
const eicar = 'X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*';
const tinyPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);

function pdf(size = 2048): Buffer {
  const content = Buffer.alloc(size, 0x20);
  content.write(`%PDF-1.4\n% ${uniqueToken()}\n`);
  return content;
}

function jpeg(): Buffer {
  return Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.from(uniqueToken())]);
}

async function newAward(page: Page, title: string): Promise<void> {
  await page.goto('/awards/new');
  await page.getByTestId('award-title-uk').fill(title);
}

function headerValues(headers: { name: string; value: string }[], name: string): string[] {
  return headers
    .filter((header) => header.name.toLowerCase() === name)
    .map((header) => header.value);
}

function row(page: Page, name: string) {
  return page.getByTestId('documents-list').locator('li').filter({ hasText: name });
}

async function accessible(page: Page): Promise<void> {
  const result = await new AxeBuilder({ page })
    .include('[data-testid="award-documents"]')
    .analyze();
  expect(result.violations.map((violation) => violation.id)).toEqual([]);
}

test.describe('award documents', () => {
  test('ac2_1 to ac2_7 ac2_9 ac2_11 the owner attaches a certificate and a dean downloads it', async ({
    browser,
  }) => {
    const title = `Грамота з документами ${uniqueToken()}`;
    const page = await signedIn(browser, employee);
    let uploads = 0;
    page.on('request', (request) => {
      if (request.method() === 'POST' && /\/documents$/.test(request.url())) {
        uploads++;
      }
    });
    await newAward(page, title);
    const section = page.getByTestId('award-documents');
    await expect(section).toContainText('Перетягніть файли сюди або оберіть файл');
    await expect(section).toContainText('PDF, JPG, PNG або WEBP, до 10 МБ, не більше 10 файлів');
    await expect(page.getByTestId('documents-type')).toContainText('Сертифікат');

    const input = page.getByTestId('documents-input');
    await input.setInputFiles({ name: 'диплом.pdf', mimeType: 'application/pdf', buffer: pdf() });
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    const id = /\/awards\/(\d+)\/edit/.exec(page.url())?.[1] ?? '';
    await expect(row(page, 'диплом.pdf')).toContainText('Сертифікат');
    await expect(row(page, 'диплом.pdf')).toContainText('2 КБ');
    await expect(row(page, 'диплом.pdf')).toContainText(shownDay(kyivDay()));
    await expect(page.getByTestId('documents-announcement')).toHaveText('Завантажено: диплом.pdf');
    await expect(page.getByTestId('documents-type')).toContainText('Додатковий документ');

    await input.setInputFiles([
      { name: 'фото.png', mimeType: 'image/png', buffer: tinyPng },
      { name: 'грамота.jpg', mimeType: 'image/jpeg', buffer: jpeg() },
    ]);
    await expect(row(page, 'грамота.jpg')).toContainText('Додатковий документ');
    await expect(row(page, 'фото.png')).toContainText('Додатковий документ');
    expect(uploads).toBe(3);

    await input.setInputFiles([
      { name: 'звіт.docx', mimeType: 'application/octet-stream', buffer: Buffer.from('PK') },
      { name: 'великий.pdf', mimeType: 'application/pdf', buffer: pdf(12 * megabyte) },
    ]);
    const queue = page.getByTestId('documents-queue');
    await expect(queue.locator('li').filter({ hasText: 'звіт.docx' })).toContainText(
      'Непідтримуваний формат файлу',
    );
    await expect(queue.locator('li').filter({ hasText: 'великий.pdf' })).toContainText(
      'Файл більший за 10 МБ',
    );
    await expect(queue.getByTestId('documents-retry')).toHaveCount(0);
    expect(uploads).toBe(3);
    await accessible(page);
    await queue.getByTestId('documents-dismiss').first().click();
    await queue.getByTestId('documents-dismiss').click();
    await expect(queue).toHaveCount(0);

    await input.setInputFiles({
      name: 'копія.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.concat([tinyPng, Buffer.from(uniqueToken())]),
    });
    await expect(page.getByTestId('documents-queued-problem')).toHaveText(
      'Вміст файлу не відповідає його розширенню',
    );
    await page.getByTestId('documents-dismiss').click();

    await row(page, 'фото.png').getByTestId('document-name').click();
    await expect(page.getByTestId('document-preview-image')).toBeVisible();
    await page.getByTestId('document-preview-close').click();

    const download = page.waitForEvent('download');
    await row(page, 'диплом.pdf').getByTestId('document-download').click();
    expect((await download).suggestedFilename()).toBe('диплом.pdf');

    await row(page, 'фото.png').getByTestId('document-remove').click();
    await expect(page.getByRole('dialog')).toContainText('Видалити документ «фото.png»?');
    await page.getByTestId('confirm-accept').click();
    await expect(row(page, 'фото.png')).toHaveCount(0);

    await page.getByTestId('award-category').click();
    await page.getByTestId('category-option-13').click();
    await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
    await page.getByTestId('award-date').fill(pastDay());
    await page.getByTestId('award-submit').click();
    await expect(page.getByTestId('award-submitted-name')).toContainText(title);

    await page.goto(`/awards/${id}`);
    await expect(page.getByTestId('documents-list').locator('li')).toHaveCount(2);
    await expect(page.getByTestId('document-remove')).toHaveCount(0);
    await expect(page.getByTestId('documents-drop')).toHaveCount(0);

    const reader = await signedIn(browser, dean);
    await reader.goto(`/awards/${id}`);
    await expect(reader.getByTestId('documents-list').locator('li')).toHaveCount(2);
    await expect(reader.getByTestId('document-remove')).toHaveCount(0);
    const tokenInUrl = reader.waitForRequest((request) =>
      /\/api\/v1\/documents\/\d+$/.test(request.url()),
    );
    const saved = reader.waitForEvent('download');
    await row(reader, 'диплом.pdf').getByTestId('document-download').click();
    expect((await tokenInUrl).url()).not.toContain('token');
    expect((await saved).suggestedFilename()).toBe('диплом.pdf');
    await accessible(reader);
  });

  test('ac2_9 a document removed elsewhere is no longer available', async ({ browser }) => {
    const page = await signedIn(browser, employee);
    await newAward(page, `Грамота ${uniqueToken()}`);
    await page.getByTestId('documents-input').setInputFiles({
      name: 'диплом.pdf',
      mimeType: 'application/pdf',
      buffer: pdf(),
    });
    await expect(row(page, 'диплом.pdf')).toBeVisible();
    const id = /\/awards\/(\d+)\/edit/.exec(page.url())?.[1] ?? '';

    const other = await (await browser.newContext()).newPage();
    await signIn(other, employee, demo);
    await other.goto(`/awards/${id}`);
    await row(other, 'диплом.pdf').getByTestId('document-remove').click();
    await other.getByTestId('confirm-accept').click();
    await expect(other.getByTestId('documents-empty')).toBeVisible();

    await row(page, 'диплом.pdf').getByTestId('document-download').click();
    await expect(page.getByTestId('documents-notice')).toHaveText('Документ більше не доступний');
    await expect(page.getByTestId('documents-empty')).toBeVisible();
  });

  test('ac2_10 ac2_11 the section speaks English and the drop zone opens the picker from the keyboard', async ({
    browser,
  }) => {
    const page = await signedIn(browser, employee);
    await page.getByTestId('language-toggle').click();
    await newAward(page, `Certificate ${uniqueToken()}`);
    const section = page.getByTestId('award-documents');
    await expect(section).toContainText('Documents');
    await expect(section).toContainText('Drag files here or choose a file');
    await expect(section).toContainText('PDF, JPG, PNG or WEBP, up to 10 MB, at most 10 files');
    await expect(page.getByTestId('documents-type')).toContainText('Certificate');
    await expect(page.getByTestId('documents-empty')).toHaveText('No documents attached');

    await page.getByTestId('documents-input').setInputFiles({
      name: 'scan.heic',
      mimeType: 'image/heic',
      buffer: Buffer.from('heic'),
    });
    await expect(page.getByTestId('documents-queued-problem')).toHaveText(
      'Unsupported file format',
    );
    await expect(page.getByTestId('documents-dismiss')).toHaveText('Dismiss');

    await page.getByTestId('documents-drop').focus();
    const chooser = page.waitForEvent('filechooser');
    await page.keyboard.press('Enter');
    await (
      await chooser
    ).setFiles({ name: 'scan.pdf', mimeType: 'application/pdf', buffer: pdf() });
    await expect(page.getByTestId('documents-announcement')).toHaveText('Uploaded: scan.pdf');
    await expect(row(page, 'scan.pdf')).toContainText('Certificate');

    await page.getByTestId('award-category').click();
    await page.getByTestId('category-option-13').click();
    await page.getByTestId('award-organization').fill('Ministry of Education and Science');
    await page.getByTestId('award-date').fill(pastDay());
    await page.getByTestId('award-remove').focus();
    await page.keyboard.press('Shift+Tab');
    await expect(row(page, 'scan.pdf').getByTestId('document-remove')).toBeFocused();
    await accessible(page);
  });

  test('ac1_16 finding5 a 9.5 MB upload passes the frontend nginx, a larger body is refused there and the download keeps the backend headers', async ({
    browser,
  }) => {
    test.skip(!nginx, 'E2E_NGINX_URL is set by tools/e2e.ps1');
    const page = await signedIn(browser, employee);
    await newAward(page, `Велика грамота ${uniqueToken()}`);
    await page.getByTestId('award-save').click();
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    const id = /\/awards\/(\d+)\/edit/.exec(page.url())?.[1] ?? '';
    const token = await page.evaluate(() => sessionStorage.getItem('access_token'));
    const send = (size: number) =>
      page.request.post(`${nginx}/api/v1/awards/${id}/documents`, {
        headers: { Authorization: `Bearer ${token}` },
        multipart: {
          type: 'CERTIFICATE',
          file: { name: 'скан.pdf', mimeType: 'application/pdf', buffer: pdf(size) },
        },
        timeout: 30_000,
      });

    try {
      const accepted = await send(9.5 * megabyte);
      expect(accepted.status()).toBe(201);
      const download = await page.request.get(
        `${nginx}/api/v1/documents/${(await accepted.json()).id}`,
        {
          headers: { Authorization: `Bearer ${token}` },
        },
      );
      expect(download.status()).toBe(200);
      expect(headerValues(download.headersArray(), 'content-security-policy')).toEqual(['sandbox']);
      expect(headerValues(download.headersArray(), 'x-content-type-options')).toEqual(['nosniff']);
      const site = await page.request.get(`${nginx}/`);
      expect(headerValues(site.headersArray(), 'content-security-policy')[0]).toContain(
        "default-src 'self'",
      );
      const refused = await send(12 * megabyte);
      expect(refused.status()).toBe(413);
      expect((await refused.json()).type).toBe('urn:awards:problem:file-too-large');
    } finally {
      await page.request.delete(`${nginx}/api/v1/awards/${id}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
    }
  });

  test('finding1 a draft deleted in another window is saved again when its file is retried', async ({
    browser,
  }) => {
    const page = await signedIn(browser, employee);
    await newAward(page, `Грамота з двох вікон ${uniqueToken()}`);
    await page.getByTestId('documents-input').setInputFiles({
      name: 'перший.pdf',
      mimeType: 'application/pdf',
      buffer: pdf(),
    });
    await expect(row(page, 'перший.pdf')).toBeVisible();
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    const deleted = /\/awards\/(\d+)\/edit/.exec(page.url())?.[1] ?? '';
    const other = await signedIn(browser, employee);
    await other.goto(`/awards/${deleted}`);
    await other.getByTestId('award-remove').click();
    await other.getByTestId('confirm-accept').click();
    await expect(other).toHaveURL(/\/awards$/);

    await page.getByTestId('documents-input').setInputFiles({
      name: 'другий.pdf',
      mimeType: 'application/pdf',
      buffer: pdf(),
    });

    await expect(page.getByTestId('documents-queued-problem')).toHaveText(
      'Чернетку видалено в іншому вікні. Спробуйте ще раз, щоб зберегти нову чернетку',
    );
    await expect(page).toHaveURL(/\/awards\/new$/);
    await expect(row(page, 'перший.pdf')).toHaveCount(0);
    await page.getByTestId('documents-retry').click();
    await expect(row(page, 'другий.pdf')).toBeVisible();
    await expect(page).toHaveURL(/\/awards\/\d+\/edit$/);
    const saved = /\/awards\/(\d+)\/edit/.exec(page.url())?.[1] ?? '';
    expect(saved).not.toBe(deleted);
    await other.goto(`/awards/${saved}`);
    await other.getByTestId('award-remove').click();
    await other.getByTestId('confirm-accept').click();
    await expect(other).toHaveURL(/\/awards$/);
  });

  test('ac3_1 ac3_4 a file with malware is refused and nothing is attached', async ({
    browser,
  }) => {
    const page = await signedIn(browser, employee);
    await newAward(page, `Грамота з вірусом ${uniqueToken()}`);

    await page.getByTestId('documents-input').setInputFiles({
      name: 'eicar.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from(eicar, 'ascii'),
    });

    await expect(page.getByTestId('documents-queued-problem')).toHaveText(
      'Файл містить шкідливий код і не був завантажений',
    );
    await expect(page.getByTestId('documents-retry')).toHaveCount(0);
    await page.getByTestId('documents-dismiss').click();
    await expect(page.getByTestId('documents-empty')).toBeVisible();
  });
});
