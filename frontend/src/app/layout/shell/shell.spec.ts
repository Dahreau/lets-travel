import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth';
import { Shell } from './shell';

describe('Shell', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Shell>>;
  let component: Shell;
  let authService: AuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    authService = TestBed.inject(AuthService);
    fixture = TestBed.createComponent(Shell);
    component = fixture.componentInstance;
  });

  it('shows the full admin toolkit nav, including browse, for an ADMIN', () => {
    spyOn(authService, 'currentUser').and.returnValue({ username: 'admin', role: 'ADMIN' });
    fixture.detectChanges();

    expect(component['navItems']().map((item) => item.label)).toEqual([
      'dashboard',
      'voyages',
      'users',
      'travels',
      'payments',
      'payment-methods',
      // fix/audit-gaps (troubleshooting.md #41) : self-service RGPD, ajoute aux 3 roles.
      'mon compte',
    ]);
  });

  it('shows the dashboard, browse and payment-methods links for a TRAVEL_MANAGER, never the admin tools', () => {
    spyOn(authService, 'currentUser').and.returnValue({ username: 'manager', role: 'TRAVEL_MANAGER' });
    fixture.detectChanges();

    expect(component['navItems']().map((item) => item.label)).toEqual([
      'dashboard',
      'voyages',
      'payment-methods',
      // fix/audit-gaps (troubleshooting.md #41) : self-service RGPD, ajoute aux 3 roles.
      'mon compte',
    ]);
  });

  it('shows the browse & payment-methods nav for a TRAVELER, never the admin tools', () => {
    spyOn(authService, 'currentUser').and.returnValue({ username: 'traveler1', role: 'TRAVELER' });
    fixture.detectChanges();

    expect(component['navItems']().map((item) => item.label)).toEqual([
      'dashboard',
      'voyages',
      'payment-methods',
      // fix/audit-gaps (troubleshooting.md #41) : self-service RGPD, ajoute aux 3 roles.
      'mon compte',
    ]);
  });
});
