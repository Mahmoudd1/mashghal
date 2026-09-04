import { Routes } from '@angular/router';

import { adminGuard, authGuard } from './core/auth/auth.guard';
import { Shell } from './layout/shell/shell';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'fabrics',
        loadComponent: () => import('./features/fabrics/fabrics-page').then((m) => m.FabricsPage),
      },
      {
        path: 'models',
        loadComponent: () => import('./features/models/models-page').then((m) => m.ModelsPage),
      },
      {
        path: 'pipeline',
        loadComponent: () =>
          import('./features/pipeline/pipeline-page').then((m) => m.PipelinePage),
      },
      {
        path: 'reports',
        loadComponent: () => import('./features/reports/reports-page').then((m) => m.ReportsPage),
      },
      {
        path: 'suppliers',
        loadComponent: () =>
          import('./features/suppliers/suppliers-page').then((m) => m.SuppliersPage),
      },
      {
        path: 'users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/users/users-page').then((m) => m.UsersPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
