import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';

import { BranchPipeline, ModelPipeline, StageCode } from '../../../core/models/api.models';
import { toIsoDate } from '../../../shared/date-utils';
import { PipelineService } from '../pipeline.service';

export type PipelineAction = 'receive' | 'sell' | 'move' | 'flag' | 'unflag';

export interface PipelineActionDialogData {
  action: PipelineAction;
  model: ModelPipeline;
  branch: BranchPipeline;
}

/**
 * One form for all five pipeline actions. They share the same shape — a model, a
 * branch, a quantity and a date — and differ only in which extra fields show and
 * what the quantity is capped at.
 */
@Component({
  selector: 'app-pipeline-action-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    TranslatePipe,
  ],
  templateUrl: './pipeline-action-dialog.html',
  styleUrl: '../../fabrics/dialogs/dialog-form.scss',
})
export class PipelineActionDialog {
  protected readonly dialogRef = inject<MatDialogRef<PipelineActionDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<PipelineActionDialogData>(MAT_DIALOG_DATA);
  private readonly pipeline = inject(PipelineService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);
  protected readonly today = new Date();

  protected readonly isMove = this.data.action === 'move';
  protected readonly isFlagging = this.data.action === 'flag' || this.data.action === 'unflag';
  protected readonly titleKey = `pipeline.${this.data.action}`;

  /** Stages that currently hold pieces — the only sensible sources for a move. */
  protected readonly sourceStages = this.data.branch.stages.filter((stage) => stage.pieceCount > 0);
  protected readonly allStages = this.data.branch.stages;

  /** Defects are only recorded from RECEIVED onwards. */
  protected readonly flaggableStages = this.data.branch.stages.filter(
    (stage) => stage.sequenceNo >= 300,
  );

  protected readonly form = this.formBuilder.nonNullable.group({
    quantity: [null as number | null, [Validators.required, Validators.min(1)]],
    fromStageCode: [null as StageCode | null],
    toStageCode: [null as StageCode | null],
    stageCode: ['RECEIVED' as StageCode],
    reason: ['', Validators.maxLength(512)],
    note: ['', Validators.maxLength(512)],
    date: [new Date(), Validators.required],
  });

  private readonly stageCode = toSignal(this.form.controls.stageCode.valueChanges, {
    initialValue: this.form.controls.stageCode.value,
  });

  private readonly fromStageCode = toSignal(this.form.controls.fromStageCode.valueChanges, {
    initialValue: this.form.controls.fromStageCode.value,
  });

  /** The largest quantity this action can legally take, shown as a hint. */
  protected readonly maxQuantity = computed(() => {
    const stages = this.data.branch.stages;
    const received = stages.find((stage) => stage.stageCode === 'RECEIVED');

    switch (this.data.action) {
      case 'receive': {
        // Anything still before RECEIVED can be received.
        return stages
          .filter((stage) => stage.sequenceNo < 300)
          .reduce((total, stage) => total + stage.pieceCount, 0);
      }
      case 'sell':
        return received ? received.pieceCount - received.flaggedCount : 0;
      case 'move': {
        const from = stages.find((stage) => stage.stageCode === this.fromStageCode());
        return from?.pieceCount ?? 0;
      }
      case 'flag': {
        const stage = stages.find((s) => s.stageCode === this.stageCode());
        return stage ? stage.pieceCount - stage.flaggedCount : 0;
      }
      case 'unflag': {
        const stage = stages.find((s) => s.stageCode === this.stageCode());
        return stage?.flaggedCount ?? 0;
      }
    }
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    const raw = this.form.getRawValue();
    const date = toIsoDate(raw.date);
    const base = { modelId: this.data.model.modelId, branchId: this.data.branch.branchId };
    const quantity = raw.quantity!;

    let call;
    switch (this.data.action) {
      case 'receive':
        call = this.pipeline.receive({
          ...base,
          quantity,
          receivedDate: date,
          note: raw.note.trim() || null,
        });
        break;
      case 'sell':
        call = this.pipeline.sell({
          ...base,
          quantity,
          soldDate: date,
          note: raw.note.trim() || null,
        });
        break;
      case 'move':
        if (!raw.fromStageCode || !raw.toStageCode) {
          return;
        }
        call = this.pipeline.move({
          ...base,
          quantity,
          fromStageCode: raw.fromStageCode,
          toStageCode: raw.toStageCode,
          movementDate: date,
          note: raw.note.trim() || null,
        });
        break;
      case 'flag':
      case 'unflag': {
        const request = {
          ...base,
          quantity,
          stageCode: raw.stageCode,
          reason: raw.reason.trim() || null,
          eventDate: date,
        };
        call =
          this.data.action === 'flag' ? this.pipeline.flag(request) : this.pipeline.unflag(request);
        break;
      }
    }

    this.saving.set(true);
    call.subscribe({
      next: () => this.dialogRef.close(true),
      error: () => this.saving.set(false),
    });
  }
}
