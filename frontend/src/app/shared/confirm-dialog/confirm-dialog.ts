import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { TranslatePipe } from '@ngx-translate/core';

export interface ConfirmDialogData {
  /** Translation key for the dialog title. */
  titleKey: string;
  /** Translation key for the explanatory line. */
  messageKey: string;
  /** The thing being acted on, shown verbatim (user-entered Arabic). */
  subject?: string;
}

@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatDialogModule, TranslatePipe],
  template: `
    <h2 mat-dialog-title>{{ data.titleKey | translate }}</h2>
    <mat-dialog-content>
      @if (data.subject) {
        <p class="confirm-subject">{{ data.subject }}</p>
      }
      <p>{{ data.messageKey | translate }}</p>
      <p class="confirm-warning">{{ 'common.deleteWarning' | translate }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close(false)">{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button class="confirm-delete" (click)="dialogRef.close(true)">
        {{ 'common.delete' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .confirm-subject {
      margin: 0 0 0.5rem;
      font-weight: 600;
    }

    .confirm-warning {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }

    .confirm-delete {
      --mdc-filled-button-container-color: var(--mat-sys-error);
      --mdc-filled-button-label-text-color: var(--mat-sys-on-error);
    }
  `,
})
export class ConfirmDialog {
  protected readonly dialogRef = inject<MatDialogRef<ConfirmDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
