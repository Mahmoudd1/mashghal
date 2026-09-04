import { Pipe, PipeTransform, inject } from '@angular/core';

import { LanguageService } from './language.service';

/** Picks the English label when the UI is in English and one was entered. */
@Pipe({ name: 'localizedName', pure: false })
export class LocalizedNamePipe implements PipeTransform {
  private readonly language = inject(LanguageService);

  transform(value: { nameAr: string; nameEn?: string | null } | null | undefined): string {
    if (!value) {
      return '';
    }
    return this.language.language() === 'en' && value.nameEn ? value.nameEn : value.nameAr;
  }
}
