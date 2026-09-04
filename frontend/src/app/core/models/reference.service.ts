import { httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API_BASE_URL } from '../http/api.config';
import { Branch, PipelineStage } from './api.models';

/**
 * Lookup data (branches, pipeline stages) loaded once and shared app-wide.
 * Both are small, rarely change, and are needed by nearly every screen.
 */
@Injectable({ providedIn: 'root' })
export class ReferenceService {
  private readonly baseUrl = inject(API_BASE_URL);

  readonly branches = httpResource<Branch[]>(() => `${this.baseUrl}/reference/branches`, {
    defaultValue: [],
  });

  readonly stages = httpResource<PipelineStage[]>(() => `${this.baseUrl}/reference/stages`, {
    defaultValue: [],
  });
}
