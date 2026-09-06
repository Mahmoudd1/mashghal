import { DatePipe, DecimalPipe } from '@angular/common';
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
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable, forkJoin, of, switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { DerbyColorRequest, FabricIntake, FabricType } from '../../../core/models/api.models';
import { toIsoDate } from '../../../shared/date-utils';
import { filterByName, findExact } from '../../../shared/lookup-autocomplete/lookup-filter';
import { NumericFieldDirective } from '../../../shared/numerals/numeric-field.directive';
import { SupplierService } from '../../suppliers/supplier.service';
import { FabricService } from '../fabric.service';

/**
 * Either the purchase the derby was bought with, or the fabric type it belongs
 * to when it was bought on its own. Exactly one is given.
 */
export interface DerbyDialogData {
  purchase?: FabricIntake;
  type?: FabricType;
}

/** One colour of the derby, and how much of it there is. */
type ColorRow = FormGroup<{
  colorName: FormControl<string>;
  quantity: FormControl<number | null>;
}>;

/**
 * Records a derby purchase.
 *
 * <p>Derby is bought the way fabric is — on a date, from a supplier, at a price —
 * and almost always in the same transaction as the fabric itself. Opened from a
 * purchase, that is exactly what it means: the date and supplier are that
 * purchase's and are not asked for again, and its price is offered as the
 * derby's. Opened on its own, the derby belongs to the fabric type and carries
 * its own date, supplier and price.
 *
 * <p>Either way it is bought as colours with a weight each, never as a roll
 * count, so that is all the form asks for.
 */
@Component({
  selector: 'app-derby-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTooltipModule,
    TranslatePipe,
    DecimalPipe,
    DatePipe,
    NumericFieldDirective,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'fabric.addDerby' | translate }}</h2>
    <mat-dialog-content>
      <p class="action-context">
        {{ fabricType().nameAr }} · {{ 'unit.' + fabricType().unit | translate }}
        @if (data.purchase) {
          · {{ 'fabric.boughtWith' | translate }}
          {{ data.purchase.intakeDate | date: 'dd/MM/yyyy' }}
          @if (data.purchase.supplierNameAr) {
            · {{ data.purchase.supplierNameAr }}
          }
        }
      </p>

      <form [formGroup]="form" class="dialog-form">
        <h3 class="dialog-section">{{ 'fabric.derbyColors' | translate }}</h3>

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
              <mat-label>{{ 'fabric.weight' | translate }}</mat-label>
              <input
                matInput
                type="number"
                step="0.001"
                min="0"
                formControlName="quantity"
                dir="ltr"
              />
              <span matTextSuffix>&nbsp;{{ 'unit.' + fabricType().unit | translate }}</span>
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
          {{ 'unit.' + fabricType().unit | translate }}
        </p>

        <!-- Only a derby bought on its own states where it came from; one bought
             with a purchase takes that purchase's date and supplier. -->
        @if (!data.purchase) {
          <h3 class="dialog-section">{{ 'fabric.derbySource' | translate }}</h3>

          <mat-form-field appearance="outline">
            <mat-label>{{ 'fabric.intakeDate' | translate }}</mat-label>
            <input matInput [matDatepicker]="picker" [max]="today" formControlName="intakeDate" />
            <mat-datepicker-toggle matIconSuffix [for]="picker" />
            <mat-datepicker #picker />
          </mat-form-field>

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
            <mat-hint>
              {{
                (data.purchase ? 'fabric.derbyPriceFromPurchase' : 'fabric.derbyInheritedHint')
                  | translate
              }}
            </mat-hint>
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
  protected readonly today = new Date();

  /** The fabric the derby belongs to, however the dialog was opened. */
  protected readonly fabricType = computed<{ id: number; nameAr: string; unit: string }>(() => {
    const purchase = this.data.purchase;
    if (!purchase) {
      return this.data.type!;
    }
    return (
      this.fabrics.types.value().find((type) => type.id === purchase.fabricTypeId) ?? {
        id: purchase.fabricTypeId,
        nameAr: purchase.fabricTypeNameAr,
        unit: purchase.unit,
      }
    );
  });

  protected readonly form = this.formBuilder.nonNullable.group({
    intakeDate: [new Date(), Validators.required],
    supplierName: ['', Validators.maxLength(128)],
    pricePerUnit: [this.data.purchase?.pricePerUnit ?? (null as number | null)],
    note: ['', Validators.maxLength(512)],
    colors: this.formBuilder.array<ColorRow>([this.colorRow()]),
  });

  protected get colorRows(): FormArray<ColorRow> {
    return this.form.controls.colors;
  }

  private readonly knownColors = computed(() =>
    this.fabrics.colorsOfType(this.fabricType().id).filter((color) => color.active),
  );

  /** Re-read on every value change, so the total and hints keep up with typing. */
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
    // A derby bought on its own still starts from what the fabric last cost —
    // it is usually the same fabric from the same market.
    if (!this.data.purchase) {
      this.fabrics.derbyDefaults(this.fabricType().id).subscribe((defaults) => {
        this.form.patchValue({
          supplierName: defaults.supplierNameAr ?? '',
          pricePerUnit: defaults.pricePerUnit,
        });
      });
    }
  }

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

  protected removeColor(index: number): void {
    // Never the last one: a derby purchase with no colours is nothing at all.
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
      .pipe(switchMap((colorIds) => this.persist(colorIds, raw)))
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving.set(false),
      });
  }

  private persist(
    colorIds: number[],
    raw: ReturnType<DerbyDialog['form']['getRawValue']>,
  ): Observable<FabricIntake> {
    const colors = colorIds.map<DerbyColorRequest>((fabricColorId, index) => ({
      fabricColorId,
      quantity: Number(this.colorRows.at(index).controls.quantity.value),
    }));
    // Left null by a non-owner, who never sees the field: the server then falls
    // back to what the fabric itself cost.
    const pricePerUnit = this.auth.isOwner() ? raw.pricePerUnit : null;
    const note = raw.note.trim() || null;

    const purchase = this.data.purchase;
    if (purchase) {
      return this.fabrics.addDerbyToPurchase(purchase.id, { note, pricePerUnit, colors });
    }
    return this.resolveSupplier(raw.supplierName).pipe(
      switchMap((supplierId) =>
        this.fabrics.recordDerbyPurchase(this.fabricType().id, {
          intakeDate: toIsoDate(raw.intakeDate),
          note,
          supplierId,
          pricePerUnit,
          colors,
        }),
      ),
    );
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
      .addColor(this.fabricType().id, {
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
