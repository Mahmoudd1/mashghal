import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../../../core/auth/auth.service';
import { LocalizedNamePipe } from '../../../core/i18n/localized-name.pipe';
import { ProductionModel } from '../../../core/models/api.models';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { clientPage } from '../../../shared/paging/client-page';
import { ModelDialog, ModelDialogData } from '../dialogs/model-dialog';
import { ProductionService } from '../production.service';

/**
 * Models with their derived plan. Expanding a model loads the cuts feeding it —
 * the "which cuts made this model" direction of the many-to-many.
 */
@Component({
  selector: 'app-models-tab',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatChipsModule,
    MatDividerModule,
    MatExpansionModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTooltipModule,
    TranslatePipe,
    LocalizedNamePipe,
  ],
  templateUrl: './models-tab.html',
  styleUrl: '../../fabrics/tabs/tab.scss',
})
export class ModelsTab {
  protected readonly production = inject(ProductionService);
  protected readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly page = clientPage(this.production.models.value);

  protected addModel(): void {
    this.dialog.open<ModelDialog, ModelDialogData, boolean>(ModelDialog, { data: {} });
  }

  protected editModel(model: ProductionModel): void {
    this.dialog.open<ModelDialog, ModelDialogData, boolean>(ModelDialog, { data: { model } });
  }

  protected deleteModel(model: ProductionModel): void {
    const data: ConfirmDialogData = {
      titleKey: 'model.delete',
      messageKey: 'model.deleteConfirm',
      subject: `${model.modelNumber} — ${model.nameAr}`,
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.production.deleteModel(model.id).subscribe();
        }
      });
  }

  /** Loads the feeding cuts lazily, only for the model being expanded. */
  protected onExpand(model: ProductionModel): void {
    this.production.selectedModelId.set(model.id);
  }

  protected onCollapse(model: ProductionModel): void {
    if (this.production.selectedModelId() === model.id) {
      this.production.selectedModelId.set(null);
    }
  }
}
