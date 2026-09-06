import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';

import { API_BASE_URL } from '../../../core/http/api.config';
import {
  Cut,
  CutRoll,
  FabricIntake,
  FabricRoll,
  Page,
  emptyPage,
} from '../../../core/models/api.models';
import { filterByName, findExact } from '../../../shared/lookup-autocomplete/lookup-filter';
import { FabricService } from '../../fabrics/fabric.service';
import { Observable, of, switchMap } from 'rxjs';

import { ProductionService } from '../production.service';
import { NumericFieldDirective } from '../../../shared/numerals/numeric-field.directive';

export interface CutRollDialogData {
  cut: Cut;
  /** Absent when adding a roll. */
  cutRoll?: CutRoll;
}

/**
 * Puts a roll on a cut.
 *
 * <p>Two sources: a fresh roll drawn off a dated batch and weighed here, or an
 * open roll left part-used by an earlier cut. The `done` flag is what closes the
 * roll and moves the batch's roll count — leaving it unticked keeps the roll
 * available, and only its weight moves.
 */
@Component({
  selector: 'app-cut-roll-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    TranslatePipe,
    NumericFieldDirective,
  ],
  templateUrl: './cut-roll-dialog.html',
  styleUrl: '../../fabrics/dialogs/dialog-form.scss',
})
export class CutRollDialog {
  protected readonly dialogRef = inject<MatDialogRef<CutRollDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<CutRollDialogData>(MAT_DIALOG_DATA);
  private readonly production = inject(ProductionService);
  protected readonly fabrics = inject(FabricService);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);
  protected readonly editing = this.data.cutRoll !== undefined;
  protected readonly isDerbyCut = this.data.cut.cutType === 'DERBY';

  /** NEW draws a fresh roll off a batch; OPEN continues a part-used one. */
  protected readonly source = signal<'NEW' | 'OPEN'>(this.editing ? 'OPEN' : 'NEW');

  protected readonly batches = httpResource<Page<FabricIntake>>(
    () => ({
      url: `${this.baseUrl}/intakes`,
      params: {
        inStockOnly: true,
        derbyOnly: this.isDerbyCut,
        ...(this.data.cut.fabricTypeId ? { fabricTypeId: this.data.cut.fabricTypeId } : {}),
        size: 200,
      },
    }),
    { defaultValue: emptyPage<FabricIntake>() },
  );

  protected readonly openRolls = httpResource<FabricRoll[]>(
    () => ({
      url: `${this.baseUrl}/rolls/open`,
      params: {
        derbyOnly: this.isDerbyCut,
        ...(this.data.cut.fabricTypeId ? { fabricTypeId: this.data.cut.fabricTypeId } : {}),
      },
    }),
    { defaultValue: [] },
  );

  protected readonly form = this.formBuilder.nonNullable.group({
    fabricRollId: [this.data.cutRoll?.fabricRollId ?? (null as number | null)],
    fabricIntakeId: [null as number | null],
    colorName: [this.data.cutRoll?.colorNameAr ?? '', Validators.maxLength(128)],
    rollLabel: [this.data.cutRoll?.rollLabel ?? '', Validators.maxLength(64)],
    initialWeight: [null as number | null],
    layers: [
      this.data.cutRoll?.layers ?? (null as number | null),
      [Validators.required, Validators.min(1)],
    ],
    defectWeight: [this.data.cutRoll?.defectWeight ?? 0, Validators.min(0)],
    done: [this.data.cutRoll?.done ?? true],
    weightUsed: [
      this.data.cutRoll?.weightConsumed ?? (null as number | null),
      Validators.min(0.001),
    ],
    note: [this.data.cutRoll?.note ?? '', Validators.maxLength(512)],
  });

  protected readonly doneValue = toSignal(this.form.controls.done.valueChanges, {
    initialValue: this.form.controls.done.value,
  });

  private readonly batchId = toSignal(this.form.controls.fabricIntakeId.valueChanges, {
    initialValue: this.form.controls.fabricIntakeId.value,
  });

  private readonly usedValue = toSignal(this.form.controls.weightUsed.valueChanges, {
    initialValue: this.form.controls.weightUsed.value,
  });

  /** What the roll holds right now — its whole weight, or what an open roll has left. */
  protected readonly availableWeight = computed(() => {
    if (this.editing) {
      return this.data.cutRoll?.weightAtStart ?? null;
    }
    if (this.source() === 'OPEN') {
      const roll = this.openRolls
        .value()
        .find((r) => r.id === this.form.controls.fabricRollId.value);
      return roll?.remainingWeight ?? null;
    }
    return this.form.controls.initialWeight.value;
  });

  /**
   * What this cut will not use, shown live as the used weight is typed.
   *
   * <p>Where it goes depends on the tick box: an open roll keeps it for the next
   * cut, a finished one is binned with it still on board. Finishing a roll is
   * not a claim that the cut used every gram — this is the gram count it did
   * not, and the person entering it sees the figure before saving rather than
   * discovering it afterwards.
   */
  protected readonly leftover = computed(() => {
    const available = this.availableWeight();
    if (available === null) {
      return null;
    }
    const used = this.usedValue();
    if (used === null) {
      // No figure given: finishing the roll means the cut took all of it.
      return this.doneValue() ? 0 : null;
    }
    return Math.round((available - used) * 1000) / 1000;
  });

  /** True when saving would throw fabric away, which the hint colours as a loss. */
  protected readonly binsLeftover = computed(
    () => this.doneValue() === true && (this.leftover() ?? 0) > 0,
  );

  protected readonly selectedBatch = computed(() =>
    this.batches.value().content.find((batch) => batch.id === this.batchId()),
  );

  /** Colours already known for the chosen batch's fabric type. */
  private readonly knownColors = computed(() => {
    const batch = this.selectedBatch();
    return batch
      ? this.fabrics.colorsOfType(batch.fabricTypeId).filter((color) => color.active)
      : [];
  });

  private readonly typedColor = toSignal(this.form.controls.colorName.valueChanges, {
    initialValue: this.form.controls.colorName.value,
  });

  protected readonly colorSuggestions = computed(() =>
    filterByName(this.knownColors(), this.typedColor(), (color) => color.nameAr),
  );

  private readonly matchedColor = computed(() =>
    findExact(this.knownColors(), this.typedColor(), (color) => color.nameAr),
  );

  /** A colour name matching nothing is created under the batch's fabric type. */
  protected readonly creatingColor = computed(
    () => this.typedColor().trim() !== '' && this.matchedColor() === undefined,
  );

  protected batchLabel(batch: FabricIntake): string {
    return `${batch.intakeDate} — ${batch.fabricTypeNameAr} (${batch.remainingRolls} rolls left)`;
  }

  protected rollLabelFor(roll: FabricRoll): string {
    const name = roll.label ?? `#${roll.id}`;
    return `${name} — ${roll.fabricTypeNameAr}${roll.colorNameAr ? ' / ' + roll.colorNameAr : ''} (${roll.remainingWeight} left)`;
  }

  protected setSource(value: 'NEW' | 'OPEN'): void {
    this.source.set(value);
    this.form.controls.fabricRollId.setValue(null);
    this.form.controls.fabricIntakeId.setValue(null);
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const raw = this.form.getRawValue();
    const usingOpenRoll = this.editing || this.source() === 'OPEN';

    if (usingOpenRoll && raw.fabricRollId === null) {
      this.form.controls.fabricRollId.setErrors({ required: true });
      return;
    }
    if (!usingOpenRoll && (raw.fabricIntakeId === null || !raw.initialWeight)) {
      this.form.controls.fabricIntakeId.setErrors({ required: true });
      return;
    }

    this.saving.set(true);
    this.resolveColor(raw.colorName)
      .pipe(
        switchMap((fabricColorId) =>
          this.production.addRoll(this.data.cut.id, {
            fabricRollId: usingOpenRoll ? raw.fabricRollId : null,
            fabricIntakeId: usingOpenRoll ? null : raw.fabricIntakeId,
            fabricColorId,
            rollLabel: raw.rollLabel.trim() || null,
            initialWeight: usingOpenRoll ? null : raw.initialWeight,
            layers: raw.layers!,
            defectWeight: raw.defectWeight ?? 0,
            done: raw.done,
            // Sent even when finishing: whatever it leaves over is the waste.
            weightUsed: raw.weightUsed,
            note: raw.note.trim() || null,
          }),
        ),
      )
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving.set(false),
      });
  }

  /**
   * Colour is optional here — a roll can be recorded before anyone has said what
   * colour it is. A new name is created under the batch's own fabric type.
   */
  private resolveColor(name: string): Observable<number | null> {
    const trimmed = name.trim();
    if (trimmed === '') {
      return of(null);
    }
    const existing = this.matchedColor();
    if (existing) {
      return of(existing.id);
    }
    const batch = this.selectedBatch();
    if (!batch) {
      return of(this.data.cutRoll?.fabricColorId ?? null);
    }
    return this.fabrics
      .addColor(batch.fabricTypeId, { nameAr: trimmed, nameEn: null, active: true })
      .pipe(switchMap((created) => of(created.id)));
  }
}
