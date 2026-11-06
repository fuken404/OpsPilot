import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { apiError } from '../core/api-error';
import { Avatar, PriorityChip, StatusChip, ticketKey } from '../core/chips';
import { SlaApi } from '../core/sla-api';
import { SlaBadge } from '../core/sla-badge';
import {
  AuditEntry, CATEGORIES, CATEGORY_LABELS, formatBytes, Member, TicketAttachment, PRIORITIES, PRIORITY_LABELS, SLA_KIND_LABELS, SlaClock, SlaKind,
  STATUS_ACTIONS, STATUS_LABELS, TicketCategory, TicketDetail, TicketPriority, TicketStatus,
} from '../core/models';
import { Realtime } from '../core/realtime';
import { Session } from '../core/session';
import { TicketsApi } from '../core/tickets-api';

@Component({
  selector: 'app-ticket',
  imports: [FormsModule, DatePipe, RouterLink, MatButtonModule, MatFormFieldModule, MatSelectModule, StatusChip, PriorityChip, SlaBadge, Avatar],
  template: `
    <nav class="crumbs" aria-label="Ruta">
      <a routerLink="/app/tickets"><span class="icon" aria-hidden="true">arrow_back</span>Tickets</a>
      @if (ticket(); as t) {
        <span aria-hidden="true">/</span><span class="mono">{{ key(t.number) }}</span>
      }
    </nav>

    @if (notFound()) {
      <section class="card card-pad notfound">
        <span class="icon" aria-hidden="true">search_off</span>
        <h1>Ticket no encontrado</h1>
        <p class="muted">No existe o no tienes acceso a él.</p>
      </section>
    } @else if (ticket(); as t) {
      <header class="head">
        <div class="tags">
          <app-status-chip [status]="t.status" />
          <app-priority-chip [priority]="t.priority" />
          <span class="tag-soft">{{ categories[t.category] }}</span>
        </div>
        <h1>{{ t.title }}</h1>
        <p class="by">
          <app-avatar [name]="t.requester?.displayName" [size]="22" />
          <span><strong>{{ t.requester?.displayName }}</strong> lo reportó el {{ t.createdAt | date: "d 'de' MMMM, HH:mm" }}</span>
        </p>
      </header>

      @if (liveNote(); as note) {
        <p class="banner live" role="status"><span class="icon" aria-hidden="true">bolt</span>{{ note }}</p>
      }
      @if (message(); as text) {
        <p class="banner warn" role="alert">
          <span class="icon" aria-hidden="true">error</span>{{ text }}
          @if (stale()) {
            <button mat-button (click)="reload()">Recargar</button>
          }
        </p>
      }

      <div class="layout">
        <section class="main">
          <article class="card card-pad description">{{ t.description }}</article>

          <h2 class="section">Conversación <span class="count mono">{{ t.comments.length }}</span></h2>
          <ol class="thread">
            @for (c of t.comments; track c.id) {
              <li [class.internal]="c.internal">
                <app-avatar [name]="c.author?.displayName" [size]="32" />
                <div class="bubble">
                  <p class="author">
                    <strong>{{ c.author?.displayName }}</strong>
                    @if (c.internal) { <span class="tag-internal"><span class="icon">lock</span>Nota interna</span> }
                    <time [attr.datetime]="c.createdAt">{{ c.createdAt | date: "d MMM, HH:mm" }}</time>
                  </p>
                  <p class="body">{{ c.body }}</p>
                </div>
              </li>
            } @empty {
              <li class="empty muted">Aún no hay comentarios.</li>
            }
          </ol>

          @if (t.canComment) {
            <form class="composer card" [class.internal]="internal()" (ngSubmit)="comment()">
              @if (t.canCommentInternally) {
                <div class="tabs" role="tablist" aria-label="Tipo de mensaje">
                  <button type="button" role="tab" [attr.aria-selected]="!internal()" (click)="internal.set(false)">
                    <span class="icon">forum</span>Respuesta al cliente
                  </button>
                  <button type="button" role="tab" [attr.aria-selected]="internal()" (click)="internal.set(true)">
                    <span class="icon">lock</span>Nota interna
                  </button>
                </div>
              }
              <textarea name="reply" rows="3" maxlength="5000" [ngModel]="reply()" (ngModelChange)="reply.set($event)"
                        [attr.aria-label]="internal() ? 'Nota interna' : 'Respuesta'"
                        [placeholder]="internal() ? 'Solo lo ve el equipo…' : 'Escribe una respuesta…'"></textarea>
              <div class="composer-foot">
                <input #picker type="file" hidden (change)="upload(picker)"
                       accept=".png,.jpg,.jpeg,.gif,.webp,.pdf,.docx,.xlsx,.zip,.txt,.log,.csv" />
                <button mat-button type="button" class="btn-icon" [disabled]="busy()" (click)="picker.click()"
                        title="Imágenes, PDF, Office, ZIP o texto. Máximo 4 MB.">
                  <span class="icon">attach_file</span>Adjuntar{{ internal() ? ' (interno)' : '' }}
                </button>
                <button mat-flat-button type="submit" class="btn-icon" [disabled]="busy() || !reply().trim()">
                  <span class="icon">send</span>{{ internal() ? 'Guardar nota' : 'Enviar' }}
                </button>
              </div>
            </form>
          }

          @if (t.attachments.length > 0) {
            <h2 class="section">Adjuntos <span class="count mono">{{ t.attachments.length }}</span></h2>
            <ul class="files">
              @for (a of t.attachments; track a.id) {
                <li>
                  <button type="button" class="file card" (click)="download(a)" [attr.aria-label]="'Descargar ' + a.filename">
                    <span class="file-icon icon" aria-hidden="true">{{ fileIcon(a.contentType) }}</span>
                    <span class="file-text">
                      <strong>{{ a.filename }}</strong>
                      <small>{{ size(a.sizeBytes) }} · {{ a.uploader?.displayName }}{{ a.internal ? ' · interno' : '' }}</small>
                    </span>
                    <span class="icon dl" aria-hidden="true">download</span>
                  </button>
                </li>
              }
            </ul>
          }
        </section>

        <aside>
          @if (sla().length > 0) {
            <section class="card card-pad">
              <h2 class="card-title">Acuerdo de servicio</h2>
              @for (c of sla(); track c.kind) {
                <div class="clock" [class]="c.state.toLowerCase()">
                  <div class="clock-head">
                    <span>{{ slaLabels[c.kind] }}</span>
                    <app-sla-badge [clock]="c" />
                  </div>
                  <div class="meter" aria-hidden="true"><i [style.width.%]="slaPercent(c)"></i></div>
                  @if (c.dueAt && c.state !== 'MET' && c.state !== 'STOPPED') {
                    <small>Límite {{ c.dueAt | date: "EEE d MMM, HH:mm" }} · {{ c.businessHours ? 'horario hábil' : '24/7' }}</small>
                  }
                </div>
              }
            </section>
          }

          @if (t.nextStatuses.length > 0) {
            <section class="card card-pad">
              <h2 class="card-title">Mover a</h2>
              <div class="moves">
                @for (s of t.nextStatuses; track s) {
                  <button mat-stroked-button [disabled]="busy()" (click)="move(s)">{{ statusActions[s] }}</button>
                }
              </div>
            </section>
          }

          <section class="card card-pad">
            <h2 class="card-title">Responsable</h2>
            @if (t.canAssign) {
              <mat-form-field class="field">
                <mat-select [value]="t.assignee?.id ?? null" (valueChange)="assign($event)" [disabled]="busy()"
                            [canSelectNullableOptions]="true" aria-label="Responsable">
                  <mat-option [value]="null">Sin asignar</mat-option>
                  @for (m of assignable(); track m.userId) {
                    <mat-option [value]="m.userId">{{ m.displayName }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              @if (!t.assignee && session.can('TECHNICIAN')) {
                <button mat-flat-button class="take btn-icon" (click)="assign(session.me()!.id)" [disabled]="busy()">
                  <span class="icon">front_hand</span>Tomármelo
                </button>
              }
            } @else {
              <p class="person">
                <app-avatar [name]="t.assignee?.displayName" [size]="28" />{{ t.assignee?.displayName ?? 'Sin asignar' }}
              </p>
            }

            @if (t.canTriage) {
              <h2 class="card-title sub">Clasificación</h2>
              <mat-form-field class="field">
                <mat-label>Prioridad</mat-label>
                <mat-select [value]="t.priority" (valueChange)="triage($event, t.category)" [disabled]="busy()">
                  @for (p of priorities; track p) {
                    <mat-option [value]="p">{{ priorityLabels[p] }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field class="field">
                <mat-label>Categoría</mat-label>
                <mat-select [value]="t.category" (valueChange)="triage(t.priority, $event)" [disabled]="busy()">
                  @for (c of categoryList; track c) {
                    <mat-option [value]="c">{{ categories[c] }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
            }
          </section>

          @if (history().length > 0) {
            <section class="card card-pad">
              <h2 class="card-title">Historial</h2>
              <ol class="history">
                @for (e of history(); track e.id) {
                  <li [class.system]="!e.actor" [class.alert]="e.action === 'SLA_BREACHED'">
                    <p><strong>{{ e.actor?.displayName ?? 'Sistema' }}</strong> {{ describe(e) }}</p>
                    <time class="mono" [attr.datetime]="e.occurredAt">{{ e.occurredAt | date: "d MMM, HH:mm" }}</time>
                  </li>
                }
              </ol>
            </section>
          }
        </aside>
      </div>
    } @else {
      <p class="muted">Cargando…</p>
    }
  `,
  styles: `
    .crumbs { display: flex; align-items: center; gap: 8px; margin-bottom: 18px; font-size: 13px; color: var(--muted); }
    .crumbs a { display: inline-flex; align-items: center; gap: 4px; color: var(--muted); text-decoration: none; font-weight: 500; }
    .crumbs a:hover { color: var(--ink); }
    .crumbs .icon { font-size: 18px; }
    .notfound { text-align: center; padding: 48px 20px; }
    .notfound .icon { font-size: 40px; color: var(--line-strong); }

    .head { margin-bottom: 20px; }
    .tags { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
    .tag-soft { padding: 3px 10px; border-radius: 999px; background: #f4f4f5; font-size: 12.5px; font-weight: 700; color: #52525b; }
    h1 { margin: 12px 0 10px; font-size: clamp(24px, 3vw, 32px); line-height: 1.15; font-weight: 800; }
    .by { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 14px; color: var(--muted); }
    .by strong { color: var(--text); font-weight: 600; }

    .banner { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; padding: 10px 14px; border-radius: 10px; font-size: 14px; }
    .banner.live { background: #ede9fe; color: #4c1d95; }
    .banner.warn { background: #fdecec; color: #8a1c1c; }

    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 28px; align-items: start; }
    aside { display: flex; flex-direction: column; gap: 14px; position: sticky; top: 24px; }
    .description { white-space: pre-wrap; overflow-wrap: anywhere; font-size: 15.5px; line-height: 1.65; }
    .section { display: flex; align-items: center; gap: 8px; margin: 28px 0 14px; font-size: 18px; font-weight: 800; }
    .count { font-size: 12px; padding: 2px 9px; border-radius: 999px; background: var(--accent-soft); color: var(--accent-ink); font-weight: 700; }

    .thread { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 14px; }
    .thread li { display: flex; gap: 12px; align-items: flex-start; }
    .bubble { flex: 1; min-width: 0; padding: 12px 16px; border-radius: 6px 18px 18px 18px; background: var(--card); border: 1px solid var(--line); }
    .internal .bubble { background: #fffaeb; border-color: #f5dfa0; }
    .author { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 0 0 4px; font-size: 13.5px; }
    .author time { margin-left: auto; font-size: 12px; color: var(--muted); }
    .tag-internal { display: inline-flex; align-items: center; gap: 3px; padding: 1px 8px 1px 5px; border-radius: 999px;
      background: #fdf0c8; color: #713f12; font-size: 11.5px; font-weight: 500; }
    .tag-internal .icon { font-size: 13px; }
    .body { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.55; }
    .thread .empty { padding: 4px 0; }

    .composer { margin-top: 16px; overflow: hidden; }
    .composer.internal { border-color: #f5dfa0; }
    .tabs { display: flex; border-bottom: 1px solid var(--line); background: #fbfaff; }
    .tabs button { display: inline-flex; align-items: center; gap: 6px; border: 0; background: transparent; cursor: pointer;
      padding: 11px 16px; font: 500 13.5px var(--font-ui); color: var(--muted); border-bottom: 2px solid transparent; margin-bottom: -1px; }
    .tabs button .icon { font-size: 17px; }
    .tabs button[aria-selected='true'] { color: var(--accent-ink); border-bottom-color: var(--accent); font-weight: 700; }
    .composer.internal .tabs button[aria-selected='true'] { border-bottom-color: #d58a06; }
    textarea { display: block; width: 100%; box-sizing: border-box; border: 0; resize: vertical; min-height: 92px; padding: 14px 16px;
      font: 400 15px/1.55 var(--font-ui); color: var(--text); background: transparent; outline: none; }
    .composer.internal textarea { background: #fffdf5; }
    .composer-foot { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 8px 10px; border-top: 1px solid var(--line); }

    .files { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 10px; }
    .file { width: 100%; display: flex; align-items: center; gap: 12px; padding: 12px; text-align: left; cursor: pointer; font: inherit; color: inherit; }
    .file:hover { border-color: var(--line-strong); }
    .file-icon { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px; background: var(--accent-soft); color: var(--accent-ink); flex: none; }
    .file-text { display: flex; flex-direction: column; min-width: 0; flex: 1; }
    .file-text strong { font-size: 13.5px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .file-text small { font-size: 12px; color: var(--muted); }
    .dl { color: var(--muted); }

    .card-title.sub { margin-top: 18px; }
    .card-title + * { margin-top: 12px; }
    .clock + .clock { margin-top: 16px; }
    .clock-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 500; }
    .meter { height: 8px; border-radius: 4px; background: #f1effa; margin: 8px 0 6px; overflow: hidden; }
    .meter i { display: block; height: 100%; border-radius: 4px; background: var(--accent); }
    .at_risk .meter i { background: #d58a06; }
    .breached .meter i { background: var(--danger); }
    .met .meter i { background: #1f9d4c; }
    .paused .meter i { background: #a8a29e; }
    .clock small { font-size: 12px; color: var(--muted); }
    .moves { display: flex; flex-direction: column; gap: 8px; }
    .moves button { justify-content: flex-start; }
    .field { width: 100%; }
    .field + .field { margin-top: 10px; }
    .take { width: 100%; margin-top: 10px; }
    .person { display: flex; align-items: center; gap: 10px; margin: 12px 0 0; font-weight: 500; }

    .history { list-style: none; margin: 12px 0 0; padding: 0 0 0 14px; border-left: 2px solid var(--line); display: flex; flex-direction: column; gap: 12px; }
    .history li { position: relative; font-size: 13px; }
    .history li::before { content: ''; position: absolute; left: -20px; top: 5px; width: 10px; height: 10px; border-radius: 50%;
      background: var(--card); border: 2px solid var(--line-strong); }
    .history li.system::before { border-color: #d58a06; }
    .history li.alert::before { background: var(--danger); border-color: var(--danger); }
    .history p { margin: 0; line-height: 1.45; }
    .history time { font-size: 11.5px; color: var(--muted); }

    @media (max-width: 960px) {
      .layout { grid-template-columns: 1fr; }
      aside { position: static; }
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
  protected readonly liveNote = signal<string | null>(null);
  private loadedOrg: string | null = null;
  private noteTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    // Cambios de otras personas sobre este ticket: se recarga solo y lo avisa unos segundos.
    inject(Realtime).listen((event) => {
      const org = this.loadedOrg;
      if (!org || event.ticketId !== this.id() || this.busy()) return;
      void this.load(org, event.ticketId);
      if (event.type === 'sla') {
        this.flash(event.level === 'BREACHED' ? 'Venció un SLA de este ticket.' : 'Un SLA de este ticket está en riesgo.');
      } else if (event.actorId !== this.session.me()?.id) {
        this.flash('Alguien más actualizó este ticket.');
      }
    });

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

  private flash(note: string): void {
    this.liveNote.set(note);
    clearTimeout(this.noteTimer);
    this.noteTimer = setTimeout(() => this.liveNote.set(null), 4000);
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
    }
  }

  protected key(number: number): string {
    return ticketKey(this.session.organization()?.slug, number);
  }

  /** Cuánto del plazo ya se consumió, para la barra. */
  protected slaPercent(clock: SlaClock): number {
    if (clock.state === 'MET' || clock.state === 'STOPPED') return 100;
    const remaining = clock.remainingMinutes ?? 0;
    return Math.min(100, Math.max(2, (1 - remaining / clock.targetMinutes) * 100));
  }

  protected fileIcon(contentType: string): string {
    if (contentType.startsWith('image/')) return 'image';
    if (contentType === 'application/pdf') return 'picture_as_pdf';
    if (contentType.includes('spreadsheet') || contentType.startsWith('text/csv')) return 'table_chart';
    if (contentType.includes('word')) return 'description';
    if (contentType === 'application/zip') return 'folder_zip';
    return 'article';
  }

  protected size(bytes: number): string {
    return formatBytes(bytes);
  }

  protected async upload(picker: HTMLInputElement): Promise<void> {
    const file = picker.files?.[0];
    picker.value = '';
    if (!file) return;
    if (file.size > 4 * 1024 * 1024) {
      this.message.set('El archivo supera el máximo de 4 MB.');
      return;
    }
    await this.update((t) => this.api.attach(this.org(), t, file, this.internal()));
  }

  protected async download(attachment: TicketAttachment): Promise<void> {
    try {
      const blob = await this.api.download(this.org(), attachment.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = attachment.filename;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      this.message.set(apiError(error, 'No se pudo descargar el archivo.'));
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
      case 'ATTACHED':
        return `adjuntó ${d['filename']}${d['internal'] ? ' (interno)' : ''}`;
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
