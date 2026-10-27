/** Whether the main pointer is a finger, read once when a component is created. */
export function coarsePointer(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(pointer: coarse)').matches;
}
