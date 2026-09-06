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

import { AuthService } from '../../../core/auth/auth.service';
import { DerbyColorRequest, FabricType } from '../../../core/models/api.models';
import { filterByName, findExact } from '../../../shared/lookup-autocomplete/lookup-filter';
import { NumericFieldDirective } from '../../../shared/numerals/numeric-field.directive';
import { SupplierService } from '../../suppliers/supplier.service';
import { FabricService } from '../fabric.service';

export interface DerbyDialogData {
  type: FabricType;
}

/** One colour of the derby being created, and how much of it there is. */
type ColorRow = FormGroup<{
  colorName: FormControl<string>;
  quantity: FormControl<number | null>;
}>;

/**
 * Creates a fabric type's derby, with the fabric that is in it.
 *
 * <p>A derby is bought as a set of colours with a weight each — not as a roll
 * count — so that is what this asks for. The pool and its opening purchase are
 * created together: an empty derby is a pool nobody can cut from, and leaving it
 * empty only meant the stock had to be entered again somewhere else.
 *
 * <p>Supplier and price arrive already filled from the fabric's last purchase,
 * because a derby is bought alongside its main fabric. They stay editable: the
 * same derby is sometimes bought from someone else, or at another price.
 */
@Component({
  selector: 'app-derby-dialog',
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
    <h2 mat-dialog-title>{{ 'fabric.addDerby' | translate }}</h2>
    <mat-dialog-content>
      <p class="action-context">
        {{ data.type.nameAr }} · {{ 'unit.' + data.type.unit | translate }}
      </p>

      <form [formGroup]="form" class="dialog-form">
        <h3 class="dialog-section">{{ 'fabric.derbyColors' | translate }}</h3>

        @for (row of colorRows.controls; track $index) {
          <div class="derby-color-row" [formGroup]="row">
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
              <mat-label>{{ 'fabric.weight' | translate }}</mat-label>
              <input
                matInput
                type="number"
                step="0.001"
                min="0"
                formControlName="quantity"
                dir="ltr"
              />
              <span matTextSuffix>&nbsp;{{ 'unit.' + data.type.unit | translate }}</span>
            </mat-form-field>

            <button
              mat-icon-button
              type="button"
              [disabled]="colorRows.length === 1"
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
          <span>{{ 'fabric.addDerbyColor' | translate }}</span>
        </button>

        <p class="action-hint">
          {{ 'fabric.derbyTotal' | translate }}:
          <strong>{{ totalWeight() | number: '1.0-3' }}</strong>
          {{ 'unit.' + data.type.unit | translate }}
        </p>

        <h3 class="dialog-section">{{ 'fabric.derbySource' | translate }}</h3>

        <!-- Prefilled from the fabric's last purchase, and still editable. -->
        <mat-form-field appearance="outline">
          <mat-label>{{ 'supplier.label' | translate }}</mat-label>
          <input
            matInput
            formControlName="supplierName"
            [matAutocomplete]="supplierAuto"
            autocomplete="off"
          />
          <mat-autocomplete #supplierAuto="matAutocomplete">
            @for (supplier of supplierSuggestions(); track supplier.id) {
              <mat-option [value]="supplier.nameAr">{{ supplier.nameAr }}</mat-option>
            }
          </mat-autocomplete>
          <mat-hint>{{ 'fabric.derbyInheritedHint' | translate }}</mat-hint>
        </mat-form-field>

        @if (creatingSupplier()) {
          <p class="creating-hint">
            <mat-icon inline>add_circle</mat-icon>
            {{ 'common.willCreate' | translate }}
          </p>
        }

        @if (auth.isOwner()) {
          <mat-form-field appearance="outline">
            <mat-label>{{ 'fabric.pricePerUnit' | translate }}</mat-label>
            <input
              matInput
              type="number"
              step="0.001"
              min="0"
              formControlName="pricePerUnit"
              dir="ltr"
            />
            <mat-hint>{{ 'fabric.derbyInheritedHint' | translate }}</mat-hint>
          </mat-form-field>
        }

        <mat-form-field appearance="outline">
          <mat-label>{{ 'common.notes' | translate }}</mat-label>
          <textarea matInput rows="2" formControlName="note"></textarea>
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
export class DerbyDialog {
  protected readonly dialogRef = inject<MatDialogRef<DerbyDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<DerbyDialogData>(MAT_DIALOG_DATA);
  protected readonly auth = inject(AuthService);
  private readonly fabrics = inject(FabricService);
  private readonly suppliers = inject(SupplierService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.nonNullable.group({
    supplierName: ['', Validators.maxLength(128)],
    pricePerUnit: [null as number | null],
    note: ['', Validators.maxLength(512)],
    colors: this.formBuilder.array<ColorRow>([this.colorRow()]),
  });

  protected get colorRows(): FormArray<ColorRow> {
    return this.form.controls.colors;
  }

  /** Colours already known for this fabric type. */
  private readonly knownColors = computed(() =>
    this.fabrics.colorsOfType(this.data.type.id).filter((color) => color.active),
  );

  /** Re-read on every value change, so the totals and hints keep up with typing. */
  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly totalWeight = computed(() => {
    this.formValue();
    return this.colorRows.controls.reduce(
      (sum, row) => sum + (Number(row.controls.quantity.value) || 0),
      0,
    );
  });

  private readonly typedSupplier = toSignal(this.form.controls.supplierName.valueChanges, {
    initialValue: this.form.controls.supplierName.value,
  });

  protected readonly supplierSuggestions = computed(() =>
    filterByName(this.suppliers.suppliers.value(), this.typedSupplier(), (s) => s.nameAr),
  );

  private readonly matchedSupplier = computed(() =>
    findExact(this.suppliers.suppliers.value(), this.typedSupplier(), (s) => s.nameAr),
  );

  protected readonly creatingSupplier = computed(
    () => this.typedSupplier().trim() !== '' && this.matchedSupplier() === undefined,
  );

  constructor() {
    // The whole point of the prefill: the derby is bought with its fabric, so
    // the supplier and price are already known and need not be retyped.
    this.fabrics.derbyDefaults(this.data.type.id).subscribe((defaults) => {
      this.form.patchValue({
        supplierName: defaults.supplierNameAr ?? '',
        pricePerUnit: defaults.pricePerUnit,
      });
    });
  }

  protected suggestionsFor(index: number): { id: number; nameAr: string }[] {
    this.formValue();
    return filterByName(this.knownColors(), this.typedNameAt(index), (color) => color.nameAr);
  }

  protected creatingColorAt(index: number): boolean {
    this.formValue();
    const typed = this.typedNameAt(index).trim();
    return typed !== '' && this.matchedColorAt(index) === undefined;
  }

  protected addColor(): void {
    this.colorRows.push(this.colorRow());
  }

  protected removeColor(index: number): void {
    // Never the last one: a derby with no colours is a pool with no fabric.
    if (this.colorRows.length > 1) {
      this.colorRows.removeAt(index);
    }
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();

    // Colours are resolved to records first — a name matching none is created
    // under this fabric type, exactly as the colour breakdown does it.
    forkJoin(this.colorRows.controls.map((_, index) => this.resolveColorAt(index)))
      .pipe(
        switchMap((colorIds) =>
          this.resolveSupplier(raw.supplierName).pipe(
            switchMap((supplierId) =>
              this.fabrics.createDerby(this.data.type.id, {
                note: raw.note.trim() || null,
                supplierId,
                // Left null by a non-owner, who never sees the field: the server
                // then falls back to what the fabric itself last cost.
                pricePerUnit: this.auth.isOwner() ? raw.pricePerUnit : null,
                colors: colorIds.map<DerbyColorRequest>((fabricColorId, index) => ({
                  fabricColorId,
                  quantity: Number(this.colorRows.at(index).controls.quantity.value),
                })),
              }),
            ),
          ),
        ),
      )
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving.set(false),
      });
  }

  private colorRow(): ColorRow {
    return this.formBuilder.nonNullable.group({
      colorName: ['', [Validators.required, Validators.maxLength(128)]],
      quantity: [null as number | null, [Validators.required, Validators.min(0.001)]],
    });
  }

  private typedNameAt(index: number): string {
    return this.colorRows.at(index)?.controls.colorName.value ?? '';
  }

  private matchedColorAt(index: number) {
    return findExact(this.knownColors(), this.typedNameAt(index), (color) => color.nameAr);
  }

  private resolveColorAt(index: number): Observable<number> {
    const existing = this.matchedColorAt(index);
    if (existing) {
      return of(existing.id);
    }
    return this.fabrics
      .addColor(this.data.type.id, {
        nameAr: this.typedNameAt(index).trim(),
        nameEn: null,
        active: true,
      })
      .pipe(switchMap((created) => of(created.id)));
  }

  private resolveSupplier(name: string): Observable<number | null> {
    const trimmed = name.trim();
    if (trimmed === '') {
      return of(null);
    }
    const existing = this.matchedSupplier();
    if (existing) {
      return of(existing.id);
    }
    return this.suppliers
      .create({ nameAr: trimmed, nameEn: null, phone: null, note: null, active: true })
      .pipe(switchMap((created) => of(created.id)));
  }
}
