import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { ROLE_LABELS } from '../core/models';
import { Session } from '../core/session';

@Component({
  selector: 'app-overview',
  imports: [MatCardModule, MatButtonModule, RouterLink],
  template: `
    @if (session.organization(); as org) {
      <h1>{{ org.name }}</h1>
      <p class="muted">Tu rol aquí: <strong>{{ labels[org.role] }}</strong>.</p>
      <mat-card appearance="outlined">
        <mat-card-content>
          @if (session.can('CUSTOMER')) {
            Reporta un problema y sigue su avance; el equipo de soporte te responde en el mismo ticket y cada ticket muestra el plazo comprometido.
          } @else {
            Revisa los tickets abiertos y cuánto les queda de SLA, toma los que no tienen responsable y deja notas internas para el equipo.
          }
          El tablero en tiempo real llega en la siguiente fase.
        </mat-card-content>
        <mat-card-actions>
          <a mat-button routerLink="/app/tickets">Ver tickets</a>
          <a mat-button routerLink="/app/tickets/nuevo">Nuevo ticket</a>
        </mat-card-actions>
      </mat-card>
    } @else {
      <h1>Aún no perteneces a ninguna organización</h1>
      <p class="muted">Pide a un administrador que te invite con el correo {{ session.me()?.email }}.</p>
    }
  `,
  styles: `
    h1 { font: var(--mat-sys-headline-medium); margin: 8px 0; }
    .muted { color: var(--mat-sys-on-surface-variant); margin-bottom: 24px; }
  `,
})
export class Overview {
  protected readonly session = inject(Session);
  protected readonly labels = ROLE_LABELS;
}
