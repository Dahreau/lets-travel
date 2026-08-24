import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth';
import { Badge } from '../../shared/ui/badge';

interface NavItem {
  path: string;
  label: string;
}

const ADMIN_NAV_ITEMS: NavItem[] = [
  { path: '/dashboard', label: 'dashboard' },
  { path: '/users', label: 'users' },
  { path: '/travels', label: 'travels' },
  { path: '/payments', label: 'payments' },
  { path: '/payment-methods', label: 'payment-methods' },
];

// Un Travel Manager n'a accès à aucun des outils d'admin (users/payments/payment-methods
// sont réservés à l'Admin cote backend, cf. troubleshooting.md) : son unique entrée est le
// dashboard, qui bascule automatiquement sur sa propre vue (voir Dashboard.isManager).
const MANAGER_NAV_ITEMS: NavItem[] = [{ path: '/dashboard', label: 'dashboard' }];

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
  protected readonly navItems = computed(() =>
    this.role() === 'TRAVEL_MANAGER' ? MANAGER_NAV_ITEMS : ADMIN_NAV_ITEMS,
  );

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
