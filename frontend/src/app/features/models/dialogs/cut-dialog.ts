import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';

import { LocalizedNamePipe } from '../../../core/i18n/localized-name.pipe';
import { ReferenceService } from '../../../core/models/reference.service';
import { FabricUnit } from '../../../core/models/api.models';
import { filterByName, findExact } from '../../../shared/lookup-autocomplete/lookup-filter';
import { FabricService } from '../../fabrics/fabric.service';
import { Cut, CutType } from '../../../core/models/api.models';
import { Observable, of, switchMap } from 'rxjs';

import { toIsoDate } from '../../../shared/date-utils';
import { ProductionService } from '../production.service';
import { NumericFieldDirective } from '../../../shared/numerals/numeric-field.directive';
import { ArabicDigitsDirective } from '../../../shared/numerals/arabic-digits.directive';
import { toWesternDigits } from '../../../shared/numerals/arabic-numerals';

export interface CutDialogData {
  /** Absent when creating. */
  cut?: Cut;
}

@Component({
  selector: 'app-cut-dialog',
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
    MatSelectModule,
    TranslatePipe,
    LocalizedNamePipe,
    NumericFieldDirective,
    ArabicDigitsDirective,
  ],
  templateUrl: './cut-dialog.html',
  styleUrl: '../../fabrics/dialogs/dialog-form.scss',
})
export class CutDialog {
  protected readonly dialogRef = inject<MatDialogRef<CutDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<CutDialogData>(MAT_DIALOG_DATA);
  protected readonly production = inject(ProductionService);
  protected readonly reference = inject(ReferenceService);
  protected readonly fabrics = inject(FabricService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly cutTypes: CutType[] = ['MAIN', 'SECONDARY', 'DERBY'];
  protected readonly saving = signal(false);

  /**
   * Cutting happens at Agamy unless told otherwise, so a new cut lands there
   * without anyone choosing. Applied once the branch list arrives.
   */
  private readonly defaultBranch = effect(() => {
    const branches = this.reference.branches.value();
    if (this.data.cut || branches.length === 0 || this.form.controls.branchId.value !== null) {
      return;
    }
    const agamy = branches.find((branch) => branch.code === 'AGAMY') ?? branches[0];
    this.form.controls.branchId.setValue(agamy.id);
  });
  protected readonly today = new Date();

  /** The type fixes the parent rule and cannot change once the cut exists. */
  protected readonly typeLocked = this.data.cut !== undefined;

  protected readonly form = this.formBuilder.nonNullable.group({
    cutNumber: [this.data.cut?.cutNumber ?? '', [Validators.required, Validators.maxLength(64)]],
    cutType: [
      { value: this.data.cut?.cutType ?? ('MAIN' as CutType), disabled: this.typeLocked },
      Validators.required,
    ],
    parentMainCutId: [this.data.cut?.parentMainCutId ?? (null as number | null)],
    branchId: [this.data.cut?.branchId ?? (null as number | null), Validators.required],
    fabricTypeName: [this.data.cut?.fabricTypeNameAr ?? '', Validators.maxLength(128)],
    newTypeUnit: ['KG' as FabricUnit],
    cutDate: [this.data.cut ? new Date(this.data.cut.cutDate) : new Date(), Validators.required],
    cutLength: [this.data.cut?.cutLength ?? (null as number | null)],
    modelDescription: [this.data.cut?.modelDescription ?? '', Validators.maxLength(512)],
    modelNumber: [this.data.cut?.primaryModelNumber ?? '', Validators.maxLength(64)],
    modelNameAr: [this.data.cut?.primaryModelNameAr ?? '', Validators.maxLength(128)],
    modelSewingBranchId: [null as number | null],
    labelAr: [this.data.cut?.labelAr ?? '', Validators.maxLength(128)],
    labelEn: [this.data.cut?.labelEn ?? '', Validators.maxLength(128)],
    note: [this.data.cut?.note ?? '', Validators.maxLength(512)],
  });

  private readonly selectedType = toSignal(this.form.controls.cutType.valueChanges, {
    initialValue: this.form.controls.cutType.value,
  });

  /** Only SECONDARY and DERBY cuts name a parent. */
  protected readonly needsParent = computed(() => this.selectedType() !== 'MAIN');

  /** A cut cannot be its own parent, and only MAIN cuts are eligible. */
  protected readonly parentOptions = computed(() =>
    this.production.mainCuts.value().content.filter((cut) => cut.id !== this.data.cut?.id),
  );

  protected onTypeChange(): void {
    if (!this.needsParent()) {
      this.form.controls.parentMainCutId.setValue(null);
    }
  }

  private readonly typedType = toSignal(this.form.controls.fabricTypeName.valueChanges, {
    initialValue: this.form.controls.fabricTypeName.value,
  });

  protected readonly typeSuggestions = computed(() =>
    filterByName(this.fabrics.types.value(), this.typedType(), (type) => type.nameAr),
  );

  protected readonly matchedType = computed(() =>
    findExact(this.fabrics.types.value(), this.typedType(), (type) => type.nameAr),
  );

  /** A typed name matching nothing will create a fabric type, which needs a unit. */
  protected readonly creatingType = computed(
    () => this.typedType().trim() !== '' && this.matchedType() === undefined,
  );

  protected readonly units: FabricUnit[] = ['KG', 'LENGTH'];

  private readonly typedModel = toSignal(this.form.controls.modelNumber.valueChanges, {
    initialValue: this.form.controls.modelNumber.value,
  });

  protected readonly modelSuggestions = computed(() =>
    filterByName(this.production.models.value(), this.typedModel(), (model) => model.modelNumber),
  );

  protected readonly matchedModel = computed(() =>
    findExact(this.production.models.value(), this.typedModel(), (model) => model.modelNumber),
  );

  /**
   * Nearly every cut is a new model, so the common path is creating one — the
   * hint says which of the two is about to happen.
   */
  protected readonly creatingModel = computed(
    () => this.typedModel().trim() !== '' && this.matchedModel() === undefined,
  );

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const raw = this.form.getRawValue();
    if (raw.cutType !== 'MAIN' && raw.parentMainCutId === null) {
      this.form.controls.parentMainCutId.setErrors({ required: true });
      return;
    }

    this.saving.set(true);
    this.resolveFabricType(raw.fabricTypeName, raw.newTypeUnit).subscribe({
      next: (fabricTypeId) => this.persist(raw, fabricTypeId),
      error: () => this.saving.set(false),
    });
  }

  /** Creates the fabric type first when the typed name is new. */
  private resolveFabricType(name: string, unit: FabricUnit): Observable<number | null> {
    if (name.trim() === '') {
      return of(null);
    }
    const existing = this.matchedType();
    if (existing) {
      return of(existing.id);
    }
    return this.fabrics
      .createType({ nameAr: name.trim(), nameEn: null, unit, active: true })
      .pipe(switchMap((created) => of(created.id)));
  }

  private persist(
    raw: ReturnType<typeof this.form.getRawValue>,
    fabricTypeId: number | null,
  ): void {
    const request = {
      fabricTypeId,
      cutNumber: raw.cutNumber.trim(),
      cutType: raw.cutType,
      parentMainCutId: raw.cutType === 'MAIN' ? null : raw.parentMainCutId,
      branchId: raw.branchId!,

      cutDate: toIsoDate(raw.cutDate),
      cutLength: raw.cutLength,
      modelDescription: raw.modelDescription.trim() || null,
      modelNumber: toWesternDigits(raw.modelNumber).trim() || null,
      modelNameAr: raw.modelNameAr.trim() || null,
      modelSewingBranchId: raw.modelSewingBranchId,
      labelAr: raw.labelAr.trim() || null,
      labelEn: raw.labelEn.trim() || null,
      note: raw.note.trim() || null,
    };

    const call = this.data.cut
      ? this.production.updateCut(this.data.cut.id, request)
      : this.production.createCut(request);

    call.subscribe({
      next: () => this.dialogRef.close(true),
      error: () => this.saving.set(false),
    });
  }
}
