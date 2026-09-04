import { httpResource } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

import { API_BASE_URL } from '../http/api.config';
import { GarmentSize, ModelSizeCategoryRow, SizeCategory } from './api.models';

/**
 * Size categories and their sizes.
 *
 * Small, rarely-changing lookup data needed by the cut marker and by category
 * reporting, so it is loaded once and shared.
 */
@Injectable({ providedIn: 'root' })
export class SizeService {
  private readonly baseUrl = inject(API_BASE_URL);

  /** Null shows models across every category. */
  readonly categoryFilter = signal<number | null>(null);

  readonly categories = httpResource<SizeCategory[]>(() => `${this.baseUrl}/size-categories`, {
    defaultValue: [],
  });

  readonly sizes = httpResource<GarmentSize[]>(() => `${this.baseUrl}/sizes`, { defaultValue: [] });

  readonly modelsByCategory = httpResource<ModelSizeCategoryRow[]>(
    () => {
      const categoryId = this.categoryFilter();
      return {
        url: `${this.baseUrl}/size-categories/models`,
        ...(categoryId === null ? {} : { params: { categoryId } }),
      };
    },
    { defaultValue: [] },
  );
}
