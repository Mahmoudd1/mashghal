import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';

import { Supplier } from '../../core/models/api.models';
import { SupplierService } from './supplier.service';
import { ArabicDigitsDirective } from '../../shared/numerals/arabic-digits.directive';

export interface SupplierDialogData {
  /** Absent when adding. */
  supplier?: Supplier;
}

@Component({
  selector: 'app-supplier-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    TranslatePipe,
    ArabicDigitsDirective,
  ],
  template: `
    <h2 mat-dialog-title>{{ (data.supplier ? 'supplier.edit' : 'supplier.add') | translate }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'supplier.name' | translate }}</mat-label>
          <input matInput formControlName="nameAr" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'supplier.nameEn' | translate }}</mat-label>
          <input matInput formControlName="nameEn" dir="ltr" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'supplier.phone' | translate }}</mat-label>
          <input matInput appArabicDigits formControlName="phone" dir="ltr" inputmode="tel" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'common.notes' | translate }}</mat-label>
          <textarea matInput rows="2" formControlName="note"></textarea>
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
  styleUrl: '../fabrics/dialogs/dialog-form.scss',
})
export class SupplierDialog {
  protected readonly dialogRef = inject<MatDialogRef<SupplierDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<SupplierDialogData>(MAT_DIALOG_DATA);
  private readonly suppliers = inject(SupplierService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.nonNullable.group({
    nameAr: [this.data.supplier?.nameAr ?? '', [Validators.required, Validators.maxLength(128)]],
    nameEn: [this.data.supplier?.nameEn ?? '', Validators.maxLength(128)],
    phone: [this.data.supplier?.phone ?? '', Validators.maxLength(64)],
    note: [this.data.supplier?.note ?? '', Validators.maxLength(512)],
    active: [this.data.supplier?.active ?? true],
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
      phone: raw.phone.trim() || null,
      note: raw.note.trim() || null,
      active: raw.active,
    };

    const call = this.data.supplier
      ? this.suppliers.update(this.data.supplier.id, request)
      : this.suppliers.create(request);

    call.subscribe({
      next: () => this.dialogRef.close(true),
      error: () => this.saving.set(false),
    });
  }
}
