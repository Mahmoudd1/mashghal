import { InjectionToken } from '@angular/core';

/**
 * Base path for every API call. Relative on purpose: `ng serve` proxies `/api`
 * to the Spring Boot app (see proxy.conf.json) and in production the API is
 * served from the same origin.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => '/api',
});
