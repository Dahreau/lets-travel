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

  // currentUser() n'est peuplé qu'au login (voir Login.submit) : après un rechargement de
  // page, le token survit dans localStorage mais currentUser repart à null tant que /me n'est
  // pas rappelé. Ça cassait déjà l'affichage du username dans la topbar ; ça casserait aussi
  // le filtrage par rôle (nav manager, managerGuard) ajouté par feat/manager-frontend. On le
  // répare une bonne fois ici, au seul endroit par lequel passe toute route protégée.
  if (authService.currentUser()) {
    return true;
  }

  return authService.me().pipe(
    map(() => true),
    catchError(() => of(router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }))),
  );
};
