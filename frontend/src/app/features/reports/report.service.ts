import { httpResource } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

import { API_BASE_URL } from '../../core/http/api.config';
import {
  BranchRollup,
  FabricStock,
  FlaggedRow,
  IntakeRemainingRow,
  OpenRollRow,
  Overview,
} from '../../core/models/api.models';

const EMPTY_OVERVIEW: Overview = {
  plannedTotal: 0,
  totalInPipeline: 0,
  flaggedTotal: 0,
  reconciled: true,
  modelCount: 0,
  stages: [],
  branches: [],
};

/** Read-only rollups. Filters are signals, so changing one refetches its report. */
@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly baseUrl = inject(API_BASE_URL);

  readonly selectedBranchId = signal<number | null>(null);
  readonly flaggedModelId = signal<number | null>(null);
  readonly flaggedBranchId = signal<number | null>(null);

  readonly overview = httpResource<Overview>(() => `${this.baseUrl}/reports/overview`, {
    defaultValue: EMPTY_OVERVIEW,
  });

  readonly branchRollup = httpResource<BranchRollup | undefined>(() => {
    const branchId = this.selectedBranchId();
    return branchId === null ? undefined : `${this.baseUrl}/reports/branches/${branchId}`;
  });

  readonly flagged = httpResource<FlaggedRow[]>(
    () => ({
      url: `${this.baseUrl}/reports/flagged`,
      params: {
        ...(this.flaggedModelId() ? { modelId: this.flaggedModelId()! } : {}),
        ...(this.flaggedBranchId() ? { branchId: this.flaggedBranchId()! } : {}),
      },
    }),
    { defaultValue: [] },
  );

  readonly fabricStock = httpResource<FabricStock[]>(
    () => `${this.baseUrl}/reports/fabric-inventory`,
    {
      defaultValue: [],
    },
  );

  readonly openRolls = httpResource<OpenRollRow[]>(() => `${this.baseUrl}/reports/open-rolls`, {
    defaultValue: [],
  });

  readonly fabricByDate = httpResource<IntakeRemainingRow[]>(
    () => `${this.baseUrl}/reports/fabric-remaining-by-date`,
    { defaultValue: [] },
  );

  reloadAll(): void {
    this.overview.reload();
    this.branchRollup.reload();
    this.flagged.reload();
    this.fabricStock.reload();
    this.fabricByDate.reload();
    this.openRolls.reload();
  }
}
