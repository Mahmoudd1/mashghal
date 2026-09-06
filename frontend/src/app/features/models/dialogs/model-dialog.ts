import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';

import { ProductionModel } from '../../../core/models/api.models';
import { ReferenceService } from '../../../core/models/reference.service';
import { ProductionService } from '../production.service';
import { ArabicDigitsDirective } from '../../../shared/numerals/arabic-digits.directive';

export interface ModelDialogData {
  /** Absent when creating. */
  model?: ProductionModel;
}

@Component({
  selector: 'app-model-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslatePipe,
    ArabicDigitsDirective,
  ],
  template: `
    <h2 mat-dialog-title>{{ (data.model ? 'model.edit' : 'model.add') | translate }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'model.number' | translate }}</mat-label>
          <input matInput appArabicDigits formControlName="modelNumber" dir="ltr" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'model.nameAr' | translate }}</mat-label>
          <input matInput formControlName="nameAr" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'model.nameEn' | translate }}</mat-label>
          <input matInput formControlName="nameEn" dir="ltr" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'common.notes' | translate }}</mat-label>
          <textarea matInput rows="2" formControlName="note"></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'model.sewingBranch' | translate }}</mat-label>
          <mat-select formControlName="sewingBranchId">
            <mat-option [value]="null">{{ 'common.none' | translate }}</mat-option>
            @for (branch of reference.branches.value(); track branch.id) {
              <mat-option [value]="branch.id">{{ branch.nameAr }}</mat-option>
            }
          </mat-select>
          <mat-hint>{{ 'model.sewingBranchHint' | translate }}</mat-hint>
        </mat-form-field>

        <mat-checkbox formControlName="active">{{ 'common.active' | translate }}</mat-checkbox>
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
export class ModelDialog {
  protected readonly dialogRef = inject<MatDialogRef<ModelDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<ModelDialogData>(MAT_DIALOG_DATA);
  private readonly production = inject(ProductionService);
  protected readonly reference = inject(ReferenceService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.nonNullable.group({
    modelNumber: [
      this.data.model?.modelNumber ?? '',
      [Validators.required, Validators.maxLength(64)],
    ],
    nameAr: [this.data.model?.nameAr ?? '', [Validators.required, Validators.maxLength(128)]],
    nameEn: [this.data.model?.nameEn ?? '', Validators.maxLength(128)],
    note: [this.data.model?.note ?? '', Validators.maxLength(512)],
    sewingBranchId: [this.data.model?.sewingBranchId ?? (null as number | null)],
    active: [this.data.model?.active ?? true],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);

    const raw = this.form.getRawValue();
    const request = {
      modelNumber: raw.modelNumber.trim(),
      nameAr: raw.nameAr.trim(),
      nameEn: raw.nameEn.trim() || null,
      note: raw.note.trim() || null,
      sewingBranchId: raw.sewingBranchId,
      active: raw.active,
    };

    const call = this.data.model
      ? this.production.updateModel(this.data.model.id, request)
      : this.production.createModel(request);

    call.subscribe({
      next: () => this.dialogRef.close(true),
      error: () => this.saving.set(false),
    });
  }
}
