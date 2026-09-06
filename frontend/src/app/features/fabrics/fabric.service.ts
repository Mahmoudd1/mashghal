import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { API_BASE_URL } from '../../core/http/api.config';
import {
  Derby,
  DerbyDefaults,
  DerbyOnPurchaseRequest,
  DerbyPurchaseRequest,
  FabricColor,
  FabricColorRequest,
  FabricIntake,
  FabricIntakeColorRequest,
  FabricIntakeRequest,
  FabricPool,
  FabricStock,
  FabricType,
  FabricTypeRequest,
  IntakeRemainingRow,
  RemainingGrouping,
  RemainingRow,
  Page,
  emptyPage,
} from '../../core/models/api.models';

export interface IntakeFilters {
  fabricTypeId: number | null;
  /** null shows both pools. */
  pool: FabricPool | null;
  inStockOnly: boolean;
  pageIndex: number;
  pageSize: number;
}

const INITIAL_FILTERS: IntakeFilters = {
  fabricTypeId: null,
  pool: null,
  inStockOnly: false,
  pageIndex: 0,
  pageSize: 25,
};

/**
 * Fabric master data and stock.
 *
 * Stock lives in dated intake batches, not individual rolls, and each fabric type
 * has two independent pools: its regular stock and an optional derby.
 */
@Injectable({ providedIn: 'root' })
export class FabricService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  readonly filters = signal<IntakeFilters>(INITIAL_FILTERS);

  readonly types = httpResource<FabricType[]>(() => `${this.baseUrl}/fabric-types`, {
    defaultValue: [],
  });

  readonly stock = httpResource<FabricStock[]>(() => `${this.baseUrl}/intakes/stock`, {
    defaultValue: [],
  });

  /** How remaining stock is broken down: overall, by date, or by supplier. */
  readonly remainingGrouping = signal<RemainingGrouping>('TOTAL');
  readonly remainingFabricTypeId = signal<number | null>(null);

  readonly remaining = httpResource<RemainingRow[]>(
    () => ({
      url: `${this.baseUrl}/intakes/remaining`,
      params: {
        groupBy: this.remainingGrouping(),
        ...(this.remainingFabricTypeId() ? { fabricTypeId: this.remainingFabricTypeId()! } : {}),
      },
    }),
    { defaultValue: [] },
  );

  readonly remainingByDate = httpResource<IntakeRemainingRow[]>(
    () => `${this.baseUrl}/intakes/remaining-by-date`,
    { defaultValue: [] },
  );

  readonly intakes = httpResource<Page<FabricIntake>>(
    () => {
      const filters = this.filters();
      return {
        url: `${this.baseUrl}/intakes`,
        params: {
          ...(filters.fabricTypeId ? { fabricTypeId: filters.fabricTypeId } : {}),
          ...(filters.pool ? { derbyOnly: filters.pool === 'DERBY' } : {}),
          inStockOnly: filters.inStockOnly,
          page: filters.pageIndex,
          size: filters.pageSize,
        },
      };
    },
    { defaultValue: emptyPage<FabricIntake>() },
  );

  /** Colours of the type selected in the filter, for the colour dropdown. */
  readonly colorsOfFilteredType = computed(() => {
    const typeId = this.filters().fabricTypeId;
    return typeId === null
      ? []
      : (this.types.value().find((type) => type.id === typeId)?.colors ?? []);
  });

  colorsOfType(fabricTypeId: number): FabricColor[] {
    return this.types.value().find((type) => type.id === fabricTypeId)?.colors ?? [];
  }

  updateFilters(patch: Partial<IntakeFilters>): void {
    this.filters.update((current) => {
      const next = { ...current, ...patch };
      if (patch.pageIndex === undefined) {
        next.pageIndex = 0;
      }
      return next;
    });
  }

  // --- master data ---------------------------------------------------------

  createType(request: FabricTypeRequest): Observable<FabricType> {
    return this.http
      .post<FabricType>(`${this.baseUrl}/fabric-types`, request)
      .pipe(tap(() => this.reloadAll()));
  }

  updateType(id: number, request: FabricTypeRequest): Observable<FabricType> {
    return this.http
      .put<FabricType>(`${this.baseUrl}/fabric-types/${id}`, request)
      .pipe(tap(() => this.reloadAll()));
  }

  deleteType(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/fabric-types/${id}`)
      .pipe(tap(() => this.reloadAll()));
  }

  addColor(fabricTypeId: number, request: FabricColorRequest): Observable<FabricColor> {
    return this.http
      .post<FabricColor>(`${this.baseUrl}/fabric-types/${fabricTypeId}/colors`, request)
      .pipe(tap(() => this.reloadAll()));
  }

  updateColor(id: number, request: FabricColorRequest): Observable<FabricColor> {
    return this.http
      .put<FabricColor>(`${this.baseUrl}/fabric-colors/${id}`, request)
      .pipe(tap(() => this.reloadAll()));
  }

  deleteColor(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/fabric-colors/${id}`)
      .pipe(tap(() => this.reloadAll()));
  }

  // --- derby ---------------------------------------------------------------

  /** Records the derby bought with an existing fabric purchase. */
  addDerbyToPurchase(intakeId: number, request: DerbyOnPurchaseRequest): Observable<FabricIntake> {
    return this.http
      .post<FabricIntake>(`${this.baseUrl}/intakes/${intakeId}/derby`, request)
      .pipe(tap(() => this.reloadAll()));
  }

  /** Records a derby bought on its own, against the fabric type. */
  recordDerbyPurchase(
    fabricTypeId: number,
    request: DerbyPurchaseRequest,
  ): Observable<FabricIntake> {
    return this.http
      .post<FabricIntake>(`${this.baseUrl}/fabric-types/${fabricTypeId}/derby-purchases`, request)
      .pipe(tap(() => this.reloadAll()));
  }

  deleteDerby(derbyId: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/derbies/${derbyId}`)
      .pipe(tap(() => this.reloadAll()));
  }

  derbyFor(fabricTypeId: number): Observable<Derby> {
    return this.http.get<Derby>(`${this.baseUrl}/fabric-types/${fabricTypeId}/derby`);
  }

  /** Supplier and price a new derby would take from the fabric's last purchase. */
  derbyDefaults(fabricTypeId: number): Observable<DerbyDefaults> {
    return this.http.get<DerbyDefaults>(
      `${this.baseUrl}/fabric-types/${fabricTypeId}/derby-defaults`,
    );
  }

  // --- intake --------------------------------------------------------------

  createIntake(request: FabricIntakeRequest): Observable<FabricIntake> {
    return this.http
      .post<FabricIntake>(`${this.baseUrl}/intakes`, request)
      .pipe(tap(() => this.reloadStock()));
  }

  updateIntake(id: number, request: FabricIntakeRequest): Observable<FabricIntake> {
    return this.http
      .put<FabricIntake>(`${this.baseUrl}/intakes/${id}`, request)
      .pipe(tap(() => this.reloadStock()));
  }

  deleteIntake(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/intakes/${id}`)
      .pipe(tap(() => this.reloadStock()));
  }

  setColorBreakdown(intakeId: number, request: FabricIntakeColorRequest): Observable<FabricIntake> {
    return this.http
      .post<FabricIntake>(`${this.baseUrl}/intakes/${intakeId}/colors`, request)
      .pipe(tap(() => this.reloadStock()));
  }

  removeColorBreakdown(intakeId: number, colorId: number): Observable<FabricIntake> {
    return this.http
      .delete<FabricIntake>(`${this.baseUrl}/intakes/${intakeId}/colors/${colorId}`)
      .pipe(tap(() => this.reloadStock()));
  }

  private reloadStock(): void {
    this.intakes.reload();
    this.stock.reload();
    this.remainingByDate.reload();
    this.remaining.reload();
  }

  private reloadAll(): void {
    this.types.reload();
    this.reloadStock();
  }
}
