import { Component, inject, OnInit } from '@angular/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Avatar } from '../core/chips';
import { Logo } from '../core/logo';
import { ROLE_LABELS } from '../core/models';
import { Realtime } from '../core/realtime';
import { Session } from '../core/session';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  staff: boolean;
  exact?: boolean;
}

const NAV: NavItem[] = [
  { path: '/app', label: 'Inicio', icon: 'space_dashboard', staff: false, exact: true },
  { path: '/app/tickets', label: 'Tickets', icon: 'confirmation_number', staff: false },
  { path: '/app/sla', label: 'SLA', icon: 'timer', staff: true },
  { path: '/app/equipo', label: 'Equipo', icon: 'group', staff: true },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Avatar, Logo],
  // Campos de formulario con borde y sin espacio reservado para mensajes, en toda la app privada.
  providers: [{ provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline', subscriptSizing: 'dynamic' } }],
  template: `
    <div class="app">
      <aside class="rail">
        <a class="brand" routerLink="/app"><app-logo [light]="true" /></a>

        @if (session.me(); as me) {
          @if (me.memberships.length > 0) {
            <label class="org">
              <span class="org-label">Organización</span>
              <span class="org-box">
                <span class="org-mark" aria-hidden="true">{{ initial(session.organization()?.name) }}</span>
                <select [value]="session.organization()?.organizationId" (change)="select($event)">
                  @for (m of me.memberships; track m.organizationId) {
                    <option [value]="m.organizationId">{{ m.name }}</option>
                  }
                </select>
                <span class="icon chevron" aria-hidden="true">unfold_more</span>
              </span>
            </label>
          }

          <nav aria-label="Principal">
            @for (item of nav; track item.path) {
              @if (session.organization() && (!item.staff || session.can('ADMIN', 'TECHNICIAN'))) {
                <a [routerLink]="item.path" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: !!item.exact }">
                  <span class="icon" aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
                </a>
              }
            }
          </nav>

          <div class="rail-foot">
            @if (session.organization()) {
              <p class="live" [class.on]="realtime.status() === 'live'" role="status">
                <i aria-hidden="true"></i>
                {{ realtime.status() === 'live' ? 'Conectado en vivo' : 'Reconectando…' }}
              </p>
            }
            <div class="me">
              <app-avatar [name]="me.displayName" [size]="34" />
              <div class="me-text">
                <strong>{{ me.displayName }}</strong>
                <span>{{ labels[session.role() ?? 'CUSTOMER'] }}</span>
              </div>
              <button class="logout" type="button" (click)="session.logout()" title="Salir" aria-label="Salir">
                <span class="icon">logout</span>
              </button>
            </div>
          </div>
        }
      </aside>

      <main class="content">
        @if (session.error(); as error) {
          <p class="error-text" role="alert">{{ error }}</p>
        } @else if (session.me()) {
          <router-outlet />
        } @else {
          <p class="muted">Cargando…</p>
        }
      </main>

      @if (session.organization()) {
        <nav class="tabbar" aria-label="Principal">
          @for (item of nav; track item.path) {
            @if (!item.staff || session.can('ADMIN', 'TECHNICIAN')) {
              <a [routerLink]="item.path" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: !!item.exact }">
                <span class="icon" aria-hidden="true">{{ item.icon }}</span>
                <span>{{ item.label }}</span>
              </a>
            }
          }
        </nav>
      }
    </div>
  `,
  styles: `
    .app { display: grid; grid-template-columns: 248px minmax(0, 1fr); min-height: 100vh; }
    .rail { position: sticky; top: 0; height: 100vh; box-sizing: border-box; display: flex; flex-direction: column;
      gap: 20px; padding: 20px 14px; background: var(--ink); color: var(--ink-text); }
    .brand { padding: 4px 8px; text-decoration: none; }

    .org { display: flex; flex-direction: column; gap: 6px; }
    .org-label { padding: 0 8px; font: 500 11px/1 var(--font-mono); letter-spacing: .08em; text-transform: uppercase; color: var(--ink-muted); }
    .org-box { position: relative; display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 14px;
      background: var(--ink-2); border: 1px solid var(--ink-3); }
    .org-box:focus-within { border-color: var(--accent-light); }
    .org-mark { width: 26px; height: 26px; border-radius: 50%; display: grid; place-items: center; flex: none;
      background: var(--accent-light); color: #1e1b2e; font: 900 13px/1 var(--font-display); }
    .org select { appearance: none; flex: 1; min-width: 0; border: 0; background: transparent; color: #fff;
      font: 500 14px/1.2 var(--font-ui); padding-right: 18px; cursor: pointer; outline: none; }
    .org option { color: var(--ink); }
    .chevron { position: absolute; right: 8px; color: var(--ink-muted); font-size: 18px; pointer-events: none; }

    nav { display: flex; flex-direction: column; gap: 2px; }
    nav a { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-radius: 12px; color: var(--ink-text);
      text-decoration: none; font: 600 14.5px/1 var(--font-ui); }
    nav a .icon { font-size: 20px; color: var(--ink-muted); }
    nav a:hover { background: var(--ink-2); }
    nav a.active { background: var(--ink-3); color: #fff; font-weight: 800; }
    nav a.active .icon { color: var(--accent-light); font-variation-settings: 'FILL' 1, 'wght' 500, 'opsz' 20; }

    .rail-foot { margin-top: auto; display: flex; flex-direction: column; gap: 12px; }
    .live { display: flex; align-items: center; gap: 8px; margin: 0; padding: 0 10px; font: 500 12px/1 var(--font-ui); color: var(--ink-muted); }
    .live i { width: 7px; height: 7px; border-radius: 50%; background: #5c5f68; }
    .live.on i { background: var(--accent-light); box-shadow: 0 0 0 4px rgb(167 139 250 / 15%); animation: pulse 2.4s ease-in-out infinite; }
    .me { display: flex; align-items: center; gap: 10px; padding: 10px; border-radius: 16px; background: var(--ink-2); }
    .me-text { display: flex; flex-direction: column; min-width: 0; flex: 1; }
    .me-text strong { font: 800 13.5px/1.3 var(--font-ui); color: #fff; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .me-text span { font-size: 12px; color: var(--ink-muted); }
    .logout { border: 0; background: transparent; color: var(--ink-muted); cursor: pointer; padding: 6px; border-radius: 8px; display: grid; }
    .logout:hover { color: #fff; background: var(--ink-3); }

    .content { min-width: 0; padding: 32px clamp(16px, 4vw, 48px) 56px; max-width: 1200px; box-sizing: border-box; width: 100%; }
    .tabbar { display: none; }

    @keyframes pulse { 50% { box-shadow: 0 0 0 7px rgb(167 139 250 / 0%); } }
    @media (prefers-reduced-motion: reduce) { .live.on i { animation: none; } }

    /* Celular: barra superior compacta y navegación abajo, al alcance del pulgar. */
    @media (max-width: 860px) {
      .app { grid-template-columns: 1fr; }
      .rail { position: sticky; z-index: 5; height: auto; flex-direction: row; align-items: center; gap: 10px; padding: 10px 12px; }
      .rail nav, .live, .org-label, .me-text { display: none; }
      .brand { padding: 0; }
      .brand ::ng-deep .word { display: none; }
      .org { flex: 1; min-width: 0; }
      .org-box { padding: 6px 8px; }
      .rail-foot { margin: 0; }
      .me { padding: 0; background: transparent; gap: 4px; }
      .content { padding: 20px 16px 96px; }
      .tabbar { position: fixed; z-index: 5; bottom: 0; left: 0; right: 0; display: grid; grid-auto-flow: column; grid-auto-columns: 1fr;
        padding: 6px 8px calc(6px + env(safe-area-inset-bottom)); background: var(--ink); border-top: 1px solid var(--ink-3); }
      .tabbar a { display: flex; flex-direction: column; align-items: center; gap: 3px; padding: 6px 0; border-radius: 10px;
        color: var(--ink-muted); text-decoration: none; font: 500 11px/1 var(--font-ui); }
      .tabbar a .icon { font-size: 22px; }
      .tabbar a.active { color: #fff; }
      .tabbar a.active .icon { color: var(--accent-light); font-variation-settings: 'FILL' 1, 'wght' 500, 'opsz' 24; }
    }
  `,
})
export class Shell implements OnInit {
  protected readonly session = inject(Session);
  protected readonly realtime = inject(Realtime);
  protected readonly labels = ROLE_LABELS;
  protected readonly nav = NAV;

  ngOnInit(): void {
    void this.session.load();
  }

  protected select(event: Event): void {
    this.session.select((event.target as HTMLSelectElement).value);
  }

  protected initial(name: string | undefined): string {
    return (name ?? '?').charAt(0).toUpperCase();
  }
}
