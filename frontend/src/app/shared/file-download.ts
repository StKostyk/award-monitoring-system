/** The attachment name of a Content-Disposition header, or the fallback. */
export function attachmentName(disposition: string | null, fallback: string): string {
  const match = /filename="?([^";]+)"?/.exec(disposition ?? '');
  return match ? match[1] : fallback;
}

/** Hands a downloaded file to the browser under the given name. */
export function saveFile(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}
