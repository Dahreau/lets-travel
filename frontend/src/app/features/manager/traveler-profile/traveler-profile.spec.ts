import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TravelerProfile } from './traveler-profile';

const TRAVELER = {
  id: 'u1',
  firstName: 'Alice',
  lastName: 'Martin',
  email: 'alice@example.com',
  phone: '0102030405',
  role: 'TRAVELER' as const,
  address: { street: '1 rue de la Paix', city: 'Paris', postalCode: '75001', country: 'France' },
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-01-01T00:00:00Z',
  privacyAcceptedAt: '2025-01-01T00:00:00Z',
};

describe('TravelerProfile', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<TravelerProfile>>;
  let component: TravelerProfile;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TravelerProfile],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'u1' }) } },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TravelerProfile);
    component = fixture.componentInstance;
  });

  afterEach(() => httpMock.verify());

  it('loads the traveler profile for the id in the route', () => {
    fixture.detectChanges();

    httpMock.expectOne('/api/users/u1').flush(TRAVELER);

    expect(component['traveler']()).toEqual(TRAVELER);
    expect(component['loading']()).toBe(false);
  });

  it('stops loading and leaves traveler null when the request fails (e.g. not a real subscriber, #38)', () => {
    fixture.detectChanges();

    httpMock.expectOne('/api/users/u1').flush(
      { timestamp: '2025-01-01T00:00:00Z', status: 403, error: 'Forbidden', message: 'Acces refuse' },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(component['traveler']()).toBeNull();
    expect(component['loading']()).toBe(false);
  });
});
