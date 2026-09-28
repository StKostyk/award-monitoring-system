import { Injectable } from '@angular/core';

const PREFIX = 'awards.form-copy.';

/**
 * Unsaved form values kept in the browser per signed-in user, so an interrupted form can be offered back.
 * Storage that is missing or full is treated as empty; the copies are removed on sign-out.
 */
@Injectable({ providedIn: 'root' })
export class FormCopiesService {
  save(userId: string, form: string, value: unknown): void {
    try {
      localStorage.setItem(key(userId, form), JSON.stringify(value));
    } catch {
      // storage unavailable: the form simply cannot be restored
    }
  }

  load<T>(userId: string, form: string): T | null {
    try {
      const stored = localStorage.getItem(key(userId, form));
      return stored === null ? null : (JSON.parse(stored) as T);
    } catch {
      return null;
    }
  }

  remove(userId: string, form: string): void {
    try {
      localStorage.removeItem(key(userId, form));
    } catch {
      // nothing to remove
    }
  }

  /** Removes the copies of every user. */
  clearAll(): void {
    try {
      Object.keys(localStorage)
        .filter((stored) => stored.startsWith(PREFIX))
        .forEach((stored) => localStorage.removeItem(stored));
    } catch {
      // nothing to remove
    }
  }
}

function key(userId: string, form: string): string {
  return `${PREFIX}${userId}.${form}`;
}
