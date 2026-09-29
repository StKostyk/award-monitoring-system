import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { FormCopiesService } from './form-copies.service';

describe('FormCopiesService', () => {
  let copies: FormCopiesService;

  beforeEach(() => {
    localStorage.clear();
    copies = TestBed.inject(FormCopiesService);
  });

  it('ac1_10_keeps_a_copy_per_user_and_form', () => {
    copies.save('21', 'award-new', { titleUk: 'Подяка' });

    expect(copies.load('21', 'award-new')).toEqual({ titleUk: 'Подяка' });
    expect(copies.load('22', 'award-new')).toBeNull();
    expect(copies.load('21', 'award-5')).toBeNull();
  });

  it('ac1_10_removes_one_copy', () => {
    copies.save('21', 'award-new', { titleUk: 'Подяка' });

    copies.remove('21', 'award-new');

    expect(copies.load('21', 'award-new')).toBeNull();
  });

  it('ac1_10_sign_out_removes_every_copy_and_nothing_else', () => {
    copies.save('21', 'award-new', {});
    copies.save('22', 'award-5', {});
    localStorage.setItem('lang', 'en');

    copies.clearAll();

    expect(localStorage.length).toBe(1);
    expect(localStorage.getItem('lang')).toBe('en');
  });

  it('ac1_10_f4_a_form_closing_during_sign_out_keeps_no_copy', () => {
    expect(copies.keepsCopies()).toBe(true);

    copies.clearAll();
    copies.save('21', 'award-new', { titleUk: 'Пізня' });

    expect(copies.load('21', 'award-new')).toBeNull();
    expect(copies.keepsCopies()).toBe(false);
  });

  it('f4_a_sign_in_keeps_only_the_copies_of_the_signed_in_user', () => {
    copies.save('21', 'award-new', {});
    copies.save('2', 'award-5', {});
    copies.save('210', 'award-6', {});
    localStorage.setItem('lang', 'en');

    copies.clearOthers('21');

    expect(copies.load('21', 'award-new')).toEqual({});
    expect(copies.load('2', 'award-5')).toBeNull();
    expect(copies.load('210', 'award-6')).toBeNull();
    expect(localStorage.getItem('lang')).toBe('en');
  });

  it('ac1_10_treats_unreadable_or_unavailable_storage_as_empty', () => {
    localStorage.setItem('awards.form-copy.21.award-new', '{broken');
    expect(copies.load('21', 'award-new')).toBeNull();

    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('full');
    });
    expect(() => copies.save('21', 'award-5', {})).not.toThrow();
    setItem.mockRestore();
  });
});
