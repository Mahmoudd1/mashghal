import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { API_BASE_URL } from '../http/api.config';
import { UserRole } from '../models/api.models';
import { localStore } from '../storage/local-store';
import { AuthenticatedUser, LoginRequest, LoginResponse } from './auth.models';

const TOKEN_KEY = 'apparel.token';
const USER_KEY = 'apparel.user';
const EXPIRY_KEY = 'apparel.tokenExpiresAt';

/**
 * Holds the signed-in session.
 *
 * A stored token is only restored if it has not already expired, so a stale tab
 * lands on the login page instead of firing doomed requests at the API.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  private readonly tokenSignal = signal<string | null>(restoreToken());
  private readonly userSignal = signal<AuthenticatedUser | null>(
    restoreToken() === null ? null : localStore.getJson<AuthenticatedUser>(USER_KEY),
  );

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);
  /** OWNER is a superset of ADMIN, so it satisfies every admin-gated control. */
  readonly isAdmin = computed(() => {
    const role = this.userSignal()?.role;
    return role === 'ADMIN' || role === 'OWNER';
  });

  /** Only the owner sees prices, costs and supplier price comparisons. */
  readonly isOwner = computed(() => this.userSignal()?.role === 'OWNER');

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.baseUrl}/auth/login`, credentials)
      .pipe(tap((response) => this.acceptSession(response)));
  }

  logout(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    localStore.remove(TOKEN_KEY);
    localStore.remove(USER_KEY);
    localStore.remove(EXPIRY_KEY);
  }

  hasRole(role: UserRole): boolean {
    return this.userSignal()?.role === role;
  }

  private acceptSession(response: LoginResponse): void {
    this.tokenSignal.set(response.token);
    this.userSignal.set(response.user);
    localStore.set(TOKEN_KEY, response.token);
    localStore.set(USER_KEY, JSON.stringify(response.user));
    localStore.set(EXPIRY_KEY, response.expiresAt);
  }
}

function restoreToken(): string | null {
  const token = localStore.get(TOKEN_KEY);
  if (token === null) {
    return null;
  }

  const expiresAt = localStore.get(EXPIRY_KEY);
  if (expiresAt !== null && Date.parse(expiresAt) <= Date.now()) {
    localStore.remove(TOKEN_KEY);
    localStore.remove(USER_KEY);
    localStore.remove(EXPIRY_KEY);
    return null;
  }
  return token;
}
