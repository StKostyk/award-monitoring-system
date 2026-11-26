import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from './fixtures';

import {
  FRESH_PASSWORD,
  expireOpenRequests,
  freshEmployee,
  openNewAward,
  pastDay,
  settled,
  signedInAs,
  sql,
  toggleLanguage,
  uniqueToken,
} from './helpers';

/** A faculty without seed reviewers and one of its departments. */
const FACULTY = 5;
const DEPARTMENT = 41;
/** A university category: the faculty secretary reviews it. */
const UNIVERSITY = 21;
const PHONE_WIDTH = 360;

function user(email: string): string {
  return `(select user_id from users where email_address = '${email}')`;
}

/** A valid PDF with two A4 pages of text in a standard font, unique per call. */
function twoPagePdf(): Buffer {
  const text = (line: string) => `BT /F1 40 Tf 72 720 Td (${line}) Tj ET`;
  const stream = (content: string) =>
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`;
  const page = (contents: number) =>
    `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents ${contents} 0 R ` +
    '/Resources << /Font << /F1 7 0 R >> >> >>';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>',
    page(4),
    stream(text('Page one')),
    page(6),
    stream(text('Page two')),
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let body = `%PDF-1.4\n% ${uniqueToken()}\n`;
  const offsets = objects.map((object, index) => {
    const offset = Buffer.byteLength(body);
    body += `${index + 1} 0 obj\n${object}\nendobj\n`;
    return offset;
  });
  const xref = Buffer.byteLength(body);
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  body += offsets.map((offset) => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(body, 'latin1');
}

/** Starts with the PDF signature, so the upload accepts it, but has no readable structure. */
function brokenPdf(): Buffer {
  const content = Buffer.alloc(2048, 0x20);
  content.write(`%PDF-1.4\n% ${uniqueToken()}\n`);
  return content;
}

function row(page: Page, name: string) {
  return page.getByTestId('documents-list').locator('li').filter({ hasText: name });
}

/** Counts the requests for the pdf.js worker, which only screens with a document load. */
function workerRequests(page: Page): () => number {
  let count = 0;
  page.on('request', (request) => {
    if (request.url().includes('/pdfjs/pdf.worker')) {
      count++;
    }
  });
  return () => count;
}

async function dialogViolations(page: Page): Promise<string[]> {
  await settled(page);
  const result = await new AxeBuilder({ page }).include('mat-dialog-container').analyze();
  return result.violations.map((violation) => violation.id);
}

test.describe('9.2.6 document preview', () => {
  let owner: string;
  let reviewer: string;

  test.beforeAll(async ({ browser }) => {
    owner = await freshEmployee(browser, 'previewowner');
    reviewer = await freshEmployee(browser, 'previewreviewer');
    sql(`update users set organization_id = ${DEPARTMENT} where user_id = ${user(owner)}`);
    sql(`update user_roles set organization_id = ${DEPARTMENT} where user_id = ${user(owner)}`);
    sql(
      `insert into user_roles (user_id, organization_id, role_type) select user_id, ${FACULTY}, 'FACULTY_SECRETARY' from users where email_address = '${reviewer}'`,
    );
  });

  test.afterAll(() => {
    expireOpenRequests(`u.email_address = '${owner}'`);
    sql(
      `delete from user_roles where organization_id = ${FACULTY} and user_id = ${user(reviewer)}`,
    );
  });

  test('9_2_6 ac1 to ac5 thumbnails, the viewer and the review queue pane', async ({ browser }) => {
    const title = `Грамота з переглядом ${uniqueToken()}`;
    const page = await signedInAs(browser, owner, FRESH_PASSWORD);
    const worker = workerRequests(page);

    await page.goto('/awards');
    await settled(page);
    expect(worker()).toBe(0);

    await openNewAward(page);
    await page.getByTestId('award-title-uk').fill(title);
    await page.getByTestId('documents-input').setInputFiles([
      { name: 'диплом.pdf', mimeType: 'application/pdf', buffer: twoPagePdf() },
      { name: 'зламаний.pdf', mimeType: 'application/pdf', buffer: brokenPdf() },
    ]);
    const thumbnail = row(page, 'диплом.pdf').getByTestId('document-thumbnail');
    await expect(thumbnail).toHaveClass(/thumbnail--ready/, { timeout: 15_000 });
    await expect(thumbnail).toHaveAttribute('aria-label', 'Переглянути диплом.pdf');
    expect(worker()).toBeGreaterThan(0);
    await expect(row(page, 'зламаний.pdf').getByTestId('document-thumbnail-tile')).toHaveText(
      /Не вдалося показати/,
    );
    await expect(row(page, 'зламаний.pdf').getByTestId('document-download')).toBeVisible();

    await thumbnail.focus();
    await page.keyboard.press('Enter');
    const status = page.getByTestId('document-viewer-status');
    await expect(status).toHaveText('Сторінка 1 з 2');
    await expect(page.getByTestId('document-viewer-previous')).toBeDisabled();
    await page.getByTestId('document-viewer-next').click();
    await expect(status).toHaveText('Сторінка 2 з 2');
    await expect(page.getByTestId('document-viewer-next')).toBeDisabled();
    const canvas = page.getByTestId('document-viewer-page');
    const before = (await canvas.boundingBox())?.width ?? 0;
    await page.getByTestId('document-viewer-zoom-in').click();
    await expect.poll(async () => (await canvas.boundingBox())?.width ?? 0).toBeGreaterThan(before);
    expect(await dialogViolations(page)).toEqual([]);
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(thumbnail).toBeFocused();

    await page.getByTestId('award-category').click();
    await page.getByTestId(`category-option-${UNIVERSITY}`).click();
    await page.getByTestId('award-organization').fill('Міністерство освіти і науки України');
    await page.getByTestId('award-date').fill(pastDay());
    await page.getByTestId('award-submit').click();
    await expect(page.getByTestId('award-submitted-name')).toContainText(title);
    const id = sql(`select award_id from awards where title_uk = '${title}'`);
    await page.context().close();

    const review = await signedInAs(browser, reviewer, FRESH_PASSWORD);
    await review.goto(`/reviews?request=${id}`);
    await expect(review.getByTestId('queue-open')).toHaveText(title);
    const queued = review
      .getByTestId('queue-selected')
      .getByRole('button', { name: 'Переглянути диплом.pdf' });
    await expect(queued).toHaveClass(/thumbnail--ready/, { timeout: 15_000 });
    await queued.click();
    await expect(review.getByTestId('document-viewer-status')).toHaveText('Сторінка 1 з 2');
    const saved = review.waitForEvent('download');
    await review.getByTestId('document-viewer-download').click();
    expect((await saved).suggestedFilename()).toBe('диплом.pdf');
    await review.context().close();
  });

  test('9_2_6 ac5 the viewer speaks English and fits a phone', async ({ browser }) => {
    const page = await signedInAs(browser, owner, FRESH_PASSWORD);
    await page.setViewportSize({ width: PHONE_WIDTH, height: 740 });
    await openNewAward(page);
    await page.getByTestId('award-title-uk').fill(`Phone preview ${uniqueToken()}`);
    await page
      .getByTestId('documents-input')
      .setInputFiles({ name: 'diploma.pdf', mimeType: 'application/pdf', buffer: twoPagePdf() });
    const thumbnail = row(page, 'diploma.pdf').getByTestId('document-thumbnail');
    await expect(thumbnail).toHaveClass(/thumbnail--ready/, { timeout: 15_000 });
    await toggleLanguage(page);

    await thumbnail.click();
    await expect(page.getByTestId('document-viewer-status')).toHaveText('Page 1 of 2');
    await expect(page.getByTestId('document-viewer-zoom-in')).toHaveAttribute(
      'aria-label',
      'Zoom in',
    );
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
    expect(await dialogViolations(page)).toEqual([]);
    await page.getByTestId('document-viewer-close').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.context().close();
  });
});
