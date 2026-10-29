/** Whether the main pointer is a finger, read once when a component is created. */
export function coarsePointer(): boolean {
  return matches('(pointer: coarse)');
}

/** Media query of a phone-sized screen, the `$narrow` width of the touch styles. */
export const NARROW_SCREEN = '(max-width: 600px)';

/** Whether the screen is phone-sized, read once when called. */
export function narrowScreen(): boolean {
  return matches(NARROW_SCREEN);
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
