import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslatePipe } from '@ngx-translate/core';

import { AppLanguage, LanguageService } from '../../core/i18n/language.service';

@Component({
  selector: 'app-language-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatMenuModule, TranslatePipe],
  template: `
    <button
      mat-icon-button
      [matMenuTriggerFor]="menu"
      [attr.aria-label]="'language.label' | translate"
    >
      <mat-icon>translate</mat-icon>
    </button>
    <mat-menu #menu="matMenu">
      @for (option of options; track option.code) {
        <button
          mat-menu-item
          (click)="select(option.code)"
          [disabled]="language.language() === option.code"
        >
          <span>{{ option.labelKey | translate }}</span>
        </button>
      }
    </mat-menu>
  `,
})
export class LanguageToggle {
  protected readonly language = inject(LanguageService);

  protected readonly options: { code: AppLanguage; labelKey: string }[] = [
    { code: 'ar', labelKey: 'language.arabic' },
    { code: 'en', labelKey: 'language.english' },
  ];

  protected select(code: AppLanguage): void {
    this.language.use(code);
  }
}
