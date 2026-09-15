import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { Note } from '../core/models';
import { NotesService } from '../core/notes.service';

@Component({
  selector: 'app-notes',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './notes.html',
  styleUrl: './notes.scss',
})
export class Notes implements OnInit {
  private readonly notesService = inject(NotesService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly notes = signal<Note[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly user = this.auth.user;

  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    body: ['', [Validators.required, Validators.maxLength(10000)]],
  });

  ngOnInit(): void {
    // The token survives a reload but the in-memory user does not.
    if (!this.user()) {
      this.auth.loadCurrentUser().subscribe({ error: () => undefined });
    }
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.notesService.list().subscribe({
      next: (notes) => {
        this.notes.set(notes);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(`Could not load notes (${err.status}).`);
      },
    });
  }

  protected add(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);

    this.notesService.create(this.form.getRawValue()).subscribe({
      next: (created) => {
        this.notes.update((current) => [created, ...current]);
        this.form.reset();
        this.saving.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(`Could not save the note (${err.status}).`);
      },
    });
  }

  protected remove(note: Note): void {
    this.notesService.remove(note.id).subscribe({
      next: () => this.notes.update((current) => current.filter((n) => n.id !== note.id)),
      error: (err: HttpErrorResponse) => this.error.set(`Could not delete (${err.status}).`),
    });
  }

  protected signOut(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
