import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { AuthenticatedUser } from '../../core/auth/auth.models';
import { API_BASE_URL } from '../../core/http/api.config';
import { UserRequest } from '../../core/models/api.models';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  readonly users = httpResource<AuthenticatedUser[]>(() => `${this.baseUrl}/users`, {
    defaultValue: [],
  });

  create(request: UserRequest): Observable<AuthenticatedUser> {
    return this.http
      .post<AuthenticatedUser>(`${this.baseUrl}/users`, request)
      .pipe(tap(() => this.users.reload()));
  }

  update(id: number, request: UserRequest): Observable<AuthenticatedUser> {
    return this.http
      .put<AuthenticatedUser>(`${this.baseUrl}/users/${id}`, request)
      .pipe(tap(() => this.users.reload()));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/users/${id}`)
      .pipe(tap(() => this.users.reload()));
  }
}
