import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
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
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'admin', role: 'ADMIN' });
    fixture.detectChanges();

    expect(component['navItems']().map((item) => item.label)).toEqual([
      'dashboard',
      'voyages',
      'users',
      'travels',
      'payments',
      'payment-methods',
    ]);
  });

  it('shows the dashboard and browse links for a TRAVEL_MANAGER, never the admin tools', () => {
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'manager', role: 'TRAVEL_MANAGER' });
    fixture.detectChanges();

    expect(component['navItems']().map((item) => item.label)).toEqual(['dashboard', 'voyages']);
  });

  it('shows the browse & payment-methods nav for a TRAVELER, never the admin tools', () => {
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'traveler1', role: 'TRAVELER' });
    fixture.detectChanges();

    expect(component['navItems']().map((item) => item.label)).toEqual([
      'dashboard',
      'voyages',
      'payment-methods',
    ]);
  });
});
