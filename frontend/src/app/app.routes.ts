import { Routes } from '@angular/router';
import { autoLoginPartialRoutesGuard } from 'angular-auth-oidc-client';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/landing').then((m) => m.Landing) },
  {
    path: 'app',
    canActivate: [autoLoginPartialRoutesGuard],
    loadComponent: () => import('./pages/shell').then((m) => m.Shell),
    children: [
      { path: '', loadComponent: () => import('./pages/overview').then((m) => m.Overview) },
      { path: 'tickets', loadComponent: () => import('./pages/tickets').then((m) => m.Tickets) },
      { path: 'tickets/nuevo', loadComponent: () => import('./pages/new-ticket').then((m) => m.NewTicket) },
      { path: 'tickets/:id', loadComponent: () => import('./pages/ticket').then((m) => m.Ticket) },
      { path: 'equipo', loadComponent: () => import('./pages/members').then((m) => m.Members) },
    ],
  },
  { path: '**', redirectTo: '' },
];
