/** Whether the main pointer is a finger, read once when a component is created. */
export function coarsePointer(): boolean {
  return matches('(pointer: coarse)');
}

/** Media query that matches when the system asks for reduced motion. */
export const REDUCED_MOTION = '(prefers-reduced-motion: reduce)';

/** Whether the system asks for reduced motion, read once at start-up. */
export function reducedMotion(): boolean {
  return matches(REDUCED_MOTION);
}

function matches(query: string): boolean {
  return typeof matchMedia === 'function' && matchMedia(query).matches;
}
