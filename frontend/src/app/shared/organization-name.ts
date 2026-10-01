/** The organisation name in the active language, falling back to the English one. */
export function organizationName(
  organization: { name: string; nameUk: string | null },
  language: string,
): string {
  return language === 'uk' && organization.nameUk ? organization.nameUk : organization.name;
}
