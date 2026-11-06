import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { ROLE_LABELS } from '../core/models';
import { Session } from '../core/session';

@Component({
  selector: 'app-overview',
  imports: [MatCardModule],
  template: `
    @if (session.organization(); as org) {
      <h1>{{ org.name }}</h1>
      <p class="muted">Tu rol aquí: <strong>{{ labels[org.role] }}</strong>.</p>
      <mat-card appearance="outlined">
        <mat-card-content>
          Los tickets, el SLA y el tablero en tiempo real llegan en las siguientes fases.
        </mat-card-content>
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
