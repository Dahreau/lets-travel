import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  // currentUser() n'est peuplé qu'au login : après un reload le token survit mais
  // currentUser repart à null tant que /me n'est pas rappelé - on le repeuple ici.
  if (authService.currentUser()) {
    return true;
  }

  return authService.me().pipe(
    map(() => true),
    catchError(() => of(router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }))),
  );
};
