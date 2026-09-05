import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { LocalizedNamePipe } from '../../core/i18n/localized-name.pipe';
import { BranchPipeline, ModelPipeline } from '../../core/models/api.models';
import { ReferenceService } from '../../core/models/reference.service';
import { clientPage } from '../../shared/paging/client-page';
import {
  PipelineAction,
  PipelineActionDialog,
  PipelineActionDialogData,
} from './dialogs/pipeline-action-dialog';
import { PipelineService } from './pipeline.service';

/**
 * Stage counts per model per branch, with the actions that move pieces.
 *
 * Each branch row shows its reconciliation state: pieces counted across all
 * stages against pieces allocated from cuts.
 */
@Component({
  selector: 'app-pipeline-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTooltipModule,
    TranslatePipe,
    LocalizedNamePipe,
  ],
  templateUrl: './pipeline-page.html',
  styleUrl: './pipeline-page.scss',
})
export class PipelinePage {
  protected readonly pipeline = inject(PipelineService);
  protected readonly reference = inject(ReferenceService);
  private readonly dialog = inject(MatDialog);

  /** Null shows every branch; a branch id narrows each model to that branch. */
  protected readonly branchFilter = signal<number | null>(null);

  /** Matches on model number or Arabic name, so a floor supervisor can jump to one model. */
  protected readonly search = signal('');

  protected readonly models = computed(() => {
    const branchId = this.branchFilter();
    const term = this.search().trim().toLowerCase();
    let all = this.pipeline.allModels.value();

    if (term) {
      all = all.filter(
        (model) =>
          model.modelNumber.toLowerCase().includes(term) ||
          model.modelNameAr.toLowerCase().includes(term),
      );
    }
    if (branchId === null) {
      return all;
    }
    return all
      .map((model) => ({
        ...model,
        branches: model.branches.filter((row) => row.branchId === branchId),
      }))
      .filter((model) => model.branches.length > 0);
  });

  // Every card carries a full set of stage bars and action buttons, so the page
  // is paged even though the data arrives in one request.
  protected readonly page = clientPage(this.models, 10);

  /** Share of a branch's planned pieces sitting in a given stage, for the bar. */
  protected stageShare(branch: BranchPipeline, pieceCount: number): number {
    return branch.totalInPipeline === 0 ? 0 : (pieceCount / branch.totalInPipeline) * 100;
  }

  protected act(action: PipelineAction, model: ModelPipeline, branch: BranchPipeline): void {
    this.dialog.open<PipelineActionDialog, PipelineActionDialogData, boolean>(
      PipelineActionDialog,
      {
        data: { action, model, branch },
      },
    );
  }
}
