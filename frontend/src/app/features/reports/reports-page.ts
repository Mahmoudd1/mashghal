import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
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

  protected typeName(row: FabricStock): string {
    return this.language.language() === 'en' && row.fabricTypeNameEn
      ? row.fabricTypeNameEn
      : row.fabricTypeNameAr;
  }
}
