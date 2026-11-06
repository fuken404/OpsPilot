import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { apiError } from '../core/api-error';
import { Avatar, PriorityChip, StatusChip, ticketKey, timeAgo } from '../core/chips';
import { SlaApi } from '../core/sla-api';
import { SlaBadge } from '../core/sla-badge';
import { CATEGORY_LABELS, SlaClock, TicketSummary } from '../core/models';
import { Realtime } from '../core/realtime';
import { Session } from '../core/session';
import { AssigneeFilter, TicketsApi, TicketView } from '../core/tickets-api';

@Component({
  selector: 'app-tickets',
  imports: [FormsModule, RouterLink, MatButtonModule, StatusChip, PriorityChip, SlaBadge, Avatar],
  template: `
    <header class="page-head">
      <div>
        <p class="eyebrow">{{ session.organization()?.name }}</p>
        <h1>Tickets</h1>
      </div>
      <a mat-flat-button routerLink="nuevo" class="btn-icon"><span class="icon">add</span>Nuevo ticket</a>
    </header>

    <div class="toolbar">
      <div class="segmented" role="group" aria-label="Estado">
        @for (option of views; track option.value) {
          <button type="button" [attr.aria-pressed]="view() === option.value" (click)="view.set(option.value)">
            {{ option.label }}
          </button>
        }
      </div>
      @if (session.can('ADMIN', 'TECHNICIAN')) {
        <div class="segmented" role="group" aria-label="Responsable">
          @for (option of assignees; track option.value) {
            <button type="button" [attr.aria-pressed]="assignee() === option.value" (click)="assignee.set(option.value)">
              {{ option.label }}
            </button>
          }
        </div>
      }
      <label class="search">
        <span class="icon" aria-hidden="true">search</span>
        <span class="sr-only">Buscar por título</span>
        <input type="search" placeholder="Buscar por título" [ngModel]="query()" (ngModelChange)="search($event)" />
      </label>
    </div>

    <section class="card list" [attr.aria-busy]="loading()">
      <div class="list-head" aria-hidden="true">
        <span>Ticket</span><span>Prioridad</span><span>Estado</span><span>SLA</span><span>Responsable</span><span>Actualizado</span>
      </div>
      @if (error(); as text) {
        <p class="empty error-text" role="alert">{{ text }}</p>
      } @else if (loading() && tickets().length === 0) {
        <p class="empty muted">Cargando…</p>
      } @else if (tickets().length === 0) {
        <div class="empty">
          <span class="icon big" aria-hidden="true">inbox</span>
          <p><strong>Nada por aquí.</strong></p>
          <p class="muted">No hay tickets con estos filtros.</p>
        </div>
      } @else {
        <ul>
          @for (t of tickets(); track t.id) {
            <li>
              <a [routerLink]="t.id" class="row">
                <span class="main">
                  <span class="key mono">{{ key(t.number) }}</span>
                  <span class="title">{{ t.title }}</span>
                  <span class="meta">{{ categories[t.category] }} · {{ t.requester?.displayName }}</span>
                </span>
                <app-priority-chip class="c-prio" [priority]="t.priority" />
                <app-status-chip class="c-status" [status]="t.status" />
                <span class="c-sla">
                  @if (urgent().get(t.id); as clock) {
                    <app-sla-badge [clock]="clock" />
                  } @else {
                    <span class="muted">—</span>
                  }
                </span>
                <span class="c-who">
                  <app-avatar [name]="t.assignee?.displayName" [size]="26" />
                  <span class="who-name">{{ t.assignee?.displayName ?? 'Sin asignar' }}</span>
                </span>
                <time class="c-date mono" [attr.datetime]="t.updatedAt">{{ ago(t.updatedAt) }}</time>
              </a>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    .toolbar { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 16px; }
    .search { flex: 1; min-width: 220px; display: flex; align-items: center; gap: 8px; padding: 0 12px; height: 40px;
      background: var(--card); border: 1px solid var(--line-strong); border-radius: 999px; }
    .search:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px #ede9fe; }
    .search .icon { color: var(--muted); }
    .search input { flex: 1; border: 0; outline: none; background: transparent; font: 400 14px var(--font-ui); color: var(--text); }

    .list { overflow: hidden; }
    .list-head, .row { display: grid; grid-template-columns: minmax(0, 1fr) 92px 128px 150px 140px 70px; gap: 16px; align-items: center; }
    .list-head { padding: 10px 20px; border-bottom: 1px solid var(--line); background: #fbfaff;
      font: 800 11.5px/1 var(--font-ui); letter-spacing: .05em; text-transform: uppercase; color: var(--muted); }
    ul { list-style: none; margin: 0; padding: 0; }
    li + li { border-top: 1px dashed var(--line-strong); }
    .row { padding: 14px 20px; color: inherit; text-decoration: none; transition: background .12s ease; }
    .row:hover { background: #fbfaff; }
    .row:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }
    .main { display: grid; grid-template-columns: auto minmax(0, 1fr); column-gap: 10px; row-gap: 2px; min-width: 0; }
    .key { grid-row: span 2; align-self: start; padding: 3px 8px; border-radius: 999px; background: var(--accent-soft); font-size: 11.5px; font-weight: 600; color: var(--accent-ink); }
    .title { font-weight: 800; font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .meta { font-size: 13px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .c-who { display: flex; align-items: center; gap: 8px; min-width: 0; }
    .who-name { font-size: 13.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .c-date { font-size: 12px; color: var(--muted); text-align: right; }
    .empty { padding: 48px 20px; text-align: center; margin: 0; }
    .empty p { margin: 4px 0; }
    .big { font-size: 40px; color: var(--line-strong); }

    @media (max-width: 1080px) {
      .list-head { display: none; }
      .row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 12px; }
      .main { flex-basis: 100%; }
      .who-name { display: none; }
      .c-sla:has(.muted) { display: none; }
      .c-date { margin-left: auto; }
    }
  `,
})
export class Tickets {
  protected readonly session = inject(Session);
  private readonly api = inject(TicketsApi);
  private readonly slaApi = inject(SlaApi);
  protected readonly categories = CATEGORY_LABELS;
  protected readonly views: { value: TicketView; label: string }[] = [
    { value: 'OPEN', label: 'Abiertos' }, { value: 'CLOSED', label: 'Cerrados' }, { value: 'ALL', label: 'Todos' },
  ];
  protected readonly assignees: { value: AssigneeFilter; label: string }[] = [
    { value: 'ANY', label: 'Todos' }, { value: 'MINE', label: 'Míos' }, { value: 'UNASSIGNED', label: 'Sin asignar' },
  ];
  protected readonly view = signal<TicketView>('OPEN');
  protected readonly assignee = signal<AssigneeFilter>('ANY');
  protected readonly query = signal('');
  protected readonly tickets = signal<TicketSummary[]>([]);
  /** El compromiso más urgente de cada ticket abierto (solo para el equipo). */
  protected readonly urgent = signal(new Map<string, SlaClock>());
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  private searchTimer?: ReturnType<typeof setTimeout>;
  private liveTimer?: ReturnType<typeof setTimeout>;
  private readonly refresh = signal(0);
  private request = 0;

  constructor() {
    // Si alguien cambia un ticket, la lista se actualiza sola (agrupando ráfagas de eventos).
    inject(Realtime).listen(() => {
      clearTimeout(this.liveTimer);
      this.liveTimer = setTimeout(() => this.refresh.update((n) => n + 1), 300);
    });
    effect(() => {
      this.refresh();
      const org = this.session.organization();
      const view = this.view();
      const assignee = this.assignee();
      const query = this.query();
      if (org) void this.load(org.organizationId, view, assignee, query);
    });
  }

  protected key(number: number): string {
    return ticketKey(this.session.organization()?.slug, number);
  }

  protected ago(iso: string): string {
    return timeAgo(iso);
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
