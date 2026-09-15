import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { AuthResponse, AuthUser } from './models';

const STORAGE_KEY = 'vksiv.auth';

interface StoredTokens {
  access: string;
  refresh: string;
}

/**
 * localStorage is unavailable in private windows and can throw on access, so
 * every read and write is guarded. A failure here degrades to "not signed in"
 * rather than breaking the app.
 */
function readStoredTokens(): StoredTokens | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as StoredTokens) : null;
  } catch {
    return null;
  }
}

function writeStoredTokens(tokens: StoredTokens | null): void {
  try {
    if (tokens) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Non-fatal: the session simply will not survive a reload.
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokens = signal<StoredTokens | null>(readStoredTokens());

  readonly user = signal<AuthUser | null>(null);
  readonly isAuthenticated = computed(() => this.tokens() !== null);

  accessToken(): string | null {
    return this.tokens()?.access ?? null;
  }

  register(email: string, password: string, displayName: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/register', { email, password, displayName })
      .pipe(tap((response) => this.accept(response)));
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password })
      .pipe(tap((response) => this.accept(response)));
  }

  /** Restores the signed-in user after a page reload, using the stored token. */
  loadCurrentUser(): Observable<AuthUser> {
    return this.http.get<AuthUser>('/api/me').pipe(tap((user) => this.user.set(user)));
  }

  logout(): void {
    this.tokens.set(null);
    this.user.set(null);
    writeStoredTokens(null);
  }

  private accept(response: AuthResponse): void {
    const tokens: StoredTokens = { access: response.accessToken, refresh: response.refreshToken };
    this.tokens.set(tokens);
    this.user.set(response.user);
    writeStoredTokens(tokens);
  }
}
