import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../../../core/auth/auth.service';
import { LocalizedNamePipe } from '../../../core/i18n/localized-name.pipe';
import {
  Cut,
  CutModelAllocation,
  CutModelSize,
  CutRoll,
  CutStatus,
  CutType,
} from '../../../core/models/api.models';
import { ReferenceService } from '../../../core/models/reference.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { CutDialog, CutDialogData } from '../dialogs/cut-dialog';
import {
  ModelAllocationDialog,
  ModelAllocationDialogData,
} from '../dialogs/model-allocation-dialog';
import { CutRollDialog, CutRollDialogData } from '../dialogs/cut-roll-dialog';
import { CutSizeDialog, CutSizeDialogData } from '../dialogs/cut-size-dialog';
import { ProductionService } from '../production.service';

/**
 * Cut list plus a detail panel showing both allocation directions: which models
 * a cut fed, and which rolls it consumed.
 */
@Component({
  selector: 'app-cuts-tab',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    MatButtonModule,
    MatChipsModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
    TranslatePipe,
    LocalizedNamePipe,
  ],
  templateUrl: './cuts-tab.html',
  styleUrl: '../../fabrics/tabs/tab.scss',
})
export class CutsTab {
  protected readonly production = inject(ProductionService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly derivedColumns = ['model', 'derived', 'allocated', 'balance'];
  protected readonly columns = [
    'cutNumber',
    'model',
    'cutType',
    'parent',
    'branch',
    'date',
    'allocated',
    'status',
    'actions',
  ];
  protected readonly cutTypes: CutType[] = ['MAIN', 'SECONDARY', 'DERBY'];
  protected readonly statuses: CutStatus[] = ['OPEN', 'CLOSED'];

  protected onPage(event: PageEvent): void {
    this.production.updateCutFilters({ pageIndex: event.pageIndex, pageSize: event.pageSize });
  }

  protected select(cut: Cut): void {
    this.production.selectedCutId.update((current) => (current === cut.id ? null : cut.id));
  }

  protected addCut(): void {
    this.dialog.open<CutDialog, CutDialogData, boolean>(CutDialog, { data: {} });
  }

  protected editCut(cut: Cut): void {
    this.dialog.open<CutDialog, CutDialogData, boolean>(CutDialog, { data: { cut } });
  }

  protected toggleStatus(cut: Cut): void {
    const call =
      cut.status === 'OPEN' ? this.production.closeCut(cut.id) : this.production.reopenCut(cut.id);
    call.subscribe();
  }

  protected deleteCut(cut: Cut): void {
    this.confirm({
      titleKey: 'cut.delete',
      messageKey: 'cut.deleteConfirm',
      subject: cut.cutNumber,
    }).then((confirmed) => {
      if (confirmed) {
        this.production.deleteCut(cut.id).subscribe();
      }
    });
  }

  protected allocateToModel(cut: Cut): void {
    this.dialog.open<ModelAllocationDialog, ModelAllocationDialogData, boolean>(
      ModelAllocationDialog,
      {
        data: { cut },
      },
    );
  }

  protected editModelAllocation(cut: Cut, allocation: CutModelAllocation): void {
    this.dialog.open<ModelAllocationDialog, ModelAllocationDialogData, boolean>(
      ModelAllocationDialog,
      {
        data: { cut, allocation },
      },
    );
  }

  protected removeModelAllocation(allocation: CutModelAllocation): void {
    this.confirm({
      titleKey: 'cut.removeAllocation',
      messageKey: 'cut.removeAllocationConfirm',
      subject: `${allocation.modelNumber} · ${allocation.quantityAllocated}`,
    }).then((confirmed) => {
      if (confirmed) {
        this.production.removeModelAllocation(allocation.id).subscribe();
      }
    });
  }

  protected addRoll(cut: Cut): void {
    this.dialog.open<CutRollDialog, CutRollDialogData, boolean>(CutRollDialog, { data: { cut } });
  }

  protected editRoll(cut: Cut, cutRoll: CutRoll): void {
    this.dialog.open<CutRollDialog, CutRollDialogData, boolean>(CutRollDialog, {
      data: { cut, cutRoll },
    });
  }

  protected removeRoll(cutRoll: CutRoll): void {
    this.confirm({
      titleKey: 'cut.removeRoll',
      messageKey: 'cut.removeRollConfirm',
      subject: `${cutRoll.rollLabel ?? '#' + cutRoll.fabricRollId} · ${cutRoll.weightConsumed}`,
    }).then((confirmed) => {
      if (confirmed) {
        this.production.removeRoll(cutRoll.id).subscribe();
      }
    });
  }

  protected addSize(cut: Cut): void {
    this.dialog.open<CutSizeDialog, CutSizeDialogData, boolean>(CutSizeDialog, { data: { cut } });
  }

  protected editSize(cut: Cut, row: CutModelSize): void {
    this.dialog.open<CutSizeDialog, CutSizeDialogData, boolean>(CutSizeDialog, {
      data: { cut, row },
    });
  }

  protected removeSize(cut: Cut, row: CutModelSize): void {
    this.confirm({
      titleKey: 'cut.marker',
      messageKey: 'cut.removeMarkerConfirm',
      subject: `${row.modelNumber} · ${row.sizeCode}`,
    }).then((confirmed) => {
      if (confirmed) {
        this.production.removeModelSize(cut.id, row.modelId, row.sizeId).subscribe();
      }
    });
  }

  private confirm(data: ConfirmDialogData): Promise<boolean> {
    const ref = this.dialog.open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
      data,
    });
    return new Promise((resolve) =>
      ref.afterClosed().subscribe((result) => resolve(result === true)),
    );
  }
}
