import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { LocalizedNamePipe } from '../../../core/i18n/localized-name.pipe';
import { ReferenceService } from '../../../core/models/reference.service';
import { SizeService } from '../../../core/models/size.service';
import { FabricUnit } from '../../../core/models/api.models';
import { filterByName, findExact } from '../../../shared/lookup-autocomplete/lookup-filter';
import { FabricService } from '../../fabrics/fabric.service';
import { Cut, CutModelSizeRequest, CutType } from '../../../core/models/api.models';
import { Observable, concat, last, of, switchMap } from 'rxjs';

import { toIsoDate } from '../../../shared/date-utils';
import { ProductionService } from '../production.service';
import { NumericFieldDirective } from '../../../shared/numerals/numeric-field.directive';
import { ArabicDigitsDirective } from '../../../shared/numerals/arabic-digits.directive';
import { toWesternDigits } from '../../../shared/numerals/arabic-numerals';

/** One model row's value: who it is, and what a layer yields of it. */
interface ModelRowValue {
  modelNumber: string;
  modelNameAr: string;
  sewingBranchId: number | null;
  sizes: { garmentSizeId: number | null; piecesPerLayer: number | null }[];
}

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
    MatTooltipModule,
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
  protected readonly sizes = inject(SizeService);
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
    // One entry per model this cut produces. The first is the cut's own model,
    // the one the create call names; the rest join through their marker rows.
    models: this.formBuilder.array([this.modelGroup()]),
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

  // --- models and their markers -------------------------------------------

  /**
   * Every model row's current value, as a signal, so the per-row lookups below
   * recompute when any field in the array changes. Reading the array's controls
   * directly would not, and the suggestions would go stale as you type.
   */
  private readonly modelsRaw = toSignal(this.form.controls.models.valueChanges, {
    initialValue: this.form.controls.models.getRawValue(),
  });

  /** An untyped FormArray erases the row shape; this puts it back in one place. */
  private readonly modelsValue = computed(() => this.modelsRaw() as ModelRowValue[]);

  protected get modelRows(): FormGroup[] {
    return this.form.controls.models.controls as FormGroup[];
  }

  protected sizeRows(index: number): FormGroup[] {
    return (
      this.form.controls.models.at(index).get('sizes') as never as {
        controls: FormGroup[];
      }
    ).controls;
  }

  private typedModelAt(index: number): string {
    return (this.modelsValue()[index]?.modelNumber ?? '').trim();
  }

  protected modelSuggestionsAt(index: number) {
    return filterByName(
      this.production.models.value(),
      this.typedModelAt(index),
      (model) => model.modelNumber,
    );
  }

  protected matchedModelAt(index: number) {
    return findExact(
      this.production.models.value(),
      this.typedModelAt(index),
      (model) => model.modelNumber,
    );
  }

  /**
   * Nearly every cut is a new model, so the common path is creating one — the
   * hint says which of the two is about to happen.
   */
  protected creatingModelAt(index: number): boolean {
    return this.typedModelAt(index) !== '' && this.matchedModelAt(index) === undefined;
  }

  /**
   * What one layer yields for this model: its sizes added up. This is the "4"
   * in "size 10 counts twice, so 101 is 4 a layer".
   */
  protected perLayerAt(index: number): number {
    return (this.modelsValue()[index]?.sizes ?? []).reduce(
      (sum, row) => sum + (row.piecesPerLayer ?? 0),
      0,
    );
  }

  /** The cut's own figure — every model's sizes, per layer. The "7". */
  protected totalPerLayer(): number {
    return this.modelsValue().reduce((sum, _row, index) => sum + this.perLayerAt(index), 0);
  }

  protected addModel(): void {
    this.form.controls.models.push(this.modelGroup());
  }

  protected removeModel(index: number): void {
    this.form.controls.models.removeAt(index);
  }

  protected addSize(index: number): void {
    (
      this.form.controls.models.at(index).get('sizes') as never as {
        push: (group: FormGroup) => void;
      }
    ).push(this.sizeGroup());
  }

  protected removeSize(modelIndex: number, sizeIndex: number): void {
    (
      this.form.controls.models.at(modelIndex).get('sizes') as never as {
        removeAt: (i: number) => void;
      }
    ).removeAt(sizeIndex);
  }

  /** A model row: who it is, and what a layer yields of it. */
  private modelGroup(): FormGroup {
    return this.formBuilder.nonNullable.group({
      modelNumber: [this.data.cut?.primaryModelNumber ?? '', Validators.maxLength(64)],
      modelNameAr: [this.data.cut?.primaryModelNameAr ?? '', Validators.maxLength(128)],
      sewingBranchId: [null as number | null],
      sizes: this.formBuilder.array([] as FormGroup[]),
    });
  }

  private sizeGroup(): FormGroup {
    return this.formBuilder.nonNullable.group({
      garmentSizeId: [null as number | null, Validators.required],
      piecesPerLayer: [1 as number | null, [Validators.required, Validators.min(1)]],
    });
  }

  /**
   * A model number entered twice would have its marker rows split across two
   * rows of a form that is meant to read as one model each, and the second
   * would silently overwrite the first's sizes on the server.
   */
  protected duplicateModel(): boolean {
    const numbers = this.modelsValue()
      .map((row) => toWesternDigits(row.modelNumber).trim())
      .filter((number) => number !== '');
    return new Set(numbers).size !== numbers.length;
  }

  /** The same size twice on one model: the second would replace the first. */
  protected duplicateSize(index: number): boolean {
    const ids = (this.modelsValue()[index]?.sizes ?? [])
      .map((row) => row.garmentSizeId)
      .filter((id) => id !== null);
    return new Set(ids).size !== ids.length;
  }

  /** Sizes belong to a model, so a row with sizes has to say which. */
  protected sizesWithoutModel(index: number): boolean {
    return this.typedModelAt(index) === '' && (this.modelsValue()[index]?.sizes ?? []).length > 0;
  }

  protected blocked(): boolean {
    return (
      this.duplicateModel() ||
      this.modelsValue().some(
        (_row, index) => this.duplicateSize(index) || this.sizesWithoutModel(index),
      )
    );
  }

  protected save(): void {
    if (this.form.invalid || this.saving() || this.blocked()) {
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
    // The first model is the cut's own: the create call names it, which is what
    // makes it the cut's primary model. The others exist only as marker rows.
    const models = raw.models as unknown as ModelRowValue[];
    const first = models[0];
    const request = {
      fabricTypeId,
      cutNumber: raw.cutNumber.trim(),
      cutType: raw.cutType,
      parentMainCutId: raw.cutType === 'MAIN' ? null : raw.parentMainCutId,
      branchId: raw.branchId!,

      cutDate: toIsoDate(raw.cutDate),
      cutLength: raw.cutLength,
      modelDescription: raw.modelDescription.trim() || null,
      modelNumber: toWesternDigits(first.modelNumber).trim() || null,
      modelNameAr: first.modelNameAr.trim() || null,
      modelSewingBranchId: first.sewingBranchId,
      labelAr: raw.labelAr.trim() || null,
      labelEn: raw.labelEn.trim() || null,
      note: raw.note.trim() || null,
    };

    if (this.data.cut) {
      // Editing touches the header only. An existing cut's marker is edited on
      // the cut itself, where the rows can be changed one at a time.
      this.production.updateCut(this.data.cut.id, request).subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving.set(false),
      });
      return;
    }

    const marker = this.markerRequests(models);
    this.production
      .createCut(request)
      .pipe(
        switchMap((cut) =>
          marker.length === 0
            ? of(cut)
            : // Strictly in order, never in parallel: two rows naming the same
              // new model would otherwise both try to create it.
              concat(...marker.map((row) => this.production.setModelSize(cut.id, row))).pipe(
                last(),
              ),
        ),
      )
      .subscribe({
        next: () => this.dialogRef.close(true),
        // The cut may already exist with some of its marker saved. Closing shows
        // it in the list with what did land, next to the error the interceptor
        // reports, rather than stranding the user in a dialog that cannot retry.
        error: () => this.dialogRef.close(true),
      });
  }

  /** Every model's sizes, flattened into the calls that record the marker. */
  private markerRequests(models: ModelRowValue[]): CutModelSizeRequest[] {
    return models.flatMap((model) => {
      const modelNumber = toWesternDigits(model.modelNumber).trim();
      if (modelNumber === '') {
        return [];
      }
      return model.sizes
        .filter((size) => size.garmentSizeId !== null && size.piecesPerLayer !== null)
        .map((size) => ({
          modelId: null,
          modelNumber,
          modelNameAr: model.modelNameAr.trim() || null,
          garmentSizeId: size.garmentSizeId!,
          piecesPerLayer: size.piecesPerLayer!,
          branchId: null,
        }));
    });
  }
}
