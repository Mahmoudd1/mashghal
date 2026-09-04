import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../../core/auth/auth.service';
import { LanguageService } from '../../core/i18n/language.service';
import { LanguageToggle } from '../language-toggle/language-toggle';

interface NavItem {
  path: string;
  labelKey: string;
  icon: string;
  adminOnly?: boolean;
}

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatSidenavModule,
    MatToolbarModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    TranslatePipe,
    LanguageToggle,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly language = inject(LanguageService);

  protected readonly sidenavOpen = signal(true);

  protected readonly navItems: NavItem[] = [
    { path: '/dashboard', labelKey: 'nav.dashboard', icon: 'dashboard' },
    { path: '/fabrics', labelKey: 'nav.fabrics', icon: 'inventory_2' },
    { path: '/models', labelKey: 'nav.models', icon: 'content_cut' },
    { path: '/pipeline', labelKey: 'nav.pipeline', icon: 'conveyor_belt' },
    { path: '/suppliers', labelKey: 'nav.suppliers', icon: 'local_shipping' },
    { path: '/reports', labelKey: 'nav.reports', icon: 'insights' },
    { path: '/users', labelKey: 'nav.users', icon: 'group', adminOnly: true },
  ];

  /** Admin-only entries drop out of the menu for data-entry users. */
  protected readonly visibleNavItems = computed(() =>
    this.navItems.filter((item) => !item.adminOnly || this.auth.isAdmin()),
  );

  protected toggleSidenav(): void {
    this.sidenavOpen.update((open) => !open);
  }

  protected logout(): void {
    this.auth.logout();
  }
}
