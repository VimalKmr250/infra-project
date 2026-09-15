import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ProblemDetail } from '../core/models';

type Mode = 'login' | 'register';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly mode = signal<Mode>('login');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(10)]],
    displayName: [''],
  });

  protected toggleMode(): void {
    this.mode.set(this.mode() === 'login' ? 'register' : 'login');
    this.error.set(null);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const { email, password, displayName } = this.form.getRawValue();
    const request =
      this.mode() === 'login'
        ? this.auth.login(email, password)
        : this.auth.register(email, password, displayName || email.split('@')[0]);

    request.subscribe({
      next: () => void this.router.navigate(['/notes']),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(describe(err));
      },
    });
  }
}

function describe(err: HttpErrorResponse): string {
  const problem = err.error as ProblemDetail | undefined;
  if (problem?.errors) {
    return Object.entries(problem.errors)
      .map(([field, message]) => `${field}: ${message}`)
      .join('; ');
  }
  if (problem?.detail) {
    return problem.detail;
  }
  return err.status === 0 ? 'Cannot reach the server.' : `Request failed (${err.status}).`;
}
