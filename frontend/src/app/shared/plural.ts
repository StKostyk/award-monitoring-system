const RULES = new Map<string, Intl.PluralRules>();

/**
 * The plural form of a count in a language, for keys with one, few, many and other forms.
 *
 * @param count the count
 * @param language the interface language
 * @returns the plural category
 */
export function pluralForm(count: number, language: string): Intl.LDMLPluralRule {
  let rules = RULES.get(language);
  if (!rules) {
    rules = new Intl.PluralRules(language);
    RULES.set(language, rules);
  }
  return rules.select(count);
}
