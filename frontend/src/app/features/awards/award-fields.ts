import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Longest title, as the API accepts it. */
export const TITLE_LIMIT = 500;
/** Longest description. */
export const DESCRIPTION_LIMIT = 4000;
/** Longest awarding organisation. */
export const ORGANIZATION_LIMIT = 255;
/** Longest link. */
export const URL_LIMIT = 2048;
/** An http or https link. */
export const WEB_LINK = /^https?:\/\/\S+$/i;

/** A title is required in Ukrainian or English. */
export function titleInOneLanguage(group: AbstractControl): ValidationErrors | null {
  const title = group.get('title')?.value as string | null;
  const titleUk = group.get('titleUk')?.value as string | null;
  return empty(title) && empty(titleUk) ? { titleRequired: true } : null;
}

/** The text without surrounding spaces; null when nothing is left. */
export function text(value: string | null | undefined): string | null {
  const trimmed = value?.trim() ?? '';
  return trimmed === '' ? null : trimmed;
}

/** Whether a form value holds no text. */
export function empty(value: unknown): boolean {
  return value === null || value === undefined || String(value).trim() === '';
}

/**
 * The translation key of the first error of an award field, the server's code first.
 *
 * @param errors the errors of the field's control
 * @returns the key, or null when the field is valid
 */
export function fieldErrorKey(errors: ValidationErrors | null): string | null {
  if (!errors) {
    return null;
  }
  if (errors['server']) {
    return `awards.errors.${errors['server']}`;
  }
  if (errors['required']) {
    return 'awards.errors.required';
  }
  if (errors['maxlength']) {
    return 'awards.errors.too-long';
  }
  if (errors['matDatepickerParse']) {
    return 'app.dateInvalid';
  }
  if (errors['matDatepickerMax']) {
    return 'awards.errors.future';
  }
  if (errors['matDatepickerMin']) {
    return 'awards.errors.too-old';
  }
  return errors['pattern'] ? 'awards.errors.invalid' : null;
}
