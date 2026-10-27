/** Whether the main pointer is a finger, read once when a component is created. */
export function coarsePointer(): boolean {
  return matches('(pointer: coarse)');
}

/** Whether the system asks for reduced motion, read once at start-up. */
export function reducedMotion(): boolean {
  return matches('(prefers-reduced-motion: reduce)');
}

function matches(query: string): boolean {
  return typeof matchMedia === 'function' && matchMedia(query).matches;
}
