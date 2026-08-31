import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth';

// Filtre grossier par role uniquement - le vrai controle "c'est bien SON voyage" reste
// fait par le backend (SubscriptionService/FeedbackService).
export const managerGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const role = authService.currentUser()?.role;
  if (role === 'TRAVEL_MANAGER' || role === 'ADMIN') {
    return true;
  }

  return router.createUrlTree(['/dashboard']);
};
