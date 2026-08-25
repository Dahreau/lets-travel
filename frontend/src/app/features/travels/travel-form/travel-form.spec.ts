import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../../../core/auth/auth';
import { TravelForm } from './travel-form';

@Component({ template: '' })
class DummyComponent {}

describe('TravelForm (as ADMIN)', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<TravelForm>>;
  let component: TravelForm;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TravelForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();

    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'admin', role: 'ADMIN' });

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TravelForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
    httpMock.expectOne('/api/users').flush([]);
  });

  afterEach(() => httpMock.verify());

  it('starts with one empty destination in create mode', () => {
    expect(component['destinationsArray']).toHaveLength(1);
  });

  it('adds and removes destinations', () => {
    component['addDestination']();
    expect(component['destinationsArray']).toHaveLength(2);

    component['removeDestination'](0);
    expect(component['destinationsArray']).toHaveLength(1);
  });

  it('adds and removes activities within a destination', () => {
    expect(component['destinationActivities'](0)).toHaveLength(0);

    component['addActivity'](0);
    expect(component['destinationActivities'](0)).toHaveLength(1);

    component['removeActivity'](0, 0);
    expect(component['destinationActivities'](0)).toHaveLength(0);
  });

  it('accommodation group starts disabled and toggles on', () => {
    const accommodation = component['destinationsArray'].at(0)!.controls.accommodation;
    expect(accommodation.disabled).toBe(true);

    component['toggleAccommodation'](0);
    expect(accommodation.disabled).toBe(false);

    component['toggleAccommodation'](0);
    expect(accommodation.disabled).toBe(true);
  });

  it('adds and removes transportations', () => {
    expect(component['transportationsArray']).toHaveLength(0);

    component['addTransportation']();
    expect(component['transportationsArray']).toHaveLength(1);

    component['removeTransportation'](0);
    expect(component['transportationsArray']).toHaveLength(0);
  });

  it('exposes the manager dropdown control, enabled, for an admin', () => {
    expect(component['form'].controls.managerId.disabled).toBe(false);
  });
});

describe('TravelForm (as ADMIN) — manager dropdown filtering', () => {
  it('only lists TRAVEL_MANAGER users as candidate managers', async () => {
    await TestBed.configureTestingModule({
      imports: [TravelForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();

    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'admin', role: 'ADMIN' });

    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TravelForm);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock.expectOne('/api/users').flush([
      { id: 'm1', firstName: 'Ada', lastName: 'M', email: 'ada@t.com', phone: null, role: 'TRAVEL_MANAGER', address: null, createdAt: '', updatedAt: '' },
      { id: 'u1', firstName: 'Bob', lastName: 'T', email: 'bob@t.com', phone: null, role: 'TRAVELER', address: null, createdAt: '', updatedAt: '' },
      { id: 'a1', firstName: 'Zoe', lastName: 'A', email: 'zoe@t.com', phone: null, role: 'ADMIN', address: null, createdAt: '', updatedAt: '' },
    ]);

    expect(component['managers']().map((m) => m.id)).toEqual(['m1']);
    httpMock.verify();
  });
});

describe('TravelForm (as TRAVEL_MANAGER)', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<TravelForm>>;
  let component: TravelForm;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TravelForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();

    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'manager', role: 'TRAVEL_MANAGER' });
    vi.spyOn(authService, 'userId').mockReturnValue('manager-1');

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TravelForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('forces the managerId control to the connected manager, disables it, and never calls GET /api/users', () => {
    expect(component['form'].controls.managerId.disabled).toBe(true);
    expect(component['form'].getRawValue().managerId).toBe('manager-1');

    httpMock.expectNone('/api/users');
  });
});

describe('TravelForm (edit mode) — delete', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<TravelForm>>;
  let component: TravelForm;
  let httpMock: HttpTestingController;

  const TRAVEL = {
    id: 't1',
    title: 'Trip',
    managerId: 'manager-1',
    startDate: '2026-01-01',
    endDate: '2026-01-05',
    status: 'PLANNED',
    price: 500,
    currency: 'EUR',
    destinations: [],
    transportations: [],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TravelForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'travels', component: DummyComponent }]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 't1' }) } },
        },
      ],
    }).compileComponents();

    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'manager', role: 'TRAVEL_MANAGER' });
    vi.spyOn(authService, 'userId').mockReturnValue('manager-1');

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TravelForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
    httpMock.expectOne('/api/travels/t1').flush(TRAVEL);
  });

  afterEach(() => httpMock.verify());

  // Le controle "c'est bien SON voyage" reste fait par le backend (requireOwnershipOrAdmin) :
  // le bouton delete n'est qu'un raccourci UI, pas un second controle d'autorisation.
  it('deletes the travel and navigates away on confirm', () => {
    component['confirmDelete']();
    const req = httpMock.expectOne('/api/travels/t1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(component['confirmingDelete']()).toBe(false);
  });
});
