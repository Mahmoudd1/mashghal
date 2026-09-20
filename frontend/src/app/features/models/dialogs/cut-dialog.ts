import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  DestroyRef,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
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
import {
  Cut,
  CutEntryMode,
  CutFabricDraw,
  CutModelSizeRequest,
  CutType,
  FabricIntake,
} from '../../../core/models/api.models';
import { Observable, concat, last, of, switchMap, debounceTime } from 'rxjs';

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
    MatButtonToggleModule,
    DatePipe,
    DecimalPipe,
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
  private readonly destroyRef = inject(DestroyRef);
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
    // Fixed once the cut exists: its model and its marker came from that main
    // cut, so re-pointing it would quietly reshape what the run yields.
    parentMainCutId: [
      {
        value: this.data.cut?.parentMainCutId ?? (null as number | null),
        disabled: this.typeLocked,
      },
    ],
    branchId: [this.data.cut?.branchId ?? (null as number | null), Validators.required],
    fabricTypeName: [this.data.cut?.fabricTypeNameAr ?? '', Validators.maxLength(128)],
    newTypeUnit: ['KG' as FabricUnit],
    cutDate: [this.data.cut ? new Date(this.data.cut.cutDate) : new Date(), Validators.required],
    cutLength: [this.data.cut?.cutLength ?? (null as number | null)],
    // Totals, for a cut written up afterwards rather than built roll by roll.
    entryMode: [this.data.cut?.entryMode ?? ('DETAILED' as CutEntryMode)],
    totalRolls: [this.data.cut?.totalRolls ?? (null as number | null)],
    reusedRolls: [this.data.cut?.reusedRolls ?? (null as number | null)],
    totalWeight: [
      this.data.cut
        ? this.data.cut.totalWeightConsumed + this.data.cut.totalWasteWeight
        : (null as number | null),
    ],
    wasteWeight: [this.data.cut?.totalWasteWeight ?? (null as number | null)],
    totalLayers: [this.data.cut?.totalLayers ?? (null as number | null)],
    // Which batch the run was cut from, and which colour of it. Derby always
    // says; a secondary run says instead of being spent from its main cut.
    fromNamedBatch: [this.data.cut?.fabricIntakeId != null],
    fabricColorId: [this.data.cut?.fabricColorId ?? (null as number | null)],
    fabricIntakeId: [this.data.cut?.fabricIntakeId ?? (null as number | null)],
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

  /**
   * How this cut's fabric is being written down. Fixed once a cut exists: the
   * two ways describe the same fabric and would be counted twice.
   */
  protected readonly entryMode = toSignal(this.form.controls.entryMode.valueChanges, {
    initialValue: this.form.controls.entryMode.value,
  });

  protected readonly isSummary = computed(() => this.entryMode() === 'SUMMARY');

  private readonly fromNamedBatch = toSignal(this.form.controls.fromNamedBatch.valueChanges, {
    initialValue: this.form.controls.fromNamedBatch.value,
  });

  protected readonly isDerby = computed(() => this.selectedType() === 'DERBY');
  protected readonly isSecondary = computed(() => this.selectedType() === 'SECONDARY');

  /**
   * Whether this run says which batch its fabric came off, instead of letting it
   * be drawn oldest-batch-first.
   *
   * <p>A derby run always does: derby is bought and asked for by colour, so the
   * purchase it left is something the person knows and the system cannot guess.
   * A secondary run may, when its fabric came off the shelf rather than out of
   * the main cut it hangs off.
   */
  protected readonly namesBatch = computed(
    () => this.isSummary() && (this.isDerby() || (this.isSecondary() && this.fromNamedBatch())),
  );

  /** A secondary run is spent from its main cut, not drawn from the batches. */
  protected readonly absorbedByParent = computed(
    () => this.isSummary() && this.isSecondary() && !this.fromNamedBatch(),
  );
  protected readonly modeLocked = this.data.cut !== undefined;

  /** Which batches the totals would empty, and what stops them being saved. */
  protected readonly draw = signal<CutFabricDraw[]>([]);
  protected readonly drawError = signal<string | null>(null);

  protected readonly drawnConsumed = computed(() =>
    this.draw().reduce((sum, row) => sum + row.weightConsumed, 0),
  );

  /** Only SECONDARY and DERBY cuts name a parent. */
  protected readonly needsParent = computed(() => this.selectedType() !== 'MAIN');

  /**
   * A secondary or derby run is cut out of a main run, and takes that run's model
   * and marker with it — so neither is asked for here. Typing them again is
   * transcription, and getting them wrong is how a child run ends up counted
   * against a model its parent never cut.
   */
  protected readonly inheritsModel = this.needsParent;

  /** The main cut a child run inherits from, once one is chosen. */
  protected readonly parentCut = computed(() =>
    this.parentOptions().find((cut) => cut.id === this.chosenParentId()),
  );

  /** The model that inheritance settles on, for the hint that replaces the form. */
  protected readonly inheritedModelNumber = computed(
    () => this.parentCut()?.primaryModelNumber ?? this.data.cut?.primaryModelNumber ?? null,
  );

  /** A cut cannot be its own parent, and only MAIN cuts are eligible. */
  protected readonly parentOptions = computed(() =>
    this.production.mainCuts.value().content.filter((cut) => cut.id !== this.data.cut?.id),
  );

  protected onTypeChange(): void {
    if (!this.needsParent()) {
      this.form.controls.parentMainCutId.setValue(null);
    }
    // Derby is written up from a purchase, by colour: a colour, a date and a
    // weight. That is the totals form, so a derby run opens on it.
    if (this.isDerby()) {
      this.form.controls.entryMode.setValue('SUMMARY');
    } else if (!this.needsParent()) {
      this.form.controls.fromNamedBatch.setValue(false);
    }
    this.clearBatchChoice();
  }

  private readonly chosenParentId = toSignal(this.form.controls.parentMainCutId.valueChanges, {
    initialValue: this.form.controls.parentMainCutId.value,
  });

  /**
   * Fills a secondary or derby run in from the main cut it hangs off.
   *
   * <p>It is the same run continued: the same fabric, usually at the same branch.
   * Typing that again is transcription, and getting it wrong is how a child ends
   * up laid out in a fabric its parent never used. The model and the marker are
   * not filled in but inherited outright — see {@link inheritsModel}.
   *
   * <p>Only ever fills what is still blank, so a value already typed is never
   * overwritten, and everything stays editable — a secondary run is sometimes
   * sewn somewhere else.
   */
  protected onParentChange(): void {
    const parent = this.parentCut();
    if (!parent) {
      return;
    }

    const patch: Record<string, unknown> = {};
    if (this.form.controls.branchId.value === null) {
      patch['branchId'] = parent.branchId;
    }
    if (this.form.controls.fabricTypeName.value.trim() === '') {
      patch['fabricTypeName'] = parent.fabricTypeNameAr ?? '';
    }
    if (this.form.controls.cutLength.value === null) {
      patch['cutLength'] = parent.cutLength;
    }
    this.form.patchValue(patch);
  }

  // --- the batch this run was cut from -------------------------------------

  /** Batches of the right pool that still hold stock, for the two pickers. */
  protected readonly batches = signal<FabricIntake[]>([]);

  private readonly loadBatches = effect(() => {
    const type = this.matchedType();
    if (!this.namesBatch() || !type) {
      this.batches.set([]);
      return;
    }
    this.fabrics
      .batchesInStock(type.id, this.isDerby())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rows) => this.batches.set(rows));
  });

  private readonly chosenColorId = toSignal(this.form.controls.fabricColorId.valueChanges, {
    initialValue: this.form.controls.fabricColorId.value,
  });

  private readonly chosenBatchId = toSignal(this.form.controls.fabricIntakeId.valueChanges, {
    initialValue: this.form.controls.fabricIntakeId.value,
  });

  protected readonly chosenBatch = computed(() =>
    this.batches().find((batch) => batch.id === this.chosenBatchId()),
  );

  /** Every colour the pool holds, gathered from the batches that hold it. */
  protected readonly batchColors = computed(() => {
    const byId = new Map<number, string>();
    for (const batch of this.batches()) {
      for (const row of batch.colorBreakdown) {
        byId.set(row.colorId, row.colorNameAr);
      }
    }
    return [...byId].map(([id, nameAr]) => ({ id, nameAr }));
  });

  /**
   * The batches to choose a date from: the ones holding the colour, once a
   * colour is picked. A batch whose colours were never written down is always
   * offered — it has none to fail the filter with.
   */
  protected readonly batchOptions = computed(() => {
    const colorId = this.chosenColorId();
    return this.batches().filter(
      (batch) =>
        colorId === null ||
        batch.colorBreakdown.length === 0 ||
        batch.colorBreakdown.some((row) => row.colorId === colorId),
    );
  });

  /** How much of the chosen colour the chosen batch says it holds. */
  protected remainingOfColor(batch: FabricIntake): number | null {
    const colorId = this.chosenColorId();
    const row = batch.colorBreakdown.find((entry) => entry.colorId === colorId);
    return row?.quantity ?? null;
  }

  protected onColorChange(): void {
    const batch = this.chosenBatch();
    const colorId = this.form.controls.fabricColorId.value;
    // The batch on screen may not hold the colour just picked.
    if (
      batch &&
      colorId !== null &&
      batch.colorBreakdown.length > 0 &&
      !batch.colorBreakdown.some((row) => row.colorId === colorId)
    ) {
      this.form.controls.fabricIntakeId.setValue(null);
    }
  }

  private clearBatchChoice(): void {
    this.form.patchValue({ fabricColorId: null, fabricIntakeId: null });
  }

  /** A run that draws from a named batch has to name one. */
  protected readonly missingBatch = computed(
    () => this.namesBatch() && this.chosenBatchId() === null,
  );

  /** And which colour of it, once the batch says which colours it holds. */
  protected readonly missingBatchColor = computed(() => {
    const batch = this.chosenBatch();
    return batch !== undefined && batch.colorBreakdown.length > 0 && this.chosenColorId() === null;
  });

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
    if (this.missingBatch() || this.missingBatchColor()) {
      return true;
    }
    // A child run inherits its models, so there is nothing here to disagree with.
    if (this.inheritsModel()) {
      return false;
    }
    return (
      this.duplicateModel() ||
      this.modelsValue().some(
        (_row, index) => this.duplicateSize(index) || this.sizesWithoutModel(index),
      )
    );
  }

  /**
   * Asks the server which batches these totals would empty.
   *
   * <p>Only the server knows what each batch holds right now, so the preview is
   * a round trip rather than arithmetic here. Debounced because it fires on
   * every keystroke, and errors are kept rather than thrown: "there is not
   * enough of this fabric" belongs on screen while typing, not on submit.
   */
  private watchDraw(): void {
    this.form.valueChanges
      .pipe(debounceTime(400), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        const raw = this.form.getRawValue();
        const type = this.matchedType();
        const names = this.namesBatch();
        const fabricTypeId = this.chosenBatch()?.fabricTypeId ?? type?.id ?? null;
        // Nothing to preview until the run says what it took, and from where: a
        // named batch needs the batch, an inferred draw needs the rolls. A
        // secondary run spent from its main cut takes nothing off the shelf at
        // all — its fabric came out of the main cut's.
        if (
          raw.entryMode !== 'SUMMARY' ||
          !raw.totalWeight ||
          fabricTypeId === null ||
          (names
            ? raw.fabricIntakeId === null
            : raw.cutType === 'SECONDARY' || !type || !raw.totalRolls)
        ) {
          this.draw.set([]);
          this.drawError.set(null);
          return;
        }
        this.production
          .previewCutDraw({
            fabricTypeId,
            cutType: raw.cutType,
            totalWeight: raw.totalWeight,
            wasteWeight: this.isDerby() ? 0 : (raw.wasteWeight ?? 0),
            newRolls: this.isDerby() ? 0 : (raw.totalRolls ?? 0) - (raw.reusedRolls ?? 0),
            ...(names
              ? { fabricIntakeId: raw.fabricIntakeId, fabricColorId: raw.fabricColorId }
              : {}),
          })
          .subscribe({
            next: (rows) => {
              this.draw.set(rows);
              this.drawError.set(null);
            },
            error: (response) => {
              this.draw.set([]);
              this.drawError.set(response?.error?.code ?? 'cut_summary_insufficient_fabric');
            },
          });
      });
  }

  constructor() {
    this.watchDraw();
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
    // A child run's model comes from the main cut it was cut out of, so it is
    // left unsaid here rather than sent and ignored.
    const inherits = raw.cutType !== 'MAIN';
    const derby = raw.cutType === 'DERBY';
    const names =
      raw.entryMode === 'SUMMARY' && (derby || (raw.cutType === 'SECONDARY' && raw.fromNamedBatch));
    const request = {
      fabricTypeId,
      cutNumber: raw.cutNumber.trim(),
      cutType: raw.cutType,
      parentMainCutId: raw.cutType === 'MAIN' ? null : raw.parentMainCutId,
      branchId: raw.branchId!,

      cutDate: toIsoDate(raw.cutDate),
      cutLength: raw.cutLength,
      modelDescription: raw.modelDescription.trim() || null,
      modelNumber: inherits ? null : toWesternDigits(first.modelNumber).trim() || null,
      modelNameAr: inherits ? null : first.modelNameAr.trim() || null,
      modelSewingBranchId: inherits ? null : first.sewingBranchId,
      labelAr: raw.labelAr.trim() || null,
      labelEn: raw.labelEn.trim() || null,
      note: raw.note.trim() || null,
      entryMode: raw.entryMode,
      // Sent only in summary mode; a detailed cut derives all of this from its
      // rolls, and sending both would describe the same fabric twice.
      ...(raw.entryMode === 'SUMMARY'
        ? {
            // A derby run lays out no marker — its ribbing is weighed, not
            // counted — so it has neither rolls nor layers to state.
            totalRolls: derby ? null : raw.totalRolls,
            reusedRolls: derby ? null : (raw.reusedRolls ?? 0),
            totalWeight: raw.totalWeight,
            wasteWeight: derby ? 0 : (raw.wasteWeight ?? 0),
            totalLayers: derby ? null : raw.totalLayers,
            fabricIntakeId: names ? raw.fabricIntakeId : null,
            fabricColorId: names ? raw.fabricColorId : null,
          }
        : {}),
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

    // Nothing to record for a child run: its marker was copied from the main cut.
    const marker = inherits ? [] : this.markerRequests(models);
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
