import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth';
import { Badge } from '../../shared/ui/badge';

interface NavItem {
  path: string;
  label: string;
}

// Sonar S1192 (New Code) : ces literals apparaissaient dans plusieurs des 3 tableaux ci-dessous.
const DASHBOARD_NAV_ITEM: NavItem = { path: '/dashboard', label: 'dashboard' };
const BROWSE_NAV_ITEM: NavItem = { path: '/browse', label: 'voyages' };
// voir troubleshooting.md #41 - self-service RGPD, meme route pour les 3 roles (JWT resolvent
// l'appelant, jamais un id).
const ACCOUNT_NAV_ITEM: NavItem = { path: '/mon-compte', label: 'mon compte' };

const ADMIN_NAV_ITEMS: NavItem[] = [
  DASHBOARD_NAV_ITEM,
  BROWSE_NAV_ITEM,
  { path: '/users', label: 'users' },
  { path: '/travels', label: 'travels' },
  { path: '/payments', label: 'payments' },
  { path: '/payment-methods', label: 'payment-methods' },
  ACCOUNT_NAV_ITEM,
];

// fix/audit-gaps : /payment-methods manquait alors que le backend l'autorise deja (scope
// au caller, cf. PaymentMethodController.findAll) - users/payments restent Admin-only.
const MANAGER_NAV_ITEMS: NavItem[] = [
  DASHBOARD_NAV_ITEM,
  BROWSE_NAV_ITEM,
  { path: '/payment-methods', label: 'payment-methods' },
  ACCOUNT_NAV_ITEM,
];

// Parcourir/s'abonner (browse) et gerer ses moyens de paiement (scopes au caller cote
// backend) - pas d'acces a /payments (Admin-only) ni /users.
const TRAVELER_NAV_ITEMS: NavItem[] = [
  DASHBOARD_NAV_ITEM,
  BROWSE_NAV_ITEM,
  { path: '/payment-methods', label: 'payment-methods' },
  ACCOUNT_NAV_ITEM,
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
