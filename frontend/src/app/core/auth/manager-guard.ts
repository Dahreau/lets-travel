import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth';

// Reserve aux routes /manager/travels/:id (gestion des abonnes d'un voyage). authGuard tourne
// avant (canActivate du Shell parent) et garantit que currentUser() est déjà peuplé ici -
// pas besoin de rappeler /me. Le vrai controle "c'est bien SON voyage" reste fait par le
// backend (SubscriptionService/FeedbackService) : ce guard n'est qu'un filtre grossier par
// role, pour eviter qu'un simple Traveler atterrisse sur une page qui va 403 partout.
export const managerGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const role = authService.currentUser()?.role;
  if (role === 'TRAVEL_MANAGER' || role === 'ADMIN') {
    return true;
  }

  return router.createUrlTree(['/dashboard']);
};
