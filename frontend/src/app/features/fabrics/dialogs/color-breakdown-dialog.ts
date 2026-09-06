import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable, forkJoin, of, switchMap } from 'rxjs';

import { FabricIntake } from '../../../core/models/api.models';
import { filterByName, findExact } from '../../../shared/lookup-autocomplete/lookup-filter';
import { NumericFieldDirective } from '../../../shared/numerals/numeric-field.directive';
import { FabricService } from '../fabric.service';

export interface ColorBreakdownDialogData {
  intake: FabricIntake;
}

/**
 * One colour of the batch: its name, how many rolls are that colour, and
 * optionally how much they weigh.
 */
type ColorRow = FormGroup<{
  colorId: FormControl<number | null>;
  colorName: FormControl<string>;
  rollCount: FormControl<number | null>;
  quantity: FormControl<number | null>;
}>;

/**
 * The batch's whole colour breakdown, in one place.
 *
 * <p>Colours arrive together — a batch is unloaded and counted in one go — so
 * they are entered together rather than one dialog at a time. Rows already saved
 * come back filled in and can be corrected here; removing one deletes it.
 *
 * <p>The colour is typed rather than picked: existing colours of this fabric type
 * are suggested, and a name matching none is created under that type on save.
 *
 * <p>The counts need not add up to the batch total — a partial breakdown is the
 * normal state — so nothing here validates against it.
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
    MatTooltipModule,
    TranslatePipe,
    DecimalPipe,
    NumericFieldDirective,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'fabric.colorBreakdown' | translate }}</h2>
    <mat-dialog-content>
      <p class="action-context">
        {{ data.intake.fabricTypeNameAr }} · {{ data.intake.intakeDate }} ·
        {{ data.intake.totalRolls }} {{ 'fabric.totalRolls' | translate }}
      </p>

      <form [formGroup]="form" class="dialog-form">
        @for (row of colorRows.controls; track $index) {
          <div class="color-entry-row" [formGroup]="row">
            <mat-form-field appearance="outline">
              <mat-label>{{ 'fabric.color' | translate }}</mat-label>
              <input
                matInput
                formControlName="colorName"
                [matAutocomplete]="colorAuto"
                autocomplete="off"
              />
              <mat-autocomplete #colorAuto="matAutocomplete">
                @for (color of suggestionsFor($index); track color.id) {
                  <mat-option [value]="color.nameAr">{{ color.nameAr }}</mat-option>
                }
              </mat-autocomplete>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>{{ 'fabric.totalRolls' | translate }}</mat-label>
              <input
                matInput
                type="number"
                min="1"
                step="1"
                formControlName="rollCount"
                dir="ltr"
              />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>{{ 'fabric.colorQuantityOptional' | translate }}</mat-label>
              <input
                matInput
                type="number"
                step="0.001"
                min="0"
                formControlName="quantity"
                dir="ltr"
              />
              <span matTextSuffix>&nbsp;{{ 'unit.' + data.intake.unit | translate }}</span>
            </mat-form-field>

            <button
              mat-icon-button
              type="button"
              [matTooltip]="'fabric.removeColorRow' | translate"
              (click)="removeColor($index)"
            >
              <mat-icon>close</mat-icon>
            </button>
          </div>

          @if (creatingColorAt($index)) {
            <p class="creating-hint">
              <mat-icon inline>add_circle</mat-icon>
              {{ 'common.willCreate' | translate }}
            </p>
          }
        }

        <button mat-stroked-button type="button" class="add-color-button" (click)="addColor()">
          <mat-icon>add</mat-icon>
          <span>{{ 'fabric.addColor' | translate }}</span>
        </button>

        <!-- Advisory: a breakdown that falls short of the batch is the normal
             state, so this counts rather than complains. -->
        <p class="action-hint">
          {{ 'fabric.assigned' | translate }}: <strong>{{ assignedRolls() }}</strong> /
          {{ data.intake.totalRolls }} {{ 'fabric.totalRolls' | translate }}
          @if (assignedWeight() > 0) {
            · {{ assignedWeight() | number: '1.0-3' }} {{ 'unit.' + data.intake.unit | translate }}
          }
        </p>
        @if (assignedRolls() > data.intake.totalRolls) {
          <p class="soft-warning soft-warning-strong">
            <mat-icon inline>warning</mat-icon>
            {{
              'fabric.overAssignedWarning'
                | translate: { assigned: assignedRolls(), total: data.intake.totalRolls }
            }}
          </p>
        }
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

  /** Colours whose row was deleted here, to be removed from the batch on save. */
  private readonly dropped: number[] = [];

  protected readonly form = this.formBuilder.nonNullable.group({
    colors: this.formBuilder.array<ColorRow>(
      this.data.intake.colorBreakdown.length > 0
        ? this.data.intake.colorBreakdown.map((row) =>
            this.colorRow(row.colorId, row.colorNameAr, row.rollCount, row.quantity),
          )
        : [this.colorRow()],
    ),
  });

  protected get colorRows(): FormArray<ColorRow> {
    return this.form.controls.colors;
  }

  /** Colours already known for this batch's fabric type. */
  private readonly knownColors = computed(() =>
    this.fabrics.colorsOfType(this.data.intake.fabricTypeId).filter((color) => color.active),
  );

  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly assignedRolls = computed(() => {
    this.formValue();
    return this.colorRows.controls.reduce(
      (sum, row) => sum + (Number(row.controls.rollCount.value) || 0),
      0,
    );
  });

  protected readonly assignedWeight = computed(() => {
    this.formValue();
    return this.colorRows.controls.reduce(
      (sum, row) => sum + (Number(row.controls.quantity.value) || 0),
      0,
    );
  });

  protected suggestionsFor(index: number): { id: number; nameAr: string }[] {
    this.formValue();
    return filterByName(this.knownColors(), this.typedNameAt(index), (color) => color.nameAr);
  }

  protected creatingColorAt(index: number): boolean {
    this.formValue();
    return this.typedNameAt(index).trim() !== '' && this.matchedColorAt(index) === undefined;
  }

  protected addColor(): void {
    this.colorRows.push(this.colorRow());
  }

  /** A row that was already saved is remembered, so saving deletes it too. */
  protected removeColor(index: number): void {
    const colorId = this.colorRows.at(index).controls.colorId.value;
    if (colorId !== null) {
      this.dropped.push(colorId);
    }
    this.colorRows.removeAt(index);
    if (this.colorRows.length === 0) {
      this.addColor();
    }
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);

    const writes = this.colorRows.controls.map((row, index) =>
      this.resolveColorAt(index).pipe(
        switchMap((fabricColorId) =>
          this.fabrics.setColorBreakdown(this.data.intake.id, {
            fabricColorId,
            rollCount: Number(row.controls.rollCount.value),
            quantity: Number(row.controls.quantity.value) || null,
          }),
        ),
      ),
    );
    const deletes = this.dropped.map((colorId) =>
      this.fabrics.removeColorBreakdown(this.data.intake.id, colorId),
    );

    // Deletions first: a colour dropped and re-added in one sitting must not be
    // written and then removed again.
    const run =
      deletes.length > 0
        ? forkJoin(deletes).pipe(switchMap(() => forkJoin(writes)))
        : forkJoin(writes);

    run.subscribe({
      next: () => this.dialogRef.close(true),
      error: () => this.saving.set(false),
    });
  }

  private colorRow(
    colorId: number | null = null,
    colorName = '',
    rollCount: number | null = null,
    quantity: number | null = null,
  ): ColorRow {
    return this.formBuilder.nonNullable.group({
      colorId: [colorId],
      colorName: [colorName, [Validators.required, Validators.maxLength(128)]],
      rollCount: [rollCount, [Validators.required, Validators.min(1)]],
      quantity: [quantity],
    });
  }

  private typedNameAt(index: number): string {
    return this.colorRows.at(index)?.controls.colorName.value ?? '';
  }

  private matchedColorAt(index: number) {
    return findExact(this.knownColors(), this.typedNameAt(index), (color) => color.nameAr);
  }

  /** Creates the colour under this batch's fabric type when the name is new. */
  private resolveColorAt(index: number): Observable<number> {
    const existing = this.matchedColorAt(index);
    if (existing) {
      return of(existing.id);
    }
    return this.fabrics
      .addColor(this.data.intake.fabricTypeId, {
        nameAr: this.typedNameAt(index).trim(),
        nameEn: null,
        active: true,
      })
      .pipe(switchMap((created) => of(created.id)));
  }
}
