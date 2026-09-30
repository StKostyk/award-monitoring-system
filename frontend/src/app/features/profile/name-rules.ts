import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const NAME_MAX_LENGTH = 100;
/** Letters, apostrophes, hyphens and spaces, starting with a letter: the registration rule of the API. */
const NAME = /^\p{L}[\p{L}'’\- ]*$/u;

/** The name rule of the API applied to the trimmed value, reporting the codes the API uses. */
export const nameValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  if (!value) {
    return { required: true };
  }
  if (value.length > NAME_MAX_LENGTH) {
    return { 'too-long': true };
  }
  return NAME.test(value) ? null : { invalid: true };
};
