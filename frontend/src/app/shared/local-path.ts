/**
 * A path inside this application, or the start page for anything else: an absolute URL, a protocol-relative `//host`
 * or a `/\host` that browsers read as one, so a redirect taken from the address bar never leaves the application.
 */
export function localPath(url: string | null | undefined): string {
  return url && /^\/(?![/\\])/.test(url) ? url : '/';
}
