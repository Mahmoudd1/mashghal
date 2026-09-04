import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';

import { Cut, CutModelSize } from '../../../core/models/api.models';
import { ReferenceService } from '../../../core/models/reference.service';
import { filterByName, findExact } from '../../../shared/lookup-autocomplete/lookup-filter';
import { SizeService } from '../../../core/models/size.service';
import { ProductionService } from '../production.service';

export interface CutSizeDialogData {
  cut: Cut;
  /** Absent when adding a size to the marker. */
  row?: CutModelSize;
}

/**
 * One line of the marker: how many pieces of a size a single layer yields.
 *
 * <p>This is the input piece counts derive from — the cut's allocation quantities
 * follow it rather than being typed in.
 */
@Component({
  selector: 'app-cut-size-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslatePipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'cut.addMarker' | translate }}</h2>
    <mat-dialog-content>
      <p class="action-context">
        {{ data.cut.cutNumber }} · {{ 'cut.totalLayers' | translate }}: {{ data.cut.totalLayers }}
      </p>

      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'model.label' | translate }}</mat-label>
          <mat-select formControlName="modelId">
            @for (model of production.models.value(); track model.id) {
              <mat-option [value]="model.id"
                >{{ model.modelNumber }} — {{ model.nameAr }}</mat-option
              >
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'size.label' | translate }}</mat-label>
          <mat-select formControlName="garmentSizeId">
            @for (category of sizes.categories.value(); track category.id) {
              @for (size of category.sizes; track size.id) {
                <mat-option [value]="size.id">{{ size.code }} — {{ category.nameAr }}</mat-option>
              }
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'cut.piecesPerLayer' | translate }}</mat-label>
          <input
            matInput
            type="number"
            min="1"
            step="1"
            formControlName="piecesPerLayer"
            dir="ltr"
          />
          <mat-hint>{{ 'cut.derivedHint' | translate }}</mat-hint>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button [disabled]="form.invalid || saving()" (click)="save()">
        {{ 'common.save' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styleUrl: '../../fabrics/dialogs/dialog-form.scss',
})
export class CutSizeDialog {
  protected readonly dialogRef = inject<MatDialogRef<CutSizeDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<CutSizeDialogData>(MAT_DIALOG_DATA);
  protected readonly production = inject(ProductionService);
  protected readonly sizes = inject(SizeService);
  protected readonly reference = inject(ReferenceService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.nonNullable.group({
    // Pre-filled from the cut, since nearly every cut has one model. Editable,
    // because a cut can still produce sizes for a second model.
    modelNumber: [
      {
        value: this.data.row?.modelNumber ?? this.data.cut.primaryModelNumber ?? '',
        disabled: this.data.row !== undefined,
      },
      [Validators.required, Validators.maxLength(64)],
    ],
    modelNameAr: ['', Validators.maxLength(128)],
    garmentSizeId: [
      {
        value: this.data.row?.sizeId ?? (null as number | null),
        disabled: this.data.row !== undefined,
      },
      Validators.required,
    ],
    piecesPerLayer: [
      this.data.row?.piecesPerLayer ?? (null as number | null),
      [Validators.required, Validators.min(1)],
    ],
    // Null means "wherever the model is sewn", which is the common case.
    branchId: [this.data.row?.branchOverridden ? (this.data.row?.branchId ?? null) : null],
  });

  /** What the size falls back to when no branch is chosen for it. */
  private readonly typedModel = toSignal(this.form.controls.modelNumber.valueChanges, {
    initialValue: this.form.controls.modelNumber.value,
  });

  protected readonly modelSuggestions = computed(() =>
    filterByName(this.production.models.value(), this.typedModel(), (model) => model.modelNumber),
  );

  private readonly matchedModel = computed(() =>
    findExact(this.production.models.value(), this.typedModel(), (model) => model.modelNumber),
  );

  /** A number matching no model creates one — a model first appears on a cut. */
  protected readonly creatingModel = computed(
    () =>
      this.data.row === undefined &&
      this.typedModel().trim() !== '' &&
      this.matchedModel() === undefined,
  );

  /** What the size falls back to when no branch is chosen for it. */
  protected inheritedBranchName(): string | null {
    return this.matchedModel()?.sewingBranchNameAr ?? this.data.cut.branchNameAr;
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);

    const raw = this.form.getRawValue();
    this.production
      .setModelSize(this.data.cut.id, {
        modelId: this.matchedModel()?.id ?? null,
        modelNumber: raw.modelNumber.trim(),
        modelNameAr: raw.modelNameAr.trim() || null,
        garmentSizeId: raw.garmentSizeId!,
        piecesPerLayer: raw.piecesPerLayer!,
        branchId: raw.branchId,
      })
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving.set(false),
      });
  }
}
