import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password.component').then(
        m => m.ForgotPasswordComponent
      ),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password.component').then(
        m => m.ResetPasswordComponent
      ),
  },

  // ── Authenticated shell ────────────────────────────────────────────────────
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard-view/dashboard-view.component').then(
            m => m.DashboardViewComponent
          ),
      },
      {
        path: 'movimientos',
        loadComponent: () =>
          import('./features/movimientos/movimientos.component').then(
            m => m.MovimientosComponent
          ),
      },
      {
        path: 'config',
        loadComponent: () =>
          import('./features/config/configuracion/configuracion.component').then(
            m => m.ConfiguracionComponent
          ),
      },
      {
        path: 'config/categories',
        loadComponent: () =>
          import('./features/config/categories/categories.component').then(
            m => m.CategoriesComponent
          ),
      },
      {
        path: 'settings/security',
        loadComponent: () =>
          import('./features/auth/change-password/change-password.component').then(
            m => m.ChangePasswordComponent
          ),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },

  { path: '**', redirectTo: '/dashboard' },
];
