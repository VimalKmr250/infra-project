import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from './auth.service';
import { AuthResponse } from './models';

const RESPONSE: AuthResponse = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  expiresInSeconds: 3600,
  user: { id: 'u1', email: 'a@b.test', displayName: 'A' },
};

/**
 * The test environment does not provide a full localStorage implementation, which
 * is exactly the situation AuthService is written to tolerate. These tests assert
 * on the service's own state rather than on what landed in storage.
 */
describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    service.logout();
  });

  afterEach(() => http.verify());

  it('starts unauthenticated', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.accessToken()).toBeNull();
  });

  it('stores the token and user after a successful login', () => {
    service.login('a@b.test', 'correct-horse-battery').subscribe();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush(RESPONSE);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('access-token');
    expect(service.user()?.email).toBe('a@b.test');
  });

  it('posts the display name when registering', () => {
    service.register('new@b.test', 'correct-horse-battery', 'New User').subscribe();

    const request = http.expectOne('/api/auth/register');
    expect(request.request.body).toEqual({
      email: 'new@b.test',
      password: 'correct-horse-battery',
      displayName: 'New User',
    });
    request.flush(RESPONSE);
  });

  it('clears the session on logout', () => {
    service.login('a@b.test', 'correct-horse-battery').subscribe();
    http.expectOne('/api/auth/login').flush(RESPONSE);

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.user()).toBeNull();
    expect(service.accessToken()).toBeNull();
  });
});
