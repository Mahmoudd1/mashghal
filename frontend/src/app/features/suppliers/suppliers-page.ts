import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../../core/auth/auth.service';
import { LocalizedNamePipe } from '../../core/i18n/localized-name.pipe';
import { Supplier } from '../../core/models/api.models';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';
import { FabricService } from '../fabrics/fabric.service';
import { SupplierDialog, SupplierDialogData } from './supplier-dialog';
import { SupplierService } from './supplier.service';

/**
 * Suppliers, and what each fabric has cost.
 *
 * The price tab is the same data in two shapes: overall per fabric, or split by
 * supplier — which is what answers "who is cheaper for cotton".
 */
@Component({
  selector: 'app-suppliers-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTableModule,
    MatTabsModule,
    MatTooltipModule,
    TranslatePipe,
    LocalizedNamePipe,
  ],
  templateUrl: './suppliers-page.html',
  styleUrl: './suppliers-page.scss',
})
export class SuppliersPage {
  protected readonly suppliers = inject(SupplierService);
  protected readonly fabrics = inject(FabricService);
  protected readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly supplierColumns = ['name', 'phone', 'note', 'actions'];

  /** The supplier column only earns its place when the report is split by it. */
  protected priceColumns(): string[] {
    const base = ['fabric', 'batches', 'bought', 'spent', 'avg', 'range', 'latest'];
    return this.suppliers.bySupplier() ? ['fabric', 'supplier', ...base.slice(1)] : base;
  }

  protected addSupplier(): void {
    this.dialog.open<SupplierDialog, SupplierDialogData, boolean>(SupplierDialog, { data: {} });
  }

  protected editSupplier(supplier: Supplier): void {
    this.dialog.open<SupplierDialog, SupplierDialogData, boolean>(SupplierDialog, {
      data: { supplier },
    });
  }

  protected deleteSupplier(supplier: Supplier): void {
    const data: ConfirmDialogData = {
      titleKey: 'supplier.delete',
      messageKey: 'supplier.deleteConfirm',
      subject: supplier.nameAr,
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.suppliers.delete(supplier.id).subscribe();
        }
      });
  }
}
