import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { apiError } from '../core/api-error';
import { BarItem, BarList, Meter, TrendChart } from '../core/charts';
import { Dashboard as DashboardData, PRIORITY_LABELS, ROLE_LABELS, STATUS_LABELS, TicketPriority, TicketStatus } from '../core/models';
import { Realtime } from '../core/realtime';
import { Session } from '../core/session';

/** Tablero del equipo. Se recalcula solo cuando llega un evento en vivo. */
@Component({
  selector: 'app-dashboard',
  imports: [DecimalPipe, RouterLink, BarList, Meter, TrendChart],
  template: `
    @if (error(); as text) {
      <p class="error" role="alert">{{ text }}</p>
    } @else if (data(); as d) {
      <section class="kpis" aria-label="Resumen">
        <a class="tile hero" routerLink="/app/tickets">
          <span class="label"><span class="icon" aria-hidden="true">confirmation_number</span>Tickets abiertos</span>
          <span class="value">{{ d.open }}</span>
          <span class="hint">{{ d.unassigned }} sin responsable</span>
        </a>
        <div class="tile">
          <span class="label"><span class="icon" aria-hidden="true">person_off</span>Sin responsable</span>
          <span class="value">{{ d.unassigned }}</span>
        </div>
        <div class="tile" [class.flag-warn]="d.sla.atRisk > 0">
          <span class="label"><span class="icon warn" aria-hidden="true">warning</span>SLA en riesgo</span>
          <span class="value">{{ d.sla.atRisk }}</span>
        </div>
        <div class="tile" [class.flag-bad]="d.sla.breached > 0">
          <span class="label"><span class="icon bad" aria-hidden="true">error</span>SLA vencido</span>
          <span class="value">{{ d.sla.breached }}</span>
        </div>
        <div class="tile">
          <span class="label"><span class="icon" aria-hidden="true">schedule</span>Resolución promedio</span>
          <span class="value">
            @if (d.averageResolutionHours !== null) {
              {{ d.averageResolutionHours | number: '1.0-1' }}<small>h</small>
            } @else {
              —
            }
          </span>
          <span class="hint">Últimos 30 días</span>
        </div>
      </section>

      <div class="grid">
        <section class="card card-pad wide">
          <h2 class="card-title">Creados y resueltos</h2>
          <p class="card-sub">Últimos 14 días</p>
          <app-trend-chart [points]="d.trend" />
        </section>

        <section class="card card-pad">
          <h2 class="card-title">Cumplimiento de SLA</h2>
          <p class="card-sub">Compromisos terminados en los últimos 30 días</p>
          <div class="meters">
            <app-meter label="Primera respuesta a tiempo" [value]="d.compliance.response"
                       [note]="d.compliance.responseTotal + ' tickets'" />
            <app-meter label="Resolución a tiempo" [value]="d.compliance.resolution"
                       [note]="d.compliance.resolutionTotal + ' tickets'" />
          </div>
        </section>

        <section class="card card-pad">
          <h2 class="card-title">Carga por persona</h2>
          <p class="card-sub">Tickets abiertos asignados</p>
          <app-bar-list label="Carga por persona" [items]="workload()" />
        </section>

        <section class="card card-pad">
          <h2 class="card-title">Abiertos por estado</h2>
          <app-bar-list label="Abiertos por estado" [items]="byStatus()" />
        </section>

        <section class="card card-pad">
          <h2 class="card-title">Abiertos por prioridad</h2>
          <app-bar-list label="Abiertos por prioridad" [items]="byPriority()" />
        </section>
      </div>
    } @else {
      <p class="muted">Cargando tablero…</p>
    }
  `,
  styles: `
    .kpis { display: grid; grid-template-columns: 1.3fr repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
    .tile { display: flex; flex-direction: column; gap: 8px; padding: 16px 18px; border-radius: var(--radius); color: inherit;
      text-decoration: none; background: var(--card); border: 1px solid var(--line); box-shadow: var(--shadow); }
    .tile .label { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 500; color: var(--muted); }
    .tile .label .icon { font-size: 17px; }
    .tile .value { font: 650 34px/1 var(--font-display); letter-spacing: -0.03em; color: var(--ink); }
    .tile .value small { font-size: 18px; margin-left: 2px; color: var(--muted); }
    .tile .hint { font-size: 12.5px; color: var(--muted); }
    .tile.hero { background: var(--ink); border-color: var(--ink); color: var(--ink-text); }
    .tile.hero .label, .tile.hero .hint { color: var(--ink-muted); }
    .tile.hero .value { font-size: 52px; color: var(--lime); }
    .tile.hero:hover { background: var(--ink-2); }
    .icon.warn { color: #d58a06; }
    .icon.bad { color: var(--danger); }
    .flag-warn { border-color: #f5dfa0; background: #fffaeb; }
    .flag-bad { border-color: #f3b4b4; background: #fdf1f1; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
    .wide { grid-column: 1 / -1; }
    .card-sub { margin-bottom: 16px; }
    h2 + app-bar-list { display: block; margin-top: 16px; }
    .meters { display: flex; flex-direction: column; gap: 18px; }
    @media (max-width: 1100px) { .kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); } .hero { grid-column: span 3; } }
    @media (max-width: 720px) {
      .grid { grid-template-columns: 1fr; }
      .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .hero { grid-column: 1 / -1; }
    }
  `,
})
export class Dashboard {
  private readonly session = inject(Session);
  private readonly http = inject(HttpClient);
  protected readonly data = signal<DashboardData | null>(null);
  protected readonly error = signal<string | null>(null);
  private readonly refresh = signal(0);
  private liveTimer?: ReturnType<typeof setTimeout>;

  protected readonly byStatus = computed<BarItem[]>(() => {
    const d = this.data();
    return d ? (Object.keys(d.byStatus) as TicketStatus[]).map((s) => ({ label: STATUS_LABELS[s], value: d.byStatus[s] })) : [];
  });

  protected readonly byPriority = computed<BarItem[]>(() => {
    const d = this.data();
    return d ? (Object.keys(d.byPriority) as TicketPriority[])
      .map((p) => ({ label: PRIORITY_LABELS[p], value: d.byPriority[p] })) : [];
  });

  protected readonly workload = computed<BarItem[]>(() =>
    (this.data()?.workload ?? []).map((w) => ({
      label: w.displayName,
      value: w.open,
      note: `${ROLE_LABELS[w.role]}${w.breached ? ` · ${w.breached} con SLA vencido` : ''}`,
    })));

  constructor() {
    inject(Realtime).listen(() => {
      clearTimeout(this.liveTimer);
      this.liveTimer = setTimeout(() => this.refresh.update((n) => n + 1), 1000);
    });
    effect(() => {
      this.refresh();
      const org = this.session.organization()?.organizationId;
      if (org) void this.load(org);
    });
  }

  private async load(org: string): Promise<void> {
    try {
      this.data.set(await firstValueFrom(this.http.get<DashboardData>(`${environment.apiUrl}/organizations/${org}/dashboard`)));
      this.error.set(null);
    } catch (error) {
      this.error.set(apiError(error, 'No se pudo cargar el tablero.'));
    }
  }
}
