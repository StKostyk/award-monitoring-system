import { organizationName } from '../../../shared/organization-name';
import {
  AwardVersion,
  CategoryRef,
  OrganizationName,
  SnapshotField,
  categoryName,
} from '../awards.service';

/** Translation keys of the snapshot field labels. */
export const FIELD_LABELS: Record<SnapshotField, string> = {
  title: 'awards.fields.title',
  titleUk: 'awards.fields.titleUk',
  description: 'awards.fields.description',
  descriptionUk: 'awards.fields.descriptionUk',
  awardingOrganization: 'awards.fields.awardingOrganization',
  awardDate: 'awards.fields.awardDate',
  categoryId: 'awards.fields.category',
  status: 'awards.fields.status',
  impactScore: 'awards.fields.impactScore',
  verificationBadge: 'awards.fields.verificationBadge',
  externalUrl: 'awards.fields.externalUrl',
  organizationId: 'awards.fields.organization',
};

/** A value as shown: a plain text, or a translation key. */
export interface ShownValue {
  text?: string;
  key?: string;
}

/** What the ids of a snapshot are shown as. */
export interface ValueNames {
  categories: Map<number, CategoryRef>;
  organizations: Map<number, OrganizationName>;
  language: string;
}

const EMPTY = '—';

/** A snapshot value with categories and organisations by name and empty values as a dash. */
export function shownValue(field: SnapshotField, value: unknown, names: ValueNames): ShownValue {
  if (value === null || value === undefined || value === '') {
    return { text: EMPTY };
  }
  switch (field) {
    case 'categoryId': {
      const category = names.categories.get(Number(value));
      return { text: category ? categoryName(category, names.language) : `#${String(value)}` };
    }
    case 'organizationId': {
      const organization = names.organizations.get(Number(value));
      return {
        text: organization ? organizationName(organization, names.language) : `#${String(value)}`,
      };
    }
    case 'status':
      return { key: `awards.status.${String(value)}` };
    case 'verificationBadge':
      return { key: value ? 'awards.history.yes' : 'awards.history.no' };
    default:
      return { text: String(value) };
  }
}

/** The organisations the versions refer to, in their snapshots or their changes. */
export function organizationIds(versions: AwardVersion[]): number[] {
  const ids = versions.flatMap((version) => [
    version.snapshot.organizationId,
    ...version.changes
      .filter((change) => change.field === 'organizationId')
      .flatMap((change) => [change.from, change.to]),
  ]);
  return [...new Set(ids.filter((id): id is number => typeof id === 'number'))];
}
