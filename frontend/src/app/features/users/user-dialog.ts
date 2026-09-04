import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthenticatedUser } from '../../core/auth/auth.models';
import { UserRole } from '../../core/models/api.models';
import { UserService } from './user.service';

export interface UserDialogData {
  /** Absent when creating. */
  user?: AuthenticatedUser;
}

@Component({
  selector: 'app-user-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslatePipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ (data.user ? 'user.edit' : 'user.add') | translate }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'user.username' | translate }}</mat-label>
          <input matInput formControlName="username" dir="ltr" autocomplete="off" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'user.displayName' | translate }}</mat-label>
          <input matInput formControlName="displayName" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'user.password' | translate }}</mat-label>
          <input
            matInput
            type="password"
            formControlName="password"
            dir="ltr"
            autocomplete="new-password"
          />
          @if (data.user) {
            <mat-hint>{{ 'user.passwordHint' | translate }}</mat-hint>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'user.role' | translate }}</mat-label>
          <mat-select formControlName="role">
            @for (role of roles; track role) {
              <mat-option [value]="role">{{ 'role.' + role | translate }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-checkbox formControlName="enabled">{{ 'common.active' | translate }}</mat-checkbox>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button [disabled]="form.invalid || saving()" (click)="save()">
        {{ 'common.save' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styleUrl: '../fabrics/dialogs/dialog-form.scss',
})
export class UserDialog {
  protected readonly dialogRef = inject<MatDialogRef<UserDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<UserDialogData>(MAT_DIALOG_DATA);
  private readonly users = inject(UserService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly roles: UserRole[] = ['ADMIN', 'DATA_ENTRY'];
  protected readonly saving = signal(false);

  protected readonly form = this.formBuilder.nonNullable.group({
    username: [this.data.user?.username ?? '', [Validators.required, Validators.maxLength(64)]],
    displayName: [
      this.data.user?.displayName ?? '',
      [Validators.required, Validators.maxLength(128)],
    ],
    // Required only when creating; blank on an edit means "keep the current one".
    password: [
      '',
      this.data.user ? [Validators.minLength(8)] : [Validators.required, Validators.minLength(8)],
    ],
    role: [this.data.user?.role ?? ('DATA_ENTRY' as UserRole), Validators.required],
    enabled: [true],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);

    const raw = this.form.getRawValue();
    const request = {
      username: raw.username.trim(),
      password: raw.password.trim() || null,
      displayName: raw.displayName.trim(),
      role: raw.role,
      enabled: raw.enabled,
    };

    const call = this.data.user
      ? this.users.update(this.data.user.id, request)
      : this.users.create(request);
    call.subscribe({
      next: () => this.dialogRef.close(true),
      error: () => this.saving.set(false),
    });
  }
}
