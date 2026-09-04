import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthenticatedUser } from '../../core/auth/auth.models';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';
import { UserDialog, UserDialogData } from './user-dialog';
import { UserService } from './user.service';

/** Admin-only. The route's adminGuard keeps data-entry users out. */
@Component({
  selector: 'app-users-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    TranslatePipe,
  ],
  template: `
    <h1 class="page-title">{{ 'nav.users' | translate }}</h1>

    <div class="toolbar">
      <button mat-flat-button (click)="addUser()">
        <mat-icon>person_add</mat-icon>
        <span>{{ 'user.add' | translate }}</span>
      </button>
    </div>

    @if (users.users.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    <div class="table-scroll">
      <table mat-table [dataSource]="users.users.value()">
        <ng-container matColumnDef="username">
          <th mat-header-cell *matHeaderCellDef>{{ 'user.username' | translate }}</th>
          <td mat-cell *matCellDef="let user" class="ltr">{{ user.username }}</td>
        </ng-container>

        <ng-container matColumnDef="displayName">
          <th mat-header-cell *matHeaderCellDef>{{ 'user.displayName' | translate }}</th>
          <td mat-cell *matCellDef="let user">{{ user.displayName }}</td>
        </ng-container>

        <ng-container matColumnDef="role">
          <th mat-header-cell *matHeaderCellDef>{{ 'user.role' | translate }}</th>
          <td mat-cell *matCellDef="let user">{{ 'role.' + user.role | translate }}</td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef class="row-actions">
            {{ 'common.actions' | translate }}
          </th>
          <td mat-cell *matCellDef="let user" class="row-actions">
            <button mat-icon-button [matTooltip]="'user.edit' | translate" (click)="editUser(user)">
              <mat-icon>edit</mat-icon>
            </button>
            <button
              mat-icon-button
              [matTooltip]="'user.delete' | translate"
              (click)="deleteUser(user)"
            >
              <mat-icon>delete</mat-icon>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `,
  styles: `
    .toolbar {
      padding-block: 1rem;
    }

    table {
      width: 100%;
    }

    .ltr {
      direction: ltr;
      text-align: start;
    }

    .row-actions {
      white-space: nowrap;
      text-align: end;
    }
  `,
})
export class UsersPage {
  protected readonly users = inject(UserService);
  private readonly dialog = inject(MatDialog);

  protected readonly columns = ['username', 'displayName', 'role', 'actions'];

  protected addUser(): void {
    this.dialog.open<UserDialog, UserDialogData, boolean>(UserDialog, { data: {} });
  }

  protected editUser(user: AuthenticatedUser): void {
    this.dialog.open<UserDialog, UserDialogData, boolean>(UserDialog, { data: { user } });
  }

  protected deleteUser(user: AuthenticatedUser): void {
    const data: ConfirmDialogData = {
      titleKey: 'user.delete',
      messageKey: 'user.deleteConfirm',
      subject: `${user.username} — ${user.displayName}`,
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.users.delete(user.id).subscribe();
        }
      });
  }
}
