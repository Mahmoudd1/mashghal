import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../../core/auth/auth.service';
import { LanguageToggle } from '../../layout/language-toggle/language-toggle';

/**
 * Phase 1 skeleton: the form and submit path are real, but the backend
 * `/api/auth/login` endpoint it posts to lands in phase 6.
 */
@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    TranslatePipe,
    LanguageToggle,
  ],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly submitting = signal(false);
  /** Translation key for the message under the form, or null while it has none. */
  protected readonly errorKey = signal<string | null>(null);

  protected readonly form = this.formBuilder.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.errorKey.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        const redirectTo = this.route.snapshot.queryParamMap.get('redirectTo') ?? '/dashboard';
        void this.router.navigateByUrl(redirectTo);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorKey.set(errorKeyFor(error.status));
      },
    });
  }
}

/**
 * Only a 401 means the credentials were wrong. Everything else is the API being
 * unreachable or broken, and saying "incorrect password" there sends people off
 * retyping a password that was right all along.
 */
function errorKeyFor(status: number): string {
  if (status === 401) {
    return 'auth.invalidCredentials';
  }
  // 0 is a failed connection; a dev-server proxy with no backend behind it
  // answers 502, and a restarting one 503/504.
  if (status === 0 || status === 502 || status === 503 || status === 504) {
    return 'errors.network';
  }
  return 'errors.generic';
}
