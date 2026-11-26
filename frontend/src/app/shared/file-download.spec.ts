import { vi } from 'vitest';

import { attachmentName, saveFile } from './file-download';

describe('file download', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('reads the attachment name or falls back', () => {
    expect(attachmentName('attachment; filename="award-5-audit-2026-09-30.csv"', 'x.csv')).toBe(
      'award-5-audit-2026-09-30.csv',
    );
    expect(attachmentName('attachment; filename=data.json', 'x.csv')).toBe('data.json');
    expect(attachmentName(null, 'x.csv')).toBe('x.csv');
  });

  it('f4_clicks_an_attached_link_and_releases_the_url_only_later', () => {
    vi.useFakeTimers();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:file');
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockReturnValue(undefined);
    let attached = false;
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      attached = this.isConnected;
    });

    saveFile(new Blob(['x']), 'x.csv');

    const link = click.mock.contexts[0] as HTMLAnchorElement;
    expect(link.download).toBe('x.csv');
    expect(link.href).toBe('blob:file');
    expect(attached).toBe(true);
    expect(link.isConnected).toBe(false);
    expect(revoke).not.toHaveBeenCalled();
    vi.runAllTimers();
    expect(revoke).toHaveBeenCalledWith('blob:file');
  });
});
