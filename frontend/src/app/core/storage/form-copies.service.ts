import { Injectable } from '@angular/core';

const PREFIX = 'awards.form-copy.';

/**
 * Unsaved form values kept in the browser per signed-in user, so an interrupted form can be offered back.
 * Storage that is missing or full is treated as empty. The copies are removed on sign-out, and those of other
 * users when somebody signs in; a session that merely expired keeps them for the same user.
 */
@Injectable({ providedIn: 'root' })
export class FormCopiesService {
  private signingOut = false;

  /**
   * Keeps the values of a form.
   *
   * @param userId the user the form belongs to
   * @param form the name of the form
   * @param value the values to keep
   * @returns false when the browser refused to store them (storage full or disabled)
   */
  save(userId: string, form: string, value: unknown): boolean {
    if (this.signingOut) {
      return true;
    }
    try {
      localStorage.setItem(key(userId, form), JSON.stringify(value));
      return true;
    } catch {
      return false;
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

  /** Removes the copies of every user and keeps no new ones on this page: the user is signing out. */
  clearAll(): void {
    this.signingOut = true;
    this.clearWhere(() => true);
  }

  /** Whether unsaved values are still kept; false once sign-out has begun. */
  keepsCopies(): boolean {
    return !this.signingOut;
  }

  /** Removes the copies of everybody but the given user, who has just signed in on this browser. */
  clearOthers(userId: string): void {
    const own = `${PREFIX}${userId}.`;
    this.clearWhere((stored) => !stored.startsWith(own));
  }

  private clearWhere(matches: (stored: string) => boolean): void {
    try {
      Object.keys(localStorage)
        .filter((stored) => stored.startsWith(PREFIX) && matches(stored))
        .forEach((stored) => localStorage.removeItem(stored));
    } catch {
      // nothing to remove
    }
  }
}

function key(userId: string, form: string): string {
  return `${PREFIX}${userId}.${form}`;
}
