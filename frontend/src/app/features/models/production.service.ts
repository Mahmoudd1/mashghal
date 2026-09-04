import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { API_BASE_URL } from '../../core/http/api.config';
import {
  Cut,
  CutModelAllocation,
  CutModelAllocationRequest,
  CutRequest,
  CutModelSizeRequest,
  CutRoll,
  CutRollRequest,
  ModelFabricUsage,
  CutStatus,
  CutType,
  ModelCuts,
  ModelRequest,
  Page,
  ProductionModel,
  emptyPage,
} from '../../core/models/api.models';

export interface CutFilters {
  cutType: CutType | null;
  status: CutStatus | null;
  branchId: number | null;
  modelId: number | null;
  pageIndex: number;
  pageSize: number;
}

const INITIAL_CUT_FILTERS: CutFilters = {
  cutType: null,
  status: null,
  branchId: null,
  modelId: null,
  pageIndex: 0,
  pageSize: 25,
};

/**
 * Models, cuts and the allocations between them.
 *
 * Model planned quantities are computed server-side from allocations, so any
 * allocation change reloads the model list as well as the cut being edited.
 */
@Injectable({ providedIn: 'root' })
export class ProductionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  readonly cutFilters = signal<CutFilters>(INITIAL_CUT_FILTERS);

  /** Set to a cut id to load its detail; null closes the detail panel. */
  readonly selectedCutId = signal<number | null>(null);

  /** Set to a model id to load the cuts feeding it. */
  readonly selectedModelId = signal<number | null>(null);

  readonly models = httpResource<ProductionModel[]>(() => `${this.baseUrl}/models`, {
    defaultValue: [],
  });

  /** Fabric consumed per piece, per model and fabric type. */
  readonly fabricUsage = httpResource<ModelFabricUsage[]>(
    () => `${this.baseUrl}/models/fabric-usage`,
    {
      defaultValue: [],
    },
  );

  readonly cuts = httpResource<Page<Cut>>(
    () => {
      const filters = this.cutFilters();
      return {
        url: `${this.baseUrl}/cuts`,
        params: {
          ...(filters.cutType ? { cutType: filters.cutType } : {}),
          ...(filters.status ? { status: filters.status } : {}),
          ...(filters.branchId ? { branchId: filters.branchId } : {}),
          ...(filters.modelId ? { modelId: filters.modelId } : {}),
          page: filters.pageIndex,
          size: filters.pageSize,
        },
      };
    },
    { defaultValue: emptyPage<Cut>() },
  );

  readonly selectedCut = httpResource<Cut | undefined>(() => {
    const id = this.selectedCutId();
    return id === null ? undefined : `${this.baseUrl}/cuts/${id}`;
  });

  readonly modelCuts = httpResource<ModelCuts | undefined>(() => {
    const id = this.selectedModelId();
    return id === null ? undefined : `${this.baseUrl}/models/${id}/cuts`;
  });

  /** Main cuts only — the parent options for a secondary or derby cut. */
  readonly mainCuts = httpResource<Page<Cut>>(
    () => ({ url: `${this.baseUrl}/cuts`, params: { cutType: 'MAIN', size: 200 } }),
    { defaultValue: emptyPage<Cut>() },
  );

  updateCutFilters(patch: Partial<CutFilters>): void {
    this.cutFilters.update((current) => {
      const next = { ...current, ...patch };
      if (patch.pageIndex === undefined) {
        next.pageIndex = 0;
      }
      return next;
    });
  }

  createModel(request: ModelRequest): Observable<ProductionModel> {
    return this.http
      .post<ProductionModel>(`${this.baseUrl}/models`, request)
      .pipe(tap(() => this.models.reload()));
  }

  updateModel(id: number, request: ModelRequest): Observable<ProductionModel> {
    return this.http
      .put<ProductionModel>(`${this.baseUrl}/models/${id}`, request)
      .pipe(tap(() => this.models.reload()));
  }

  deleteModel(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/models/${id}`)
      .pipe(tap(() => this.models.reload()));
  }

  createCut(request: CutRequest): Observable<Cut> {
    return this.http.post<Cut>(`${this.baseUrl}/cuts`, request).pipe(tap(() => this.reloadCuts()));
  }

  updateCut(id: number, request: CutRequest): Observable<Cut> {
    return this.http
      .put<Cut>(`${this.baseUrl}/cuts/${id}`, request)
      .pipe(tap(() => this.reloadCuts()));
  }

  closeCut(id: number): Observable<Cut> {
    return this.http
      .post<Cut>(`${this.baseUrl}/cuts/${id}/close`, {})
      .pipe(tap(() => this.reloadCuts()));
  }

  reopenCut(id: number): Observable<Cut> {
    return this.http
      .post<Cut>(`${this.baseUrl}/cuts/${id}/reopen`, {})
      .pipe(tap(() => this.reloadCuts()));
  }

  deleteCut(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/cuts/${id}`).pipe(
      tap(() => {
        if (this.selectedCutId() === id) {
          this.selectedCutId.set(null);
        }
        this.reloadAfterAllocationChange();
      }),
    );
  }

  allocateToModel(
    cutId: number,
    request: CutModelAllocationRequest,
  ): Observable<CutModelAllocation> {
    return this.http
      .post<CutModelAllocation>(`${this.baseUrl}/cuts/${cutId}/model-allocations`, request)
      .pipe(tap(() => this.reloadAfterAllocationChange()));
  }

  removeModelAllocation(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/model-allocations/${id}`)
      .pipe(tap(() => this.reloadAfterAllocationChange()));
  }

  addRoll(cutId: number, request: CutRollRequest): Observable<CutRoll> {
    return this.http
      .post<CutRoll>(`${this.baseUrl}/cuts/${cutId}/rolls`, request)
      .pipe(tap(() => this.reloadAfterAllocationChange()));
  }

  removeRoll(cutRollId: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/cut-rolls/${cutRollId}`)
      .pipe(tap(() => this.reloadAfterAllocationChange()));
  }

  /** Sets pieces-per-layer for a model and size; piece counts derive from this. */
  setModelSize(cutId: number, request: CutModelSizeRequest): Observable<Cut> {
    return this.http
      .post<Cut>(`${this.baseUrl}/cuts/${cutId}/sizes`, request)
      .pipe(tap(() => this.reloadAfterAllocationChange()));
  }

  removeModelSize(cutId: number, modelId: number, sizeId: number): Observable<Cut> {
    return this.http
      .delete<Cut>(`${this.baseUrl}/cuts/${cutId}/sizes/${modelId}/${sizeId}`)
      .pipe(tap(() => this.reloadAfterAllocationChange()));
  }

  private reloadCuts(): void {
    this.cuts.reload();
    this.mainCuts.reload();
    if (this.selectedCutId() !== null) {
      this.selectedCut.reload();
    }
  }

  /** Allocations move both the cut totals and every model's derived plan. */
  private reloadAfterAllocationChange(): void {
    this.reloadCuts();
    this.models.reload();
    this.fabricUsage.reload();
    if (this.selectedModelId() !== null) {
      this.modelCuts.reload();
    }
  }
}
