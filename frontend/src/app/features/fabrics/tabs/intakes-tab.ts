import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../../../core/auth/auth.service';
import { LocalizedNamePipe } from '../../../core/i18n/localized-name.pipe';
import { FabricIntake, FabricIntakeColorRow, FabricPool } from '../../../core/models/api.models';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { ColorBreakdownDialog, ColorBreakdownDialogData } from '../dialogs/color-breakdown-dialog';
import { DerbyDialog, DerbyDialogData } from '../dialogs/derby-dialog';
import { IntakeDialog, IntakeDialogData } from '../dialogs/intake-dialog';
import { FabricService } from '../fabric.service';

/**
 * Dated purchases, each expandable to its colour breakdown.
 *
 * The breakdown is soft, so a batch shows an advisory chip when the colours fall
 * short of the total or run past it — neither prevents anything.
 */
@Component({
  selector: 'app-intakes-tab',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatCheckboxModule,
    MatChipsModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTooltipModule,
    TranslatePipe,
    LocalizedNamePipe,
  ],
  templateUrl: './intakes-tab.html',
  styleUrl: './tab.scss',
})
export class IntakesTab {
  protected readonly fabrics = inject(FabricService);
  protected readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly pools: FabricPool[] = ['REGULAR', 'DERBY'];

  protected onPage(event: PageEvent): void {
    this.fabrics.updateFilters({ pageIndex: event.pageIndex, pageSize: event.pageSize });
  }

  protected addIntake(): void {
    this.dialog.open<IntakeDialog, IntakeDialogData, boolean>(IntakeDialog, { data: {} });
  }

  protected editIntake(intake: FabricIntake): void {
    this.dialog.open<IntakeDialog, IntakeDialogData, boolean>(IntakeDialog, { data: { intake } });
  }

  protected deleteIntake(intake: FabricIntake): void {
    this.confirm({
      titleKey: 'fabric.deleteIntake',
      messageKey: 'fabric.deleteIntakeConfirm',
      subject: `${intake.fabricTypeNameAr} · ${intake.intakeDate}`,
    }).then((confirmed) => {
      if (confirmed) {
        this.fabrics.deleteIntake(intake.id).subscribe();
      }
    });
  }

  /** The derby that came in with this purchase: its date, supplier and price. */
  protected addDerby(intake: FabricIntake): void {
    this.dialog.open<DerbyDialog, DerbyDialogData, boolean>(DerbyDialog, {
      data: { purchase: intake },
      width: '620px',
    });
  }

  /** A derby bought separately, tied to the fabric type rather than a purchase. */
  protected recordDerby(): void {
    const typeId = this.fabrics.filters().fabricTypeId;
    const type = this.fabrics.types.value().find((candidate) => candidate.id === typeId);
    if (!type) {
      // Which fabric the derby belongs to is not a guess: the filter has to say.
      this.dialog.open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
        data: { titleKey: 'fabric.recordDerbyAlone', messageKey: 'fabric.pickTypeFirst' },
      });
      return;
    }
    this.dialog.open<DerbyDialog, DerbyDialogData, boolean>(DerbyDialog, {
      data: { type },
      width: '620px',
    });
  }

  /** The batch's whole colour breakdown, edited in one place. */
  protected assignColor(intake: FabricIntake): void {
    this.dialog.open<ColorBreakdownDialog, ColorBreakdownDialogData, boolean>(
      ColorBreakdownDialog,
      { data: { intake }, width: '720px' },
    );
  }

  protected removeColor(intake: FabricIntake, row: FabricIntakeColorRow): void {
    this.fabrics.removeColorBreakdown(intake.id, row.colorId).subscribe();
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
