import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ROLE_LABELS } from '../core/models';
import { Session } from '../core/session';
import { Dashboard } from './dashboard';

@Component({
  selector: 'app-overview',
  imports: [DatePipe, MatButtonModule, RouterLink, Dashboard],
  template: `
    @if (session.organization(); as org) {
      <header class="page-head">
        <div>
          <p class="eyebrow">{{ labels[org.role] }} · {{ today | date: "EEEE d 'de' MMMM" }}</p>
          <h1>{{ greeting() }}, {{ firstName() }}</h1>
        </div>
        <a mat-flat-button routerLink="/app/tickets/nuevo" class="btn-icon"><span class="icon">add</span>Nuevo ticket</a>
      </header>
      @if (session.can('ADMIN', 'TECHNICIAN')) {
        <app-dashboard />
      } @else {
        <section class="welcome card">
          <div>
            <h2>¿Algo no funciona?</h2>
            <p class="muted">
              Cuéntale al equipo de soporte de {{ org.name }} qué pasa. Verás el plazo comprometido y las respuestas
              llegarán aquí y a tu correo, sin recargar la página.
            </p>
            <div class="actions">
              <a mat-flat-button routerLink="/app/tickets/nuevo" class="btn-icon"><span class="icon">add</span>Reportar un problema</a>
              <a mat-stroked-button routerLink="/app/tickets">Ver mis tickets</a>
            </div>
          </div>
          <span class="icon art" aria-hidden="true">support_agent</span>
        </section>
      }
    } @else {
      <h1 class="empty">Aún no perteneces a ninguna organización</h1>
      <p class="muted">Pide a un administrador que te invite con el correo {{ session.me()?.email }}.</p>
    }
  `,
  styles: `
    .eyebrow { text-transform: none; letter-spacing: .02em; }
    .welcome { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 28px; }
    .welcome h2 { margin: 0 0 6px; font-size: 22px; font-weight: 650; }
    .welcome p { margin: 0 0 18px; max-width: 56ch; line-height: 1.6; }
    .actions { display: flex; gap: 10px; flex-wrap: wrap; }
    .art { font-size: 96px; color: #d9f99d; flex: none; }
    h1.empty { font-size: 28px; }
    @media (max-width: 640px) { .art { display: none; } }
  `,
})
export class Overview {
  protected readonly session = inject(Session);
  protected readonly labels = ROLE_LABELS;
  protected readonly today = new Date();

  protected greeting(): string {
    const hour = this.today.getHours();
    return hour < 12 ? 'Buenos días' : hour < 19 ? 'Buenas tardes' : 'Buenas noches';
  }

  protected firstName(): string {
    return (this.session.me()?.displayName ?? '').split(' ')[0];
  }
}
