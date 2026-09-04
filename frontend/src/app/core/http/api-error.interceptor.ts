import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { ApiError } from '../models/api.models';

/**
 * Turns the backend's ApiError body into a translated toast, and ends the
 * session on a 401 so an expired token cannot leave the UI half-working.
 * Errors are rethrown so components can still react.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const snackBar = inject(MatSnackBar);
  const translate = inject(TranslateService);
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !request.url.includes('/auth/login')) {
        auth.logout();
        void router.navigate(['/login']);
      } else if (error.status !== 401) {
        snackBar.open(messageFor(error, translate), translate.instant('common.close'), {
          duration: 6000,
        });
      }
      return throwError(() => error);
    }),
  );
};

function messageFor(error: HttpErrorResponse, translate: TranslateService): string {
  if (error.status === 0) {
    return translate.instant('errors.network');
  }

  const body = error.error as ApiError | null;
  if (body?.code) {
    const key = `errors.${body.code}`;
    const translated = translate.instant(key);
    if (translated !== key) {
      return translated;
    }
    return body.message ?? translate.instant('errors.generic');
  }
  return translate.instant('errors.generic');
}
