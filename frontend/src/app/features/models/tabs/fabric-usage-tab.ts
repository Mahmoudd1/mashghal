import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { TranslatePipe } from '@ngx-translate/core';

import { ProductionService } from '../production.service';

/**
 * How much of each fabric goes into one piece of each model — the figure that
 * turns a fabric price into a per-garment cost.
 *
 * <p>A model can appear more than once when it is made from several fabrics, and
 * a cut making several models has its fabric split between them by pieces. The
 * hint says so, because that apportionment is an estimate rather than a
 * measurement.
 */
@Component({
  selector: 'app-fabric-usage-tab',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    TranslatePipe,
  ],
  template: `
    <div class="tab-toolbar">
      <p class="muted">
        {{ 'usage.hint' | translate }}<br />
        {{ 'usage.denominatorHint' | translate }}
      </p>
      <span class="spacer"></span>
      <button mat-stroked-button (click)="production.fabricUsage.reload()">
        <mat-icon>refresh</mat-icon>
        <span>{{ 'common.search' | translate }}</span>
      </button>
    </div>

    @if (production.fabricUsage.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    <div class="table-scroll">
      <table mat-table [dataSource]="production.fabricUsage.value()">
        <ng-container matColumnDef="model">
          <th mat-header-cell *matHeaderCellDef>{{ 'model.label' | translate }}</th>
          <td mat-cell *matCellDef="let r">{{ r.modelNumber }} — {{ r.modelNameAr }}</td>
        </ng-container>

        <ng-container matColumnDef="fabric">
          <th mat-header-cell *matHeaderCellDef>{{ 'fabric.type' | translate }}</th>
          <td mat-cell *matCellDef="let r">{{ r.fabricTypeNameAr }}</td>
        </ng-container>

        <ng-container matColumnDef="cutType">
          <th mat-header-cell *matHeaderCellDef>{{ 'usage.cutType' | translate }}</th>
          <td mat-cell *matCellDef="let r">
            <span [class.secondary-run]="r.cutType !== 'MAIN'">
              {{ 'cutType.' + r.cutType | translate }}
            </span>
          </td>
        </ng-container>

        <ng-container matColumnDef="cuts">
          <th mat-header-cell *matHeaderCellDef>{{ 'usage.cuts' | translate }}</th>
          <td mat-cell *matCellDef="let r" class="numeric">{{ r.cutCount }}</td>
        </ng-container>

        <ng-container matColumnDef="pieces">
          <th mat-header-cell *matHeaderCellDef>{{ 'common.pieces' | translate }}</th>
          <td mat-cell *matCellDef="let r" class="numeric">{{ r.totalPieces }}</td>
        </ng-container>

        <ng-container matColumnDef="total">
          <th mat-header-cell *matHeaderCellDef>{{ 'usage.totalWeight' | translate }}</th>
          <td mat-cell *matCellDef="let r" class="numeric">
            {{ r.totalWeight | number: '1.0-3' }} {{ 'unit.' + r.unit | translate }}
          </td>
        </ng-container>

        <ng-container matColumnDef="perPiece">
          <th mat-header-cell *matHeaderCellDef>{{ 'usage.perPiece' | translate }}</th>
          <td mat-cell *matCellDef="let r" class="numeric strong">
            {{ r.weightPerPiece | number: '1.0-4' }} {{ 'unit.' + r.unit | translate }}
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let r; columns: columns"></tr>
      </table>
    </div>

    @if (!production.fabricUsage.isLoading() && production.fabricUsage.value().length === 0) {
      <p class="empty-state">{{ 'common.noData' | translate }}</p>
    }
  `,
  styleUrl: '../../fabrics/tabs/tab.scss',
})
export class FabricUsageTab {
  protected readonly production = inject(ProductionService);
  protected readonly columns = [
    'model',
    'fabric',
    'cutType',
    'cuts',
    'pieces',
    'total',
    'perPiece',
  ];
}
