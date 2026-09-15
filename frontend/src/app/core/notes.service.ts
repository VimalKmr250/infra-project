import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Note, NoteRequest } from './models';

@Injectable({ providedIn: 'root' })
export class NotesService {
  private readonly http = inject(HttpClient);

  list(): Observable<Note[]> {
    return this.http.get<Note[]>('/api/notes');
  }

  create(note: NoteRequest): Observable<Note> {
    return this.http.post<Note>('/api/notes', note);
  }

  update(id: string, note: NoteRequest): Observable<Note> {
    return this.http.put<Note>(`/api/notes/${id}`, note);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`/api/notes/${id}`);
  }
}
