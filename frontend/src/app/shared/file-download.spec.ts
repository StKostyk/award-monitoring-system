import { vi } from 'vitest';

import { attachmentName, saveFile } from './file-download';

describe('file download', () => {
  afterEach(() => vi.restoreAllMocks());

  it('reads the attachment name or falls back', () => {
    expect(attachmentName('attachment; filename="award-5-audit-2026-09-30.csv"', 'x.csv')).toBe(
      'award-5-audit-2026-09-30.csv',
    );
    expect(attachmentName('attachment; filename=data.json', 'x.csv')).toBe('data.json');
    expect(attachmentName(null, 'x.csv')).toBe('x.csv');
  });

  it('hands the file to the browser and releases the url', () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:file');
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockReturnValue(undefined);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockReturnValue(undefined);

    saveFile(new Blob(['x']), 'x.csv');

    const link = click.mock.contexts[0] as HTMLAnchorElement;
    expect(link.download).toBe('x.csv');
    expect(link.href).toBe('blob:file');
    expect(revoke).toHaveBeenCalledWith('blob:file');
  });
});
