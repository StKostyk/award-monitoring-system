import { vi } from 'vitest';

/**
 * Makes the system ask for reduced motion, so count-ups show their final number at once and lists skip the stagger.
 * Undo with `vi.unstubAllGlobals()`.
 */
export function reduceMotion(): void {
  vi.stubGlobal(
    'matchMedia',
    vi.fn((query: string) => ({
      matches: query.includes('reduced-motion'),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
}
