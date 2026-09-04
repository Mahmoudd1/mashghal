import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';

import { FabricColor } from '../../../core/models/api.models';
import { FabricService } from '../fabric.service';

export interface FabricColorDialogData {
  fabricTypeId: number;
  /** Absent when creating. */
  color?: FabricColor;
}

@Component({
  selector: 'app-fabric-color-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    TranslatePipe,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ (data.color ? 'fabric.editColor' : 'fabric.addColor') | translate }}
    </h2>
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
export class FabricColorDialog {
  protected readonly dialogRef = inject<MatDialogRef<FabricColorDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<FabricColorDialogData>(MAT_DIALOG_DATA);
  private readonly fabrics = inject(FabricService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.nonNullable.group({
    nameAr: [this.data.color?.nameAr ?? '', [Validators.required, Validators.maxLength(128)]],
    nameEn: [this.data.color?.nameEn ?? '', Validators.maxLength(128)],
    active: [this.data.color?.active ?? true],
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
      active: raw.active,
    };

    const call = this.data.color
      ? this.fabrics.updateColor(this.data.color.id, request)
      : this.fabrics.addColor(this.data.fabricTypeId, request);

    call.subscribe({
      next: () => this.dialogRef.close(true),
      error: () => this.saving.set(false),
    });
  }
}
