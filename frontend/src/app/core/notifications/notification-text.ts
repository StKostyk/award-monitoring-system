import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';

import { kyivDate } from '../../shared/date-format';
import { LanguageService } from '../i18n/language.service';
import { AppNotification, NotificationType } from './notification.api';

const MINUTE = 60;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const RELATIVE_DAYS = 7;

const AWARD_TYPES: readonly NotificationType[] = [
  'AWARD_APPROVED',
  'AWARD_REJECTED',
  'AWARD_RETURNED',
  'AWARD_ESCALATED',
  'AWARD_CORRECTED',
  'REVIEW_TAKEN_OVER',
];

/** Where choosing a notification leads, or null when it has nowhere to go. */
export function notificationTarget(notification: AppNotification): unknown[] | null {
  if (AWARD_TYPES.includes(notification.type)) {
    return notification.referenceId === null ? null : ['/awards', notification.referenceId];
  }
  switch (notification.type) {
    case 'REVIEW_OVERDUE':
      return ['/reviews'];
    case 'ROLE_ASSIGNED':
    case 'ROLE_REVOKED':
      return ['/profile'];
    case 'DELEGATION_CREATED':
    case 'DELEGATION_REVOKED':
      return ['/delegations'];
    default:
      return null;
  }
}

/** How long ago an instant was, in words of the language, or the day itself after a week. */
export function relativeTime(iso: string, language: string, now = Date.now()): string {
  const sent = Date.parse(iso);
  if (Number.isNaN(sent)) {
    return '';
  }
  const seconds = Math.max(0, Math.round((now - sent) / 1000));
  if (seconds >= RELATIVE_DAYS * DAY) {
    return kyivDate(iso, language);
  }
  const format = new Intl.RelativeTimeFormat(language === 'en' ? 'en' : 'uk', { numeric: 'auto' });
  if (seconds < MINUTE) {
    return format.format(0, 'second');
  }
  if (seconds < HOUR) {
    return format.format(-Math.floor(seconds / MINUTE), 'minute');
  }
  if (seconds < DAY) {
    return format.format(-Math.floor(seconds / HOUR), 'hour');
  }
  return format.format(-Math.floor(seconds / DAY), 'day');
}

/**
 * Renders a notification from its type and payload in the interface language; calls made in a template or computed
 * follow a language switch once the new translation is loaded.
 */
@Injectable({ providedIn: 'root' })
export class NotificationText {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly loaded = toSignal(this.transloco.selectTranslation());

  /** The sentence of a notification. */
  text(notification: AppNotification): string {
    this.loaded();
    const language = this.language.current();
    const payload = notification.payload ?? {};
    const key = `notifications.types.${notification.type}`;
    const rendered = this.transloco.translate(key, this.params(payload, language));
    return rendered === key ? this.transloco.translate('notifications.types.unknown') : rendered;
  }

  /** How long ago it was sent. */
  time(notification: AppNotification): string {
    return relativeTime(notification.sentAt, this.language.current());
  }

  private params(payload: Record<string, unknown>, language: string): Record<string, string> {
    return {
      title: this.title(payload, language),
      reviewer: text(payload['reviewer']),
      level: this.named('awards.levels', payload['level']),
      fields: list(payload['fields'])
        .map((field) => this.named('awards.fields', field))
        .join(', '),
      count: text(payload['count']),
      oldest: this.dated('notifications.oldest', payload['oldestDeadline'], language),
      role: this.named('roles', payload['role']),
      organization: localized(payload['organization'], payload['organizationUk'], language),
      until: this.dated('notifications.until', payload['validTo'], language),
      lastDay: this.dated('notifications.lastDay', payload['validTo'], language),
      otherParty: text(payload['otherParty']),
    };
  }

  private title(payload: Record<string, unknown>, language: string): string {
    return localized(payload['title'], payload['titleUk'], language);
  }

  private named(group: string, value: unknown): string {
    const name = text(value);
    const key = `${group}.${name}`;
    const translated = name ? this.transloco.translate(key) : '';
    return translated === key ? name : translated;
  }

  private dated(key: string, value: unknown, language: string): string {
    const date = text(value);
    return date ? this.transloco.translate(key, { date: kyivDate(date, language) }) : '';
  }
}

function text(value: unknown): string {
  return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
}

function list(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function localized(english: unknown, ukrainian: unknown, language: string): string {
  const en = text(english);
  const uk = text(ukrainian);
  return language === 'en' ? en || uk : uk || en;
}
