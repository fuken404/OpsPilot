import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { apiError } from '../core/api-error';
import { PriorityChip, StatusChip } from '../core/chips';
import { SlaApi } from '../core/sla-api';
import { SlaBadge } from '../core/sla-badge';
import { CATEGORY_LABELS, SlaClock, TicketSummary } from '../core/models';
import { Session } from '../core/session';
import { AssigneeFilter, TicketsApi, TicketView } from '../core/tickets-api';

@Component({
  selector: 'app-tickets',
  imports: [FormsModule, DatePipe, RouterLink, MatButtonModule, MatButtonToggleModule, MatFormFieldModule,
    MatInputModule, StatusChip, PriorityChip, SlaBadge],
  template: `
    <header class="head">
      <h1>Tickets</h1>
      <a mat-flat-button routerLink="nuevo">Nuevo ticket</a>
    </header>

    <div class="filters">
      <mat-button-toggle-group aria-label="Estado" [value]="view()" (change)="view.set($event.value)">
        <mat-button-toggle value="OPEN">Abiertos</mat-button-toggle>
        <mat-button-toggle value="CLOSED">Cerrados</mat-button-toggle>
        <mat-button-toggle value="ALL">Todos</mat-button-toggle>
      </mat-button-toggle-group>
      @if (session.can('ADMIN', 'TECHNICIAN')) {
        <mat-button-toggle-group aria-label="Responsable" [value]="assignee()" (change)="assignee.set($event.value)">
          <mat-button-toggle value="ANY">Todos</mat-button-toggle>
          <mat-button-toggle value="MINE">Míos</mat-button-toggle>
          <mat-button-toggle value="UNASSIGNED">Sin asignar</mat-button-toggle>
        </mat-button-toggle-group>
      }
      <mat-form-field class="search" subscriptSizing="dynamic">
        <mat-label>Buscar por título</mat-label>
        <input matInput type="search" [ngModel]="query()" (ngModelChange)="search($event)" />
      </mat-form-field>
    </div>

    @if (error(); as text) {
      <p class="error" role="alert">{{ text }}</p>
    } @else if (loading() && tickets().length === 0) {
      <p class="muted">Cargando…</p>
    } @else if (tickets().length === 0) {
      <p class="muted">No hay tickets con estos filtros.</p>
    } @else {
      <ul class="list">
        @for (t of tickets(); track t.id) {
          <li>
            <a [routerLink]="t.id" class="row">
              <span class="number">#{{ t.number }}</span>
              <span class="title">{{ t.title }}</span>
              <span class="meta">
                {{ categories[t.category] }} · {{ t.requester?.displayName }}
                · {{ t.assignee ? t.assignee.displayName : 'Sin asignar' }}
              </span>
              <app-priority-chip class="priority" [priority]="t.priority" />
              <app-status-chip class="status" [status]="t.status" />
              <span class="sla">
                @if (urgent().get(t.id); as clock) {
                  <app-sla-badge [clock]="clock" />
                }
              </span>
              <time class="date" [attr.datetime]="t.updatedAt">{{ t.updatedAt | date: 'd MMM, HH:mm' }}</time>
            </a>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    h1 { font: var(--mat-sys-headline-medium); margin: 8px 0; }
    .filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin: 12px 0 20px; }
    .search { flex: 1; min-width: 200px; }
    .list { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--mat-sys-outline-variant); }
    .row {
      display: grid; grid-template-columns: 56px 1fr auto auto 150px 96px;
      grid-template-areas: "num title prio status sla date" "num meta prio status sla date";
      column-gap: 16px; align-items: center; padding: 12px 8px; color: inherit; text-decoration: none;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .row:hover, .row:focus-visible { background: var(--mat-sys-surface-container-low); }
    .number { grid-area: num; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-label-large); }
    .title { grid-area: title; font: var(--mat-sys-title-small); }
    .meta { grid-area: meta; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-small); }
    .priority { grid-area: prio; }
    .status { grid-area: status; }
    .sla { grid-area: sla; text-align: right; }
    .date { grid-area: date; text-align: right; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-small); }
    .muted { color: var(--mat-sys-on-surface-variant); }
    .error { color: var(--mat-sys-error); }
    @media (max-width: 640px) {
      .row { grid-template-columns: auto auto 1fr auto; grid-template-areas: "num title title title" "meta meta meta meta" "prio status sla date"; row-gap: 6px; }
      .status { justify-self: start; }
    }
  `,
})
export class Tickets {
  protected readonly session = inject(Session);
  private readonly api = inject(TicketsApi);
  private readonly slaApi = inject(SlaApi);
  protected readonly categories = CATEGORY_LABELS;
  protected readonly view = signal<TicketView>('OPEN');
  protected readonly assignee = signal<AssigneeFilter>('ANY');
  protected readonly query = signal('');
  protected readonly tickets = signal<TicketSummary[]>([]);
  /** El compromiso más urgente de cada ticket abierto (solo para el equipo). */
  protected readonly urgent = signal(new Map<string, SlaClock>());
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  private searchTimer?: ReturnType<typeof setTimeout>;
  private request = 0;

  constructor() {
    effect(() => {
      const org = this.session.organization();
      const view = this.view();
      const assignee = this.assignee();
      const query = this.query();
      if (org) void this.load(org.organizationId, view, assignee, query);
    });
  }

  protected search(text: string): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.query.set(text), 250);
  }

  /** Si llegan respuestas fuera de orden, solo cuenta la última petición. */
  private async load(org: string, view: TicketView, assignee: AssigneeFilter, query: string): Promise<void> {
    const request = ++this.request;
    this.loading.set(true);
    try {
      const staff = this.session.can('ADMIN', 'TECHNICIAN');
      const [found, clocks] = await Promise.all([
        this.api.list(org, view, assignee, query),
        staff ? this.slaApi.open(org) : Promise.resolve([]),
      ]);
      if (request === this.request) {
        this.tickets.set(found);
        this.urgent.set(mostUrgent(clocks));
        this.error.set(null);
      }
    } catch (error) {
      if (request === this.request) this.error.set(apiError(error, 'No se pudieron cargar los tickets.'));
    } finally {
      if (request === this.request) this.loading.set(false);
    }
  }
}

/** Un vencido gana; si no, el que tiene menos tiempo restante. Los pausados quedan al final. */
function mostUrgent(clocks: SlaClock[]): Map<string, SlaClock> {
  const rank = (c: SlaClock) =>
    c.state === 'BREACHED' ? -Infinity : c.state === 'PAUSED' ? Infinity : (c.remainingMinutes ?? Infinity);
  const result = new Map<string, SlaClock>();
  for (const clock of clocks) {
    const current = result.get(clock.ticketId);
    if (!current || rank(clock) < rank(current)) result.set(clock.ticketId, clock);
  }
  return result;
}
