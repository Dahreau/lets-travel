import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TravelForm } from './travel-form';

describe('TravelForm', () => {
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

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TravelForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
    httpMock.expectOne('/api/users').flush([]);
  });

  afterEach(() => httpMock.verify());

  it('starts with one empty destination in create mode', () => {
    expect(component['destinationsArray'].length).toBe(1);
  });

  it('adds and removes destinations', () => {
    component['addDestination']();
    expect(component['destinationsArray'].length).toBe(2);

    component['removeDestination'](0);
    expect(component['destinationsArray'].length).toBe(1);
  });

  it('adds and removes activities within a destination', () => {
    expect(component['destinationActivities'](0).length).toBe(0);

    component['addActivity'](0);
    expect(component['destinationActivities'](0).length).toBe(1);

    component['removeActivity'](0, 0);
    expect(component['destinationActivities'](0).length).toBe(0);
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
    expect(component['transportationsArray'].length).toBe(0);

    component['addTransportation']();
    expect(component['transportationsArray'].length).toBe(1);

    component['removeTransportation'](0);
    expect(component['transportationsArray'].length).toBe(0);
  });
});
