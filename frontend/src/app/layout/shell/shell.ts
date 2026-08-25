import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth';
import { Badge } from '../../shared/ui/badge';

interface NavItem {
  path: string;
  label: string;
}

// Sonar S1192 (New Code) : ce literal apparaissait dans les 3 tableaux ci-dessous.
const DASHBOARD_NAV_ITEM: NavItem = { path: '/dashboard', label: 'dashboard' };

const ADMIN_NAV_ITEMS: NavItem[] = [
  DASHBOARD_NAV_ITEM,
  { path: '/users', label: 'users' },
  { path: '/travels', label: 'travels' },
  { path: '/payments', label: 'payments' },
  { path: '/payment-methods', label: 'payment-methods' },
];

// Un Travel Manager n'a accès à aucun des outils d'admin (users/payments/payment-methods
// sont réservés à l'Admin cote backend, cf. troubleshooting.md) : son unique entrée est le
// dashboard, qui bascule automatiquement sur sa propre vue (voir Dashboard.isManager).
const MANAGER_NAV_ITEMS: NavItem[] = [DASHBOARD_NAV_ITEM];

// feat/traveler-frontend : parcourir/s'abonner (browse) et gerer ses moyens de paiement
// (deja scopes au caller cote backend, cf. PaymentMethodController.findAll) - pas d'acces
// a /payments (liste globale, Admin-only) ni /users.
const TRAVELER_NAV_ITEMS: NavItem[] = [
  DASHBOARD_NAV_ITEM,
  { path: '/browse', label: 'voyages' },
  { path: '/payment-methods', label: 'payment-methods' },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Badge],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly sidebarOpen = signal(false);
  protected readonly username = this.authService.username;
  protected readonly role = computed(() => this.authService.currentUser()?.role ?? 'ADMIN');
  // Un TRAVELER (role exact, pas la RoleHierarchy backend) recevait par erreur la nav Admin
  // faute de 3e branche - corrige ici avec un vrai aiguillage sur les 3 roles.
  protected readonly navItems = computed(() => {
    switch (this.role()) {
      case 'TRAVEL_MANAGER':
        return MANAGER_NAV_ITEMS;
      case 'TRAVELER':
        return TRAVELER_NAV_ITEMS;
      default:
        return ADMIN_NAV_ITEMS;
    }
  });

  protected toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  protected closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  protected logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
