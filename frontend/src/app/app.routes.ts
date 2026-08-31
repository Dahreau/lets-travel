import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth-guard';
import { managerGuard } from './core/auth/manager-guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    // Inscription publique Traveler (feat/traveler-frontend) - pas de authGuard, comme /login.
    path: 'register',
    loadComponent: () => import('./features/register/register').then((m) => m.Register),
  },
  {
    // fix/audit-gaps (troubleshooting.md #41) : publique, comme /login et /register - la case a
    // cocher obligatoire de l'inscription doit pouvoir y renvoyer avant meme d'avoir un compte.
    path: 'politique-de-confidentialite',
    loadComponent: () =>
      import('./features/legal/privacy-policy/privacy-policy').then((m) => m.PrivacyPolicy),
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
        // Parcours Traveler (feat/traveler-frontend) : recherche + abonnement, distinct de
        // /travels (table CRUD Admin/Manager) - segment different, pas d'ambiguite de matching.
        path: 'browse',
        loadComponent: () =>
          import('./features/travels/travel-browse/travel-browse').then((m) => m.TravelBrowse),
      },
      {
        path: 'browse/:id',
        loadComponent: () =>
          import('./features/travels/travel-detail/travel-detail').then((m) => m.TravelDetail),
      },
      {
        // Reserve Travel Manager/Admin (cf. managerGuard). Doit precéder /travels/:id/edit ci-dessus.
        path: 'manager/travels/:id',
        canActivate: [managerGuard],
        loadComponent: () =>
          import('./features/manager/manager-travel-detail/manager-travel-detail').then(
            (m) => m.ManagerTravelDetail,
          ),
      },
      {
        // "view profiles" des abonnés (énoncé, role Travel Manager) - lecture seule, réservé
        // au manager/admin comme manager/travels/:id ci-dessus.
        path: 'manager/travelers/:id',
        canActivate: [managerGuard],
        loadComponent: () =>
          import('./features/manager/traveler-profile/traveler-profile').then(
            (m) => m.TravelerProfile,
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
      {
        // voir troubleshooting.md #41 - self-service RGPD, ouvert a tout role authentifie.
        path: 'mon-compte',
        loadComponent: () => import('./features/account/my-data/my-data').then((m) => m.MyData),
      },
      { path: '**', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
