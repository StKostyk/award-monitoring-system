import { localPath } from './local-path';

describe('localPath', () => {
  it.each(['/', '/awards', '/awards?status=DRAFT', '/awards/5/edit#files'])(
    'keeps the path %s',
    (url) => {
      expect(localPath(url)).toBe(url);
    },
  );

  it.each([
    null,
    undefined,
    '',
    'awards',
    'https://evil.example/awards',
    '//evil.example',
    '/\\evil.example',
    'javascript:alert(1)',
  ])('turns %s into the start page', (url) => {
    expect(localPath(url)).toBe('/');
  });
});
