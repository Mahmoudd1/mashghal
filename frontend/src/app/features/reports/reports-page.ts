import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../../core/auth/auth.service';
import { LanguageService } from '../../core/i18n/language.service';
import { LocalizedNamePipe } from '../../core/i18n/localized-name.pipe';
import { FabricStock } from '../../core/models/api.models';
import { ReferenceService } from '../../core/models/reference.service';
import { ProductionService } from '../models/production.service';
import { SizeService } from '../../core/models/size.service';
import { ReportService } from './report.service';
import { StageTotalsTable } from './stage-totals-table';

/** Overview, per-branch rollup, flagged pieces, and fabric stock. */
@Component({
  selector: 'app-reports-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTabsModule,
    MatTooltipModule,
    TranslatePipe,
    LocalizedNamePipe,
    StageTotalsTable,
  ],
  templateUrl: './reports-page.html',
  styleUrl: './reports-page.scss',
})
export class ReportsPage {
  protected readonly reports = inject(ReportService);
  protected readonly reference = inject(ReferenceService);
  protected readonly production = inject(ProductionService);
  protected readonly sizes = inject(SizeService);
  protected readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  protected readonly flaggedColumns = ['model', 'branch', 'stage', 'flagged', 'of'];
  /** The cost column is money, so it only exists for the owner. */
  protected stockColumns(): string[] {
    const base = ['type', 'pool', 'batches', 'rolls', 'quantity'];
    return this.auth.isOwner() ? [...base, 'cost'] : base;
  }
  protected readonly byDateColumns = ['date', 'type', 'pool', 'rolls', 'quantity'];
  protected readonly openRollColumns = ['type', 'color', 'open', 'remaining'];
  protected readonly categoryModelColumns = ['model', 'category', 'perLayer'];

  /**
   * What every batch of fabric on the stock report cost, added up.
   *
   * <p>Money is the one column that survives the sum: the rows are per fabric
   * type, and a type is priced per kilo or per metre, so their quantities are
   * not the same thing and are left out of the footer. Null when no row carries
   * a price — either nothing has been priced yet, or the reader is not the owner
   * and the server blanked it.
   */
  protected readonly fabricTotalCost = computed<number | null>(() => {
    const priced = this.reports.fabricStock.value().filter((row) => row.totalCost !== null);
    return priced.length === 0
      ? null
      : priced.reduce((sum, row) => sum + row.totalCost!, 0);
  });

  protected readonly fabricTotalBatches = computed(() =>
    this.reports.fabricStock.value().reduce((sum, row) => sum + row.batchCount, 0),
  );

  protected typeName(row: FabricStock): string {
    return this.language.language() === 'en' && row.fabricTypeNameEn
      ? row.fabricTypeNameEn
      : row.fabricTypeNameAr;
  }
}
