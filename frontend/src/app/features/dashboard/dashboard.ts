import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { StageTotal } from '../../core/models/api.models';
import { ReportService } from '../reports/report.service';

interface StageSegment {
  code: string;
  pieceCount: number;
  /** Percentage of the row's total, for the bar width. */
  share: number;
}

/**
 * Landing page: pipeline totals across every model, then per branch.
 *
 * The stage bars are an ordinal ramp — one hue, light to dark — because the
 * stages are a funnel and the reader should see the order in the colour. Every
 * bar is backed by the same numbers in a table below it, so nothing depends on
 * colour alone.
 */
@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    RouterLink,
    TranslatePipe,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  protected readonly reports = inject(ReportService);

  protected readonly showTable = signal(false);

  protected readonly overview = computed(() => this.reports.overview.value());

  /** Stage order comes from the API (sequenceNo), never from the client. */
  protected readonly stageOrder = computed(() =>
    [...this.overview().stages].sort((a, b) => a.sequenceNo - b.sequenceNo),
  );

  protected readonly overallSegments = computed(() => this.segments(this.overview().stages));

  /** Rolls still on hand across every fabric type and both pools. */
  protected readonly fabricSummary = computed(() => {
    const rows = this.reports.fabricStock.value();
    return {
      poolCount: rows.length,
      rollsInStock: rows.reduce((total, row) => total + row.remainingRolls, 0),
      totalRolls: rows.reduce((total, row) => total + row.totalRolls, 0),
    };
  });

  protected segments(stages: StageTotal[]): StageSegment[] {
    const total = stages.reduce((sum, stage) => sum + stage.pieceCount, 0);
    return [...stages]
      .sort((a, b) => a.sequenceNo - b.sequenceNo)
      .filter((stage) => stage.pieceCount > 0)
      .map((stage) => ({
        code: stage.stageCode,
        pieceCount: stage.pieceCount,
        share: total === 0 ? 0 : (stage.pieceCount / total) * 100,
      }));
  }

  protected stageClass(code: string): string {
    return `stage-${code.toLowerCase()}`;
  }

  /**
   * A segment gets its number printed inside only when it is wide enough to hold
   * it; the rest rely on the tooltip and the table.
   */
  protected fitsLabel(segment: StageSegment): boolean {
    return segment.share >= 12;
  }

  protected reload(): void {
    this.reports.overview.reload();
    this.reports.fabricStock.reload();
  }
}
