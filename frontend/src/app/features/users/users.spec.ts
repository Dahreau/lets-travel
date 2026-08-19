import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserRequest } from '../../core/models/user';
import { UsersService } from './users';

describe('UsersService', () => {
  let service: UsersService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UsersService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the list of users', () => {
    service.findAll().subscribe();
    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('GETs a single user by id', () => {
    service.findById('u1').subscribe();
    const req = httpMock.expectOne('/api/users/u1');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('POSTs a new user', () => {
    const request: UserRequest = {
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      phone: null,
      role: 'TRAVELER',
      address: null,
    };
    service.create(request).subscribe();
    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('PUTs an update to an existing user', () => {
    const request: UserRequest = {
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      phone: null,
      role: 'ADMIN',
      address: null,
    };
    service.update('u1', request).subscribe();
    const req = httpMock.expectOne('/api/users/u1');
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('DELETEs a user', () => {
    service.delete('u1').subscribe();
    const req = httpMock.expectOne('/api/users/u1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
