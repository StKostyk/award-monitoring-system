/** The attachment name of a Content-Disposition header, or the fallback. */
export function attachmentName(disposition: string | null, fallback: string): string {
  const match = /filename="?([^";]+)"?/.exec(disposition ?? '');
  return match ? match[1] : fallback;
}

/** Time the browser gets to start the download before the object URL is released. */
const RELEASE_DELAY_MS = 30_000;

/** Hands a downloaded file to the browser under the given name. */
export function saveFile(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.hidden = true;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), RELEASE_DELAY_MS);
}
