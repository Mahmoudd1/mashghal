import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { API_BASE_URL } from '../../core/http/api.config';
import {
  FlagRequest,
  ModelPipeline,
  ReceiveRequest,
  SellRequest,
  StageMoveRequest,
} from '../../core/models/api.models';

/**
 * Pipeline counts and the actions that move pieces.
 *
 * Every action returns the model's whole pipeline, so the view updates from the
 * server's own numbers rather than guessing at the arithmetic locally.
 */
@Injectable({ providedIn: 'root' })
export class PipelineService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  readonly allModels = httpResource<ModelPipeline[]>(() => `${this.baseUrl}/pipeline/models`, {
    defaultValue: [],
  });

  receive(request: ReceiveRequest): Observable<ModelPipeline> {
    return this.post('receive', request);
  }

  sell(request: SellRequest): Observable<ModelPipeline> {
    return this.post('sell', request);
  }

  move(request: StageMoveRequest): Observable<ModelPipeline> {
    return this.post('move', request);
  }

  flag(request: FlagRequest): Observable<ModelPipeline> {
    return this.post('flag', request);
  }

  unflag(request: FlagRequest): Observable<ModelPipeline> {
    return this.post('unflag', request);
  }

  private post<T>(action: string, request: T): Observable<ModelPipeline> {
    return this.http
      .post<ModelPipeline>(`${this.baseUrl}/pipeline/${action}`, request)
      .pipe(tap(() => this.allModels.reload()));
  }
}
