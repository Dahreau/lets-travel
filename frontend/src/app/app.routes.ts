import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth-guard';
import { managerGuard } from './core/auth/manager-guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'users',
        loadComponent: () => import('./features/users/user-list/user-list').then((m) => m.UserList),
      },
      {
        path: 'users/new',
        loadComponent: () => import('./features/users/user-form/user-form').then((m) => m.UserForm),
      },
      {
        path: 'users/:id/edit',
        loadComponent: () => import('./features/users/user-form/user-form').then((m) => m.UserForm),
      },
      {
        path: 'travels',
        loadComponent: () =>
          import('./features/travels/travel-list/travel-list').then((m) => m.TravelList),
      },
      {
        path: 'travels/new',
        loadComponent: () =>
          import('./features/travels/travel-form/travel-form').then((m) => m.TravelForm),
      },
      {
        path: 'travels/:id/edit',
        loadComponent: () =>
          import('./features/travels/travel-form/travel-form').then((m) => m.TravelForm),
      },
      {
        // Gestion des abonnés/feedback d'un voyage - réservé au Travel Manager propriétaire
        // (ou Admin), cf. managerGuard. Doit précéder aucune autre route travels/* : segment
        // "manager" distinct, pas d'ambiguïté de matching avec /travels/:id/edit ci-dessus.
        path: 'manager/travels/:id',
        canActivate: [managerGuard],
        loadComponent: () =>
          import('./features/manager/manager-travel-detail/manager-travel-detail').then(
            (m) => m.ManagerTravelDetail,
          ),
      },
      {
        // Page publique (enoncé, section Traveler) : ouverte à tout utilisateur authentifié,
        // pas de guard dédié au-delà de authGuard (déjà appliqué au Shell parent).
        path: 'manager/:managerId',
        loadComponent: () =>
          import('./features/manager/manager-public/manager-public').then((m) => m.ManagerPublic),
      },
      {
        path: 'payments',
        loadComponent: () =>
          import('./features/payments/payment-list/payment-list').then((m) => m.PaymentList),
      },
      {
        path: 'payments/new',
        loadComponent: () =>
          import('./features/payments/payment-form/payment-form').then((m) => m.PaymentForm),
      },
      {
        path: 'payment-methods',
        loadComponent: () =>
          import('./features/payments/payment-method-list/payment-method-list').then(
            (m) => m.PaymentMethodList,
          ),
      },
      {
        path: 'payment-methods/new',
        loadComponent: () =>
          import('./features/payments/payment-method-form/payment-method-form').then(
            (m) => m.PaymentMethodForm,
          ),
      },
      {
        path: 'payment-methods/:id/edit',
        loadComponent: () =>
          import('./features/payments/payment-method-form/payment-method-form').then(
            (m) => m.PaymentMethodForm,
          ),
      },
      { path: '**', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
