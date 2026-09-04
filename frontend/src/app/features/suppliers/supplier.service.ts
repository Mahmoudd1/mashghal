import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { API_BASE_URL } from '../../core/http/api.config';
import { FabricPriceRow, Supplier, SupplierRequest } from '../../core/models/api.models';

/**
 * Suppliers, and what fabric has cost from them.
 *
 * Which fabrics a supplier provides is not stored — it emerges from purchase
 * history, so the price report is the same data viewed two ways.
 */
@Injectable({ providedIn: 'root' })
export class SupplierService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /** Splits the price report by who supplied each batch. */
  readonly bySupplier = signal(false);
  readonly priceFabricTypeId = signal<number | null>(null);

  readonly suppliers = httpResource<Supplier[]>(() => `${this.baseUrl}/suppliers`, {
    defaultValue: [],
  });

  readonly prices = httpResource<FabricPriceRow[]>(
    () => ({
      url: `${this.baseUrl}/fabric-prices`,
      params: {
        bySupplier: this.bySupplier(),
        ...(this.priceFabricTypeId() ? { fabricTypeId: this.priceFabricTypeId()! } : {}),
      },
    }),
    { defaultValue: [] },
  );

  create(request: SupplierRequest): Observable<Supplier> {
    return this.http
      .post<Supplier>(`${this.baseUrl}/suppliers`, request)
      .pipe(tap(() => this.reload()));
  }

  update(id: number, request: SupplierRequest): Observable<Supplier> {
    return this.http
      .put<Supplier>(`${this.baseUrl}/suppliers/${id}`, request)
      .pipe(tap(() => this.reload()));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/suppliers/${id}`).pipe(tap(() => this.reload()));
  }

  private reload(): void {
    this.suppliers.reload();
    this.prices.reload();
  }
}
