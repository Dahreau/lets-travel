import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ManagerPublic } from './manager-public';

describe('ManagerPublic', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<ManagerPublic>>;
  let component: ManagerPublic;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ManagerPublic],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ managerId: 'm1' }) } },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ManagerPublic);
    component = fixture.componentInstance;
  });

  afterEach(() => httpMock.verify());

  it('loads the public stats for the manager in the route, including the per-travel ratings', () => {
    fixture.detectChanges();

    const travelRatings = [
      { travelId: 't1', title: 'Trip 1', averageRating: 4.5, feedbackCount: 2 },
      { travelId: 't2', title: 'Trip 2', averageRating: null, feedbackCount: 0 },
    ];
    httpMock
      .expectOne('/api/travels/managers/m1/public-stats')
      .flush({ travelCount: 3, averageRating: 4.2, reportCount: 1, travelRatings });

    expect(component['stats']()).toEqual({ travelCount: 3, averageRating: 4.2, reportCount: 1, travelRatings });
    expect(component['loading']()).toBe(false);
  });

  it('leaves averageRating as null when no feedback exists yet, never 0', () => {
    fixture.detectChanges();

    httpMock
      .expectOne('/api/travels/managers/m1/public-stats')
      .flush({ travelCount: 0, averageRating: null, reportCount: 0, travelRatings: [] });

    expect(component['stats']()?.averageRating).toBeNull();
  });
});
