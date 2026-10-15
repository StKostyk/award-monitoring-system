import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { NotificationFeed } from '../notification-feed';
import { NotificationText } from '../notification-text';
import { UnreadCount } from '../unread-count';

/** How many notifications the toolbar menu lists. */
export const MENU_SIZE = 10;

/** The toolbar bell: the unread badge and a menu of the latest notifications. */
@Component({
  selector: 'app-notification-bell',
  imports: [
    MatIcon,
    MatIconButton,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationBellComponent {
  protected readonly unread = inject(UnreadCount);
  protected readonly texts = inject(NotificationText);
  protected readonly feed = new NotificationFeed();

  /** Loads the latest notifications when the menu opens. */
  load(): void {
    this.unread.refresh();
    this.feed.load(0, MENU_SIZE);
  }
}
