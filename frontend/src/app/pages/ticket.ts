import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { apiError } from '../core/api-error';
import { PriorityChip, StatusChip } from '../core/chips';
import { SlaApi } from '../core/sla-api';
import { SlaBadge } from '../core/sla-badge';
import {
  AuditEntry, CATEGORIES, CATEGORY_LABELS, Member, PRIORITIES, PRIORITY_LABELS, SLA_KIND_LABELS, SlaClock, SlaKind,
  STATUS_ACTIONS, STATUS_LABELS, TicketCategory, TicketDetail, TicketPriority, TicketStatus,
} from '../core/models';
import { Session } from '../core/session';
import { TicketsApi } from '../core/tickets-api';

@Component({
  selector: 'app-ticket',
  imports: [FormsModule, DatePipe, RouterLink, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, StatusChip, PriorityChip, SlaBadge],
  template: `
    <a mat-button routerLink="/app/tickets">← Tickets</a>

    @if (notFound()) {
      <h1>Ticket no encontrado</h1>
      <p class="muted">No existe o no tienes acceso a él.</p>
    } @else if (ticket(); as t) {
      <header>
        <p class="number">#{{ t.number }} · {{ categories[t.category] }}</p>
        <h1>{{ t.title }}</h1>
        <div class="chips">
          <app-status-chip [status]="t.status" />
          <app-priority-chip [priority]="t.priority" />
        </div>
      </header>

      @if (message(); as text) {
        <p class="message" role="alert">
          {{ text }}
          @if (stale()) {
            <button mat-button (click)="reload()">Recargar</button>
          }
        </p>
      }

      <div class="layout">
        <section class="main">
          <p class="description">{{ t.description }}</p>
          <p class="muted small">
            Reportado por {{ t.requester?.displayName }} el {{ t.createdAt | date: "d MMM y, HH:mm" }}
          </p>

          <h2>Conversación</h2>
          @for (c of t.comments; track c.id) {
            <article class="comment" [class.internal]="c.internal">
              <p class="author">
                {{ c.author?.displayName }}
                @if (c.internal) { <span class="tag">Nota interna</span> }
                <time [attr.datetime]="c.createdAt">{{ c.createdAt | date: "d MMM, HH:mm" }}</time>
              </p>
              <p class="body">{{ c.body }}</p>
            </article>
          } @empty {
            <p class="muted">Aún no hay comentarios.</p>
          }

          @if (t.canComment) {
            <form class="reply" (ngSubmit)="comment()">
              <mat-form-field>
                <mat-label>{{ internal() ? 'Nota interna (el cliente no la ve)' : 'Responder' }}</mat-label>
                <textarea matInput name="reply" rows="3" maxlength="5000" [ngModel]="reply()"
                          (ngModelChange)="reply.set($event)"></textarea>
              </mat-form-field>
              <div class="actions">
                @if (t.canCommentInternally) {
                  <mat-checkbox name="internal" [ngModel]="internal()" (ngModelChange)="internal.set($event)">
                    Nota interna
                  </mat-checkbox>
                }
                <button mat-flat-button type="submit" [disabled]="busy() || !reply().trim()">Enviar</button>
              </div>
            </form>
          }
        </section>

        <aside>
          @if (sla().length > 0) {
            <h2>SLA</h2>
            <dl class="sla">
              @for (c of sla(); track c.kind) {
                <div>
                  <dt>{{ slaLabels[c.kind] }}</dt>
                  <dd>
                    <app-sla-badge [clock]="c" />
                    @if (c.dueAt && c.state !== 'MET' && c.state !== 'STOPPED') {
                      <small>Límite: {{ c.dueAt | date: "EEE d MMM, HH:mm" }}{{ c.businessHours ? '' : ' · 24/7' }}</small>
                    }
                  </dd>
                </div>
              }
            </dl>
          }

          @if (t.nextStatuses.length > 0) {
            <h2>Estado</h2>
            <div class="buttons">
              @for (s of t.nextStatuses; track s) {
                <button mat-stroked-button [disabled]="busy()" (click)="move(s)">{{ statusActions[s] }}</button>
              }
            </div>
          }

          <h2>Responsable</h2>
          @if (t.canAssign) {
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Asignado a</mat-label>
              <mat-select [value]="t.assignee?.id ?? null" (valueChange)="assign($event)" [disabled]="busy()"
                          [canSelectNullableOptions]="true">
                <mat-option [value]="null">Sin asignar</mat-option>
                @for (m of assignable(); track m.userId) {
                  <mat-option [value]="m.userId">{{ m.displayName }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            @if (!t.assignee && session.can('TECHNICIAN')) {
              <button mat-button (click)="assign(session.me()!.id)" [disabled]="busy()">Tomármelo</button>
            }
          } @else {
            <p>{{ t.assignee?.displayName ?? 'Sin asignar' }}</p>
          }

          @if (t.canTriage) {
            <h2>Clasificación</h2>
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Prioridad</mat-label>
              <mat-select [value]="t.priority" (valueChange)="triage($event, t.category)" [disabled]="busy()">
                @for (p of priorities; track p) {
                  <mat-option [value]="p">{{ priorityLabels[p] }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Categoría</mat-label>
              <mat-select [value]="t.category" (valueChange)="triage(t.priority, $event)" [disabled]="busy()">
                @for (c of categoryList; track c) {
                  <mat-option [value]="c">{{ categories[c] }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }

          @if (history().length > 0) {
            <h2>Historial</h2>
            <ol class="history">
              @for (e of history(); track e.id) {
                <li>
                  <strong>{{ e.actor?.displayName ?? 'Sistema' }}</strong> {{ describe(e) }}
                  <time [attr.datetime]="e.occurredAt">{{ e.occurredAt | date: "d MMM, HH:mm" }}</time>
                </li>
              }
            </ol>
          }
        </aside>
      </div>
    } @else {
      <p class="muted">Cargando…</p>
    }
  `,
  styles: `
    header { margin: 8px 0 16px; }
    .number { margin: 0; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-label-large); }
    h1 { font: var(--mat-sys-headline-medium); margin: 4px 0 8px; }
    h2 { font: var(--mat-sys-title-medium); margin: 24px 0 8px; }
    .chips { display: flex; gap: 12px; align-items: center; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 40px; }
    aside { display: flex; flex-direction: column; gap: 8px; }
    aside h2:first-child { margin-top: 0; }
    .description, .body { white-space: pre-wrap; overflow-wrap: anywhere; }
    .description { font: var(--mat-sys-body-large); margin-top: 0; }
    .comment { padding: 12px 16px; border-radius: 12px; background: var(--mat-sys-surface-container-low); margin-bottom: 12px; }
    .comment.internal { background: var(--mat-sys-tertiary-container); color: var(--mat-sys-on-tertiary-container); }
    .author { display: flex; gap: 8px; align-items: center; margin: 0 0 4px; font: var(--mat-sys-label-large); }
    .author time { margin-left: auto; font: var(--mat-sys-body-small); opacity: .75; }
    .tag { font: var(--mat-sys-label-small); padding: 1px 8px; border-radius: 999px; box-shadow: inset 0 0 0 1px currentColor; }
    .body { margin: 0; }
    .reply { display: flex; flex-direction: column; margin-top: 16px; }
    .actions { display: flex; align-items: center; justify-content: flex-end; gap: 16px; }
    .buttons { display: flex; flex-wrap: wrap; gap: 8px; }
    .sla { margin: 0; display: flex; flex-direction: column; gap: 10px; }
    .sla dt { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }
    .sla dd { margin: 2px 0 0; display: flex; flex-direction: column; align-items: flex-start; gap: 2px; }
    .sla small { color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-small); }
    .history { list-style: none; padding: 0; margin: 0; font: var(--mat-sys-body-small); }
    .history li { padding: 6px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .history time { display: block; color: var(--mat-sys-on-surface-variant); }
    .muted { color: var(--mat-sys-on-surface-variant); }
    .small { font: var(--mat-sys-body-small); }
    .message { display: flex; align-items: center; gap: 8px; color: var(--mat-sys-error); }
    @media (max-width: 800px) {
      .layout { grid-template-columns: 1fr; gap: 8px; }
    }
  `,
})
export class Ticket {
  protected readonly session = inject(Session);
  private readonly api = inject(TicketsApi);
  private readonly slaApi = inject(SlaApi);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly id = toSignal(inject(ActivatedRoute).paramMap.pipe(map((p) => p.get('id')!)));

  protected readonly categories = CATEGORY_LABELS;
  protected readonly categoryList = CATEGORIES;
  protected readonly priorities = PRIORITIES;
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly statusActions = STATUS_ACTIONS;
  protected readonly slaLabels = SLA_KIND_LABELS;
  protected readonly sla = signal<SlaClock[]>([]);

  protected readonly ticket = signal<TicketDetail | null>(null);
  protected readonly history = signal<AuditEntry[]>([]);
  protected readonly members = signal<Member[]>([]);
  protected readonly notFound = signal(false);
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly stale = signal(false);
  protected readonly assignable = computed(() =>
    this.members().filter((m) => !m.pending && m.userId && m.role !== 'CUSTOMER'));
  protected readonly reply = signal('');
  protected readonly internal = signal(false);
  private loadedOrg: string | null = null;

  constructor() {
    effect(() => {
      const org = this.session.organization()?.organizationId;
      const id = this.id();
      if (!org || !id) return;
      // El ticket pertenece a una organización: si cambias de organización, vuelves a la lista.
      if (this.loadedOrg && this.loadedOrg !== org) {
        void this.router.navigate(['/app/tickets']);
        return;
      }
      this.loadedOrg = org;
      void this.load(org, id);
    });
  }

  private org(): string {
    return this.session.organization()!.organizationId;
  }

  private async load(org: string, id: string): Promise<void> {
    try {
      const [ticket, sla] = await Promise.all([this.api.get(org, id), this.slaApi.ticket(org, id)]);
      this.ticket.set(ticket);
      this.sla.set(sla);
      this.notFound.set(false);
      if (this.session.can('ADMIN', 'TECHNICIAN')) {
        const [history, members] = await Promise.all([
          this.api.history(org, id),
          firstValueFrom(this.http.get<Member[]>(`${environment.apiUrl}/organizations/${org}/members`)),
        ]);
        this.history.set(history);
        this.members.set(members);
      }
    } catch {
      this.notFound.set(true);
    }
  }

  protected reload(): void {
    this.message.set(null);
    this.stale.set(false);
    void this.load(this.org(), this.id()!);
  }

  protected move(status: TicketStatus): Promise<void> {
    return this.update((t) => this.api.changeStatus(this.org(), t, status));
  }

  protected assign(assigneeId: string | null): Promise<void> {
    return this.update((t) => this.api.assign(this.org(), t, assigneeId));
  }

  protected triage(priority: TicketPriority, category: TicketCategory): Promise<void> {
    return this.update((t) => this.api.triage(this.org(), t, priority, category));
  }

  protected async comment(): Promise<void> {
    const body = this.reply().trim();
    if (!body) return;
    await this.update((t) => this.api.comment(this.org(), t, body, this.internal()));
    if (!this.message()) {
      this.reply.set('');
      this.internal.set(false);
    }
  }

  /** Un 409 significa que alguien cambió el ticket: se muestra el aviso y se ofrece recargar. */
  private async update(action: (ticket: TicketDetail) => Promise<TicketDetail>): Promise<void> {
    const current = this.ticket();
    if (!current) return;
    this.busy.set(true);
    this.message.set(null);
    this.stale.set(false);
    try {
      this.ticket.set(await action(current));
      this.sla.set(await this.slaApi.ticket(this.org(), current.id));
      if (this.session.can('ADMIN', 'TECHNICIAN')) {
        this.history.set(await this.api.history(this.org(), current.id));
      }
    } catch (error) {
      this.message.set(apiError(error));
      this.stale.set((error as { status?: number }).status === 409);
    } finally {
      this.busy.set(false);
    }
  }

  protected describe(entry: AuditEntry): string {
    const d = entry.details;
    switch (entry.action) {
      case 'CREATED':
        return 'creó el ticket';
      case 'STATUS_CHANGED': {
        const text = `cambió el estado de ${STATUS_LABELS[d['from'] as TicketStatus]} a ${STATUS_LABELS[d['to'] as TicketStatus]}`;
        return d['reason'] === 'CUSTOMER_REPLIED' ? `${text} al responder` : text;
      }
      case 'ASSIGNED':
        return d['to'] ? `asignó a ${this.nameOf(d['to'] as string)}` : 'quitó el responsable';
      case 'TRIAGED': {
        const changes: string[] = [];
        const priority = d['priority'] as { to: TicketPriority } | undefined;
        const category = d['category'] as { to: TicketCategory } | undefined;
        if (priority) changes.push(`prioridad ${PRIORITY_LABELS[priority.to]}`);
        if (category) changes.push(`categoría ${CATEGORY_LABELS[category.to]}`);
        return `cambió ${changes.join(' y ')}`;
      }
      case 'COMMENTED':
        return d['internal'] ? 'agregó una nota interna' : 'comentó';
      case 'SLA_WARNING':
        return `avisó: el SLA de ${SLA_KIND_LABELS[d['kind'] as SlaKind].toLowerCase()} lleva el 80 % del tiempo`;
      case 'SLA_BREACHED':
        return `escaló a los administradores: venció el SLA de ${SLA_KIND_LABELS[d['kind'] as SlaKind].toLowerCase()}`;
    }
  }

  private nameOf(userId: string): string {
    return this.members().find((m) => m.userId === userId)?.displayName ?? 'otra persona';
  }
}
