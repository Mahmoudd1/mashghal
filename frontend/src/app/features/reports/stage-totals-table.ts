import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { TranslatePipe } from '@ngx-translate/core';

import { StageTotal } from '../../core/models/api.models';

/** Stage-by-stage totals with each stage's share of the pipeline. Reused by every report tab. */
@Component({
  selector: 'app-stage-totals-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, MatTableModule, TranslatePipe],
  template: `
    <div class="table-scroll">
      <table mat-table [dataSource]="stages()">
        <ng-container matColumnDef="stage">
          <th mat-header-cell *matHeaderCellDef>{{ 'stage.label' | translate }}</th>
          <td mat-cell *matCellDef="let row">{{ 'stage.' + row.stageCode | translate }}</td>
        </ng-container>

        <ng-container matColumnDef="pieces">
          <th mat-header-cell *matHeaderCellDef>{{ 'common.pieces' | translate }}</th>
          <td mat-cell *matCellDef="let row" class="numeric">{{ row.pieceCount }}</td>
        </ng-container>

        <ng-container matColumnDef="share">
          <th mat-header-cell *matHeaderCellDef>{{ 'report.share' | translate }}</th>
          <td mat-cell *matCellDef="let row" class="numeric">
            {{ share(row.pieceCount) | number: '1.0-1' }}%
          </td>
        </ng-container>

        <ng-container matColumnDef="flagged">
          <th mat-header-cell *matHeaderCellDef>{{ 'pipeline.flagged' | translate }}</th>
          <td mat-cell *matCellDef="let row" class="numeric">{{ row.flaggedCount || '—' }}</td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `,
  styles: `
    table {
      width: 100%;
    }

    .numeric {
      direction: ltr;
      text-align: start;
      white-space: nowrap;
    }
  `,
})
export class StageTotalsTable {
  readonly stages = input.required<StageTotal[]>();

  protected readonly columns = ['stage', 'pieces', 'share', 'flagged'];

  private readonly total = computed(() =>
    this.stages().reduce((sum, row) => sum + row.pieceCount, 0),
  );

  protected share(pieceCount: number): number {
    const total = this.total();
    return total === 0 ? 0 : (pieceCount / total) * 100;
  }
}
