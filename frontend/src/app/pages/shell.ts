import { Component, inject, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Session } from '../core/session';
import { ROLE_LABELS } from '../core/models';

@Component({
  selector: 'app-shell',
  imports: [MatToolbarModule, MatButtonModule, MatSelectModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <mat-toolbar class="bar">
      <a class="brand" routerLink="/app">OpsPilot</a>
      @if (session.me(); as me) {
        @if (me.memberships.length > 0) {
          <mat-select class="org" aria-label="Organización" [value]="session.organization()?.organizationId"
                      (valueChange)="session.select($event)">
            @for (m of me.memberships; track m.organizationId) {
              <mat-option [value]="m.organizationId">{{ m.name }}</mat-option>
            }
          </mat-select>
        }
        <nav>
          <a mat-button routerLink="/app" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Inicio</a>
          @if (session.can('ADMIN', 'TECHNICIAN')) {
            <a mat-button routerLink="/app/equipo" routerLinkActive="active">Equipo</a>
          }
        </nav>
        <span class="spacer"></span>
        <span class="who">{{ me.displayName }} · {{ labels[session.role() ?? 'CUSTOMER'] }}</span>
        <button mat-button (click)="session.logout()">Salir</button>
      }
    </mat-toolbar>

    <main class="content">
      @if (session.error(); as error) {
        <p class="error" role="alert">{{ error }}</p>
      } @else if (session.me()) {
        <router-outlet />
      } @else {
        <p>Cargando…</p>
      }
    </main>
  `,
  styles: `
    .bar { gap: 12px; flex-wrap: wrap; height: auto; min-height: 64px; }
    .brand { color: inherit; font: var(--mat-sys-title-large); text-decoration: none; }
    .org { width: 200px; }
    .spacer { flex: 1; }
    .who { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .active { background: var(--mat-sys-secondary-container); }
    .content { max-width: 1100px; margin: 0 auto; padding: 24px; }
    .error { color: var(--mat-sys-error); }
  `,
})
export class Shell implements OnInit {
  protected readonly session = inject(Session);
  protected readonly labels = ROLE_LABELS;

  ngOnInit(): void {
    void this.session.load();
  }
}
