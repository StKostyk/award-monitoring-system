import { AbstractControl, ValidatorFn, Validators } from '@angular/forms';

export const PASSWORD_MIN = 10;
export const PASSWORD_MAX_BYTES = 72;

/** Rejects passwords longer than the 72 bytes BCrypt can hash. */
export function maxUtf8Bytes(max: number): ValidatorFn {
  return (control: AbstractControl<string>) =>
    new TextEncoder().encode(control.value ?? '').length > max ? { maxbytes: { max } } : null;
}

/** The client-side mirror of the server password policy (length only; the common-password list stays on the server). */
export const PASSWORD_VALIDATORS = [Validators.required, Validators.minLength(PASSWORD_MIN), maxUtf8Bytes(PASSWORD_MAX_BYTES)];
