import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';

import { LocalizedNamePipe } from '../../../core/i18n/localized-name.pipe';
import { Cut, CutModelAllocation } from '../../../core/models/api.models';
import { ReferenceService } from '../../../core/models/reference.service';
import { ProductionService } from '../production.service';
import { NumericFieldDirective } from '../../../shared/numerals/numeric-field.directive';

export interface ModelAllocationDialogData {
  cut: Cut;
  /** Absent when adding a new allocation. */
  allocation?: CutModelAllocation;
}

/**
 * Allocates pieces from a cut to a model at a branch.
 *
 * When the chosen model is already fed by a different main cut, the dialog says
 * so before saving — that case is legitimate but rare, so it is worth a second
 * look rather than a silent accept.
 */
@Component({
  selector: 'app-model-allocation-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    TranslatePipe,
    LocalizedNamePipe,
    NumericFieldDirective,
  ],
  templateUrl: './model-allocation-dialog.html',
  styleUrl: '../../fabrics/dialogs/dialog-form.scss',
})
export class ModelAllocationDialog {
  protected readonly dialogRef = inject<MatDialogRef<ModelAllocationDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<ModelAllocationDialogData>(MAT_DIALOG_DATA);
  protected readonly production = inject(ProductionService);
  protected readonly reference = inject(ReferenceService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.nonNullable.group({
    modelId: [this.data.allocation?.modelId ?? (null as number | null), Validators.required],
    branchId: [this.data.allocation?.branchId ?? (null as number | null), Validators.required],
    quantityAllocated: [
      this.data.allocation?.quantityAllocated ?? (null as number | null),
      [Validators.required, Validators.min(1)],
    ],
    note: [this.data.allocation?.note ?? '', Validators.maxLength(512)],
  });

  private readonly selectedModelId = toSignal(this.form.controls.modelId.valueChanges, {
    initialValue: this.form.controls.modelId.value,
  });

  protected readonly selectedModel = computed(() =>
    this.production.models.value().find((model) => model.id === this.selectedModelId()),
  );

  /**
   * True when this model already draws from a main cut other than this one —
   * the rare multi-main-cut case worth calling out at entry time.
   */
  protected readonly warnsMultipleMainCuts = computed(() => {
    const model = this.selectedModel();
    if (!model || this.data.cut.cutType !== 'MAIN') {
      return false;
    }
    const alreadyFedByThisCut = this.data.cut.modelAllocations.some(
      (row) => row.modelId === model.id,
    );
    return alreadyFedByThisCut ? model.mainCutCount > 1 : model.mainCutCount >= 1;
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);

    const raw = this.form.getRawValue();
    this.production
      .allocateToModel(this.data.cut.id, {
        modelId: raw.modelId!,
        branchId: raw.branchId!,
        quantityAllocated: raw.quantityAllocated!,
        note: raw.note.trim() || null,
      })
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving.set(false),
      });
  }
}
