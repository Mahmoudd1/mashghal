import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { LocalizedNamePipe } from '../../../core/i18n/localized-name.pipe';
import { FabricColor, FabricType } from '../../../core/models/api.models';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { FabricColorDialog, FabricColorDialogData } from '../dialogs/fabric-color-dialog';
import { FabricTypeDialog, FabricTypeDialogData } from '../dialogs/fabric-type-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { FabricService } from '../fabric.service';

/** Fabric types with their colours, as an expandable list. */
@Component({
  selector: 'app-fabric-types-tab',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatChipsModule,
    MatExpansionModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    TranslatePipe,
    LocalizedNamePipe,
  ],
  templateUrl: './fabric-types-tab.html',
  styleUrl: './tab.scss',
})
export class FabricTypesTab {
  protected readonly fabrics = inject(FabricService);
  protected readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected addType(): void {
    this.dialog.open<FabricTypeDialog, FabricTypeDialogData, boolean>(FabricTypeDialog, {
      data: { unitLocked: false },
    });
  }

  protected editType(type: FabricType): void {
    this.dialog.open<FabricTypeDialog, FabricTypeDialogData, boolean>(FabricTypeDialog, {
      data: { type, unitLocked: this.hasStock(type) },
    });
  }

  protected deleteType(type: FabricType): void {
    this.confirm({
      titleKey: 'fabric.deleteType',
      messageKey: 'fabric.deleteTypeConfirm',
      subject: type.nameAr,
    }).then((confirmed) => {
      if (confirmed) {
        this.fabrics.deleteType(type.id).subscribe();
      }
    });
  }

  protected addColor(type: FabricType): void {
    this.dialog.open<FabricColorDialog, FabricColorDialogData, boolean>(FabricColorDialog, {
      data: { fabricTypeId: type.id },
    });
  }

  protected editColor(type: FabricType, color: FabricColor): void {
    this.dialog.open<FabricColorDialog, FabricColorDialogData, boolean>(FabricColorDialog, {
      data: { fabricTypeId: type.id, color },
    });
  }

  protected deleteColor(color: FabricColor): void {
    this.confirm({
      titleKey: 'fabric.deleteColor',
      messageKey: 'fabric.deleteColorConfirm',
      subject: color.nameAr,
    }).then((confirmed) => {
      if (confirmed) {
        this.fabrics.deleteColor(color.id).subscribe();
      }
    });
  }

  /**
   * The unit is frozen once stock exists for the type. The stock rollup already
   * carries those totals, so no extra request is needed.
   */
  private hasStock(type: FabricType): boolean {
    return this.fabrics.stock
      .value()
      .some((row) => row.fabricTypeId === type.id && row.totalRolls > 0);
  }

  protected createDerby(type: FabricType): void {
    this.fabrics.createDerby(type.id, { note: null }).subscribe();
  }

  protected deleteDerby(type: FabricType): void {
    this.confirm({
      titleKey: 'fabric.deleteDerby',
      messageKey: 'fabric.deleteDerbyConfirm',
      subject: type.nameAr,
    }).then((confirmed) => {
      if (!confirmed) {
        return;
      }
      // The derby id is not carried on the type row, so resolve it before deleting.
      this.fabrics
        .derbyFor(type.id)
        .subscribe((derby) => this.fabrics.deleteDerby(derby.id).subscribe());
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
