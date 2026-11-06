import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { ROLE_LABELS } from '../core/models';
import { Session } from '../core/session';
import { Dashboard } from './dashboard';

@Component({
  selector: 'app-overview',
  imports: [MatCardModule, MatButtonModule, RouterLink, Dashboard],
  template: `
    @if (session.organization(); as org) {
      <header class="head">
        <div>
          <h1>{{ org.name }}</h1>
          <p class="muted">Tu rol aquí: <strong>{{ labels[org.role] }}</strong>.</p>
        </div>
        <a mat-flat-button routerLink="/app/tickets/nuevo">Nuevo ticket</a>
      </header>
      @if (session.can('ADMIN', 'TECHNICIAN')) {
        <app-dashboard />
      } @else {
        <mat-card appearance="outlined">
          <mat-card-content>
            Reporta un problema y sigue su avance; el equipo de soporte te responde en el mismo ticket y cada
            ticket muestra el plazo comprometido. Los cambios aparecen sin recargar la página.
          </mat-card-content>
          <mat-card-actions>
            <a mat-button routerLink="/app/tickets">Ver mis tickets</a>
          </mat-card-actions>
        </mat-card>
      }
    } @else {
      <h1>Aún no perteneces a ninguna organización</h1>
      <p class="muted">Pide a un administrador que te invite con el correo {{ session.me()?.email }}.</p>
    }
  `,
  styles: `
    .head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
    h1 { font: var(--mat-sys-headline-medium); margin: 8px 0; }
    .muted { color: var(--mat-sys-on-surface-variant); margin: 0 0 20px; }
  `,
})
export class Overview {
  protected readonly session = inject(Session);
  protected readonly labels = ROLE_LABELS;
}
