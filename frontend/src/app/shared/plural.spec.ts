import { pluralForm } from './plural';

describe('pluralForm', () => {
  it('follows the language', () => {
    expect(pluralForm(21, 'uk')).toBe('one');
    expect(pluralForm(22, 'uk')).toBe('few');
    expect(pluralForm(906, 'uk')).toBe('many');
    expect(pluralForm(21, 'en')).toBe('other');
    expect(pluralForm(1, 'en')).toBe('one');
  });
});
