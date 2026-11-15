import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { BrandService } from '../../../core/brand/brand.service';

/**
 * Brand panel beside the content of the account pages, matching the sign-in page of the authorization server: a
 * side panel on wide screens, a strip above the content on phones.
 */
@Component({
  selector: 'app-auth-layout',
  imports: [TranslocoPipe],
  templateUrl: './auth-layout.component.html',
  styleUrl: './auth-layout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthLayoutComponent {
  protected readonly brands = inject(BrandService);
  protected readonly brand = this.brands.brand;
}
