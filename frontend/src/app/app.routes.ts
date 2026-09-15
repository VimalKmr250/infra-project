import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'notes' },
  {
    path: 'login',
    loadComponent: () => import('./auth/login').then((m) => m.Login),
    title: 'Sign in',
  },
  {
    path: 'notes',
    canActivate: [authGuard],
    loadComponent: () => import('./notes/notes').then((m) => m.Notes),
    title: 'Notes',
  },
  { path: '**', redirectTo: 'notes' },
];
