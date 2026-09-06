import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable, of, switchMap } from 'rxjs';

import { FabricIntake, FabricIntakeColorRow } from '../../../core/models/api.models';
import { filterByName, findExact } from '../../../shared/lookup-autocomplete/lookup-filter';
import { FabricService } from '../fabric.service';
import { NumericFieldDirective } from '../../../shared/numerals/numeric-field.directive';

export interface ColorBreakdownDialogData {
  intake: FabricIntake;
  /** Absent when assigning a colour for the first time. */
  row?: FabricIntakeColorRow;
}

/**
 * Assigns part of a batch to a colour.
 *
 * <p>The colour is typed rather than picked: existing colours of this fabric type
 * are suggested, and a name matching none is created under that type on save.
 *
 * <p>The counts need not add up to the batch total — a partial breakdown is the
 * normal state — so nothing here validates against it. Quantity is optional.
 */
@Component({
  selector: 'app-color-breakdown-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    TranslatePipe,
    NumericFieldDirective,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'fabric.addColorBreakdown' | translate }}</h2>
    <mat-dialog-content>
      <p class="action-context">
        {{ data.intake.fabricTypeNameAr }} · {{ data.intake.intakeDate }} ·
        {{ data.intake.totalRolls }} {{ 'fabric.totalRolls' | translate }}
      </p>

      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'fabric.color' | translate }}</mat-label>
          <input
            matInput
            formControlName="colorName"
            [matAutocomplete]="colorAuto"
            autocomplete="off"
          />
          <mat-autocomplete #colorAuto="matAutocomplete">
            @for (color of suggestions(); track color.id) {
              <mat-option [value]="color.nameAr">{{ color.nameAr }}</mat-option>
            }
          </mat-autocomplete>
        </mat-form-field>

        @if (creatingColor()) {
          <p class="creating-hint">
            <mat-icon inline>add_circle</mat-icon>
            {{ 'common.willCreate' | translate }}
          </p>
        }

        <mat-form-field appearance="outline">
          <mat-label>{{ 'fabric.totalRolls' | translate }}</mat-label>
          <input matInput type="number" min="1" step="1" formControlName="rollCount" dir="ltr" />
          <mat-hint>
            {{ 'fabric.assigned' | translate }}: {{ data.intake.assignedRolls }} /
            {{ data.intake.totalRolls }}
          </mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'fabric.colorQuantityOptional' | translate }}</mat-label>
          <input matInput type="number" step="0.001" min="0" formControlName="quantity" dir="ltr" />
          <span matTextSuffix>&nbsp;{{ 'unit.' + data.intake.unit | translate }}</span>
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
  styleUrl: './dialog-form.scss',
})
export class ColorBreakdownDialog {
  protected readonly dialogRef = inject<MatDialogRef<ColorBreakdownDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<ColorBreakdownDialogData>(MAT_DIALOG_DATA);
  private readonly fabrics = inject(FabricService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);

  /** Colours already known for this batch's fabric type. */
  private readonly knownColors = computed(() =>
    this.fabrics.colorsOfType(this.data.intake.fabricTypeId).filter((color) => color.active),
  );

  protected readonly form = this.formBuilder.nonNullable.group({
    colorName: [
      { value: this.data.row?.colorNameAr ?? '', disabled: this.data.row !== undefined },
      [Validators.required, Validators.maxLength(128)],
    ],
    rollCount: [
      this.data.row?.rollCount ?? (null as number | null),
      [Validators.required, Validators.min(1)],
    ],
    quantity: [this.data.row?.quantity ?? (null as number | null)],
  });

  private readonly typed = toSignal(this.form.controls.colorName.valueChanges, {
    initialValue: this.form.controls.colorName.value,
  });

  protected readonly suggestions = computed(() =>
    filterByName(this.knownColors(), this.typed(), (color) => color.nameAr),
  );

  private readonly matched = computed(() =>
    findExact(this.knownColors(), this.typed(), (color) => color.nameAr),
  );

  protected readonly creatingColor = computed(
    () => this.data.row === undefined && this.typed().trim() !== '' && this.matched() === undefined,
  );

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();

    this.resolveColor(raw.colorName)
      .pipe(
        switchMap((fabricColorId) =>
          this.fabrics.setColorBreakdown(this.data.intake.id, {
            fabricColorId,
            rollCount: raw.rollCount!,
            quantity: raw.quantity || null,
          }),
        ),
      )
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving.set(false),
      });
  }

  /** Creates the colour under this batch's fabric type when the name is new. */
  private resolveColor(name: string): Observable<number> {
    if (this.data.row) {
      return of(this.data.row.colorId);
    }
    const existing = this.matched();
    if (existing) {
      return of(existing.id);
    }
    return this.fabrics
      .addColor(this.data.intake.fabricTypeId, { nameAr: name.trim(), nameEn: null, active: true })
      .pipe(switchMap((created) => of(created.id)));
  }
}
