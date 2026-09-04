import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { FabricStock } from '../../../core/models/api.models';
import { FabricService } from '../fabric.service';

/**
 * Stock per fabric type and pool.
 *
 * The pool figures are authoritative. The colour rows beneath are indicative:
 * fabric can be cut from a batch before its colours are known, so they will not
 * always reconcile — which is why they are shown as a sub-table, not the total.
 */
@Component({
  selector: 'app-stock-tab',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    TranslatePipe,
  ],
  templateUrl: './stock-tab.html',
  styleUrl: './tab.scss',
})
export class StockTab {
  protected readonly fabrics = inject(FabricService);
  protected readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  protected readonly colorColumns = ['color', 'assigned', 'consumed', 'remaining'];

  protected typeName(row: FabricStock): string {
    return this.language.language() === 'en' && row.fabricTypeNameEn
      ? row.fabricTypeNameEn
      : row.fabricTypeNameAr;
  }
}
