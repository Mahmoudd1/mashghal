import { Dir } from '@angular/cdk/bidi';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { LanguageService } from './core/i18n/language.service';

/**
 * The `dir` binding drives Angular Material and the CDK through the CDK's
 * Directionality service, so switching language flips the whole layout without
 * a reload. LanguageService mirrors the same value onto <html> for CSS.
 */
@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Dir],
  template: `
    <div class="app-direction-root" [dir]="language.direction()">
      <router-outlet />
    </div>
  `,
  styles: `
    .app-direction-root {
      height: 100%;
    }
  `,
})
export class App {
  protected readonly language = inject(LanguageService);
}
