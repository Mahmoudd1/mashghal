import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { TranslatePipe } from '@ngx-translate/core';

import { LocalizedNamePipe } from '../../../core/i18n/localized-name.pipe';
import { RemainingGrouping } from '../../../core/models/api.models';
import { FabricService } from '../fabric.service';

/**
 * What is left of each fabric, at whichever level of detail is asked for:
 * overall, by the date it came in, or by who supplied it.
 *
 * One endpoint serves all three — the grouping only changes which columns are
 * meaningful, so the table swaps columns rather than the page swapping tabs.
 */
@Component({
  selector: 'app-remaining-tab',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    TranslatePipe,
    LocalizedNamePipe,
  ],
  templateUrl: './remaining-tab.html',
  styleUrl: './tab.scss',
})
export class RemainingTab {
  protected readonly fabrics = inject(FabricService);

  protected readonly groupings: RemainingGrouping[] = ['TOTAL', 'DATE', 'SUPPLIER'];

  /** The grouping column only appears when the grouping puts something in it. */
  protected readonly columns = computed(() => {
    const grouping = this.fabrics.remainingGrouping();
    const middle = grouping === 'DATE' ? ['date'] : grouping === 'SUPPLIER' ? ['supplier'] : [];
    return ['fabric', ...middle, 'batches', 'rolls', 'quantity'];
  });

  protected labelFor(grouping: RemainingGrouping): string {
    return grouping === 'DATE'
      ? 'fabric.groupDate'
      : grouping === 'SUPPLIER'
        ? 'fabric.groupSupplier'
        : 'fabric.groupTotal';
  }
}
