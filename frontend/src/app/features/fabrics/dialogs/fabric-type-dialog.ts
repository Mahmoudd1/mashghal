import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';

import { FabricType, FabricUnit } from '../../../core/models/api.models';
import { FabricService } from '../fabric.service';

export interface FabricTypeDialogData {
  /** Absent when creating. */
  type?: FabricType;
  /** True once rolls exist, which freezes the unit. */
  unitLocked: boolean;
}

@Component({
  selector: 'app-fabric-type-dialog',
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
  ],
  template: `
    <h2 mat-dialog-title>{{ (data.type ? 'fabric.editType' : 'fabric.addType') | translate }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'fabric.nameAr' | translate }}</mat-label>
          <input matInput formControlName="nameAr" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'fabric.nameEn' | translate }}</mat-label>
          <input matInput formControlName="nameEn" dir="ltr" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'unit.label' | translate }}</mat-label>
          <mat-select formControlName="unit">
            @for (unit of units; track unit) {
              <mat-option [value]="unit">{{ 'unit.' + unit | translate }}</mat-option>
            }
          </mat-select>
          @if (data.unitLocked) {
            <mat-hint>{{ 'fabric.unitLockedHint' | translate }}</mat-hint>
          }
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
  styleUrl: './dialog-form.scss',
})
export class FabricTypeDialog {
  protected readonly dialogRef = inject<MatDialogRef<FabricTypeDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<FabricTypeDialogData>(MAT_DIALOG_DATA);
  private readonly fabrics = inject(FabricService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly units: FabricUnit[] = ['KG', 'LENGTH'];
  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.nonNullable.group({
    nameAr: [this.data.type?.nameAr ?? '', [Validators.required, Validators.maxLength(128)]],
    nameEn: [this.data.type?.nameEn ?? '', Validators.maxLength(128)],
    unit: [
      { value: this.data.type?.unit ?? ('KG' as FabricUnit), disabled: this.data.unitLocked },
      Validators.required,
    ],
    active: [this.data.type?.active ?? true],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);

    const raw = this.form.getRawValue();
    const request = {
      nameAr: raw.nameAr.trim(),
      nameEn: raw.nameEn.trim() || null,
      unit: raw.unit,
      active: raw.active,
    };

    const call = this.data.type
      ? this.fabrics.updateType(this.data.type.id, request)
      : this.fabrics.createType(request);

    call.subscribe({
      next: () => this.dialogRef.close(true),
      error: () => this.saving.set(false),
    });
  }
}
