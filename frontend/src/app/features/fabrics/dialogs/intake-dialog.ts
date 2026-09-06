import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable, of, switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { FabricIntake, FabricType, FabricUnit, Supplier } from '../../../core/models/api.models';
import { filterByName, findExact } from '../../../shared/lookup-autocomplete/lookup-filter';
import { toIsoDate } from '../../../shared/date-utils';
import { SupplierService } from '../../suppliers/supplier.service';
import { FabricService } from '../fabric.service';
import { NumericFieldDirective } from '../../../shared/numerals/numeric-field.directive';

export interface IntakeDialogData {
  /** Absent when recording a new purchase. */
  intake?: FabricIntake;
  /** Pre-selects the derby pool, e.g. when adding stock from a derby panel. */
  derbyPool?: boolean;
}

/**
 * A purchase: date, roll count, total quantity, price per unit.
 *
 * <p>Fabric type and supplier are typed rather than picked from a closed list —
 * existing names are suggested as you type, and a name that does not match one is
 * created on save. A new fabric type also needs its unit, so that field appears
 * only when the typed name is genuinely new.
 *
 * <p>Colour is deliberately not asked for here; the breakdown is added afterwards.
 */
@Component({
  selector: 'app-intake-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    TranslatePipe,
    NumericFieldDirective,
  ],
  templateUrl: './intake-dialog.html',
  styleUrl: './dialog-form.scss',
})
export class IntakeDialog {
  protected readonly dialogRef = inject<MatDialogRef<IntakeDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<IntakeDialogData>(MAT_DIALOG_DATA);
  protected readonly fabrics = inject(FabricService);
  protected readonly suppliers = inject(SupplierService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly auth = inject(AuthService);
  protected readonly saving = signal(false);
  protected readonly today = new Date();
  protected readonly units: FabricUnit[] = ['KG', 'LENGTH'];

  /** Once fabric has been cut from a batch it cannot change pool. */
  protected readonly poolLocked = (this.data.intake?.consumedRolls ?? 0) > 0;
  /** The fabric type is fixed after the fact: its unit governs every figure here. */
  protected readonly typeLocked = this.data.intake !== undefined;

  protected readonly form = this.formBuilder.nonNullable.group({
    fabricTypeName: [
      { value: this.data.intake?.fabricTypeNameAr ?? '', disabled: this.typeLocked },
      [Validators.required, Validators.maxLength(128)],
    ],
    newTypeUnit: ['KG' as FabricUnit],
    supplierName: [this.data.intake?.supplierNameAr ?? '', Validators.maxLength(128)],
    derbyPool: [
      {
        value: this.data.intake
          ? this.data.intake.pool === 'DERBY'
          : (this.data.derbyPool ?? false),
        disabled: this.poolLocked,
      },
    ],
    intakeDate: [
      this.data.intake ? new Date(this.data.intake.intakeDate) : new Date(),
      Validators.required,
    ],
    totalRolls: [
      this.data.intake?.totalRolls ?? (null as number | null),
      [Validators.required, Validators.min(this.data.intake?.consumedRolls || 1)],
    ],
    totalQuantity: [
      this.data.intake?.totalQuantity ?? (null as number | null),
      [Validators.required, Validators.min(0.001)],
    ],
    pricePerUnit: [
      this.data.intake?.pricePerUnit ?? (null as number | null),
      [Validators.required, Validators.min(0)],
    ],
    note: [this.data.intake?.note ?? '', Validators.maxLength(512)],
  });

  private readonly typedType = toSignal(this.form.controls.fabricTypeName.valueChanges, {
    initialValue: this.form.controls.fabricTypeName.value,
  });

  private readonly typedSupplier = toSignal(this.form.controls.supplierName.valueChanges, {
    initialValue: this.form.controls.supplierName.value,
  });

  protected readonly typeSuggestions = computed(() =>
    filterByName(this.fabrics.types.value(), this.typedType(), (type) => type.nameAr),
  );

  protected readonly supplierSuggestions = computed(() =>
    filterByName(
      this.suppliers.suppliers.value(),
      this.typedSupplier(),
      (supplier) => supplier.nameAr,
    ),
  );

  /** The existing record the typed name resolves to, if any. */
  protected readonly matchedType = computed(() =>
    findExact(this.fabrics.types.value(), this.typedType(), (type) => type.nameAr),
  );

  protected readonly matchedSupplier = computed(() =>
    findExact(
      this.suppliers.suppliers.value(),
      this.typedSupplier(),
      (supplier) => supplier.nameAr,
    ),
  );

  /** A name that matches nothing will create a record — say so before it happens. */
  protected readonly creatingType = computed(
    () => !this.typeLocked && this.typedType().trim() !== '' && this.matchedType() === undefined,
  );

  protected readonly creatingSupplier = computed(
    () => this.typedSupplier().trim() !== '' && this.matchedSupplier() === undefined,
  );

  /** The unit in play: the matched type's, or the one chosen for a new type. */
  protected readonly effectiveUnit = computed<FabricUnit>(
    () => this.matchedType()?.unit ?? this.form.controls.newTypeUnit.value,
  );

  protected readonly canUseDerby = computed(() => this.matchedType()?.hasDerby ?? false);

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();

    // Create the fabric type and supplier first when the typed names are new, so
    // the intake is recorded against real records rather than free-floating text.
    this.resolveFabricType(raw.fabricTypeName, raw.newTypeUnit)
      .pipe(
        switchMap((fabricTypeId) =>
          this.resolveSupplier(raw.supplierName).pipe(
            switchMap((supplierId) =>
              this.persist({
                fabricTypeId,
                supplierId,
                derbyPool: raw.derbyPool && this.canUseDerby(),
                intakeDate: toIsoDate(raw.intakeDate),
                totalRolls: raw.totalRolls!,
                totalQuantity: raw.totalQuantity!,
                // Left null when unknown, or when a non-owner records the purchase:
                // the field is not theirs to set and must not be blanked either.
                pricePerUnit: this.auth.isOwner()
                  ? raw.pricePerUnit
                  : (this.data.intake?.pricePerUnit ?? null),
                note: raw.note.trim() || null,
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

  private resolveFabricType(name: string, unit: FabricUnit): Observable<number> {
    if (this.data.intake) {
      return of(this.data.intake.fabricTypeId);
    }
    const existing = this.matchedType();
    if (existing) {
      return of(existing.id);
    }
    return this.fabrics
      .createType({ nameAr: name.trim(), nameEn: null, unit, active: true })
      .pipe(switchMap((created: FabricType) => of(created.id)));
  }

  private resolveSupplier(name: string): Observable<number | null> {
    if (name.trim() === '') {
      return of(null);
    }
    const existing = this.matchedSupplier();
    if (existing) {
      return of(existing.id);
    }
    return this.suppliers
      .create({ nameAr: name.trim(), nameEn: null, phone: null, note: null, active: true })
      .pipe(switchMap((created: Supplier) => of(created.id)));
  }

  private persist(request: Parameters<FabricService['createIntake']>[0]) {
    return this.data.intake
      ? this.fabrics.updateIntake(this.data.intake.id, request)
      : this.fabrics.createIntake(request);
  }
}
