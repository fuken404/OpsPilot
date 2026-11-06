import { Component, computed, input } from '@angular/core';
import { PRIORITY_LABELS, STATUS_LABELS, TicketPriority, TicketStatus } from './models';

/** Estado como píldora con punto: el texto siempre acompaña al color. */
@Component({
  selector: 'app-status-chip',
  template: `<span class="pill" [class]="status().toLowerCase()"><i aria-hidden="true"></i>{{ label() }}</span>`,
  styles: `
    .pill { display: inline-flex; align-items: center; gap: 6px; padding: 3px 10px 3px 8px; border-radius: 999px;
      font: 500 12.5px/1.4 var(--font-ui); white-space: nowrap; background: var(--bg); color: var(--fg); }
    i { width: 7px; height: 7px; border-radius: 50%; background: var(--dot); }
    .new { --bg: #e3ebff; --fg: #1e3a8a; --dot: #3b6ef5; }
    .in_progress { --bg: #ece8ff; --fg: #43218c; --dot: #7148e8; }
    .waiting_customer { --bg: #fdf0c8; --fg: #713f12; --dot: #d58a06; }
    .resolved { --bg: #dcf6e3; --fg: #14532d; --dot: #1f9d4c; }
    .closed, .cancelled { --bg: #ecebe5; --fg: #57534e; --dot: #a8a29e; }
  `,
})
export class StatusChip {
  readonly status = input.required<TicketStatus>();
  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
}

const LEVELS: Record<TicketPriority, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };

/** Prioridad como señal de cobertura: cuantas más barras llenas, más urgente. */
@Component({
  selector: 'app-priority-chip',
  template: `
    <span class="priority" [class]="priority().toLowerCase()">
      <span class="bars" aria-hidden="true">
        @for (bar of [1, 2, 3, 4]; track bar) {
          <i [class.on]="bar <= level()" [style.height.px]="3 + bar * 3"></i>
        }
      </span>
      @if (!compact()) {
        {{ label() }}
      } @else {
        <span class="sr-only">Prioridad {{ label() }}</span>
      }
    </span>
  `,
  styles: `
    .priority { display: inline-flex; align-items: center; gap: 7px; font: 500 13px/1 var(--font-ui); white-space: nowrap; color: var(--text); }
    .bars { display: inline-flex; align-items: flex-end; gap: 2px; height: 15px; }
    i { width: 4px; border-radius: 1.5px; background: var(--line-strong); }
    i.on { background: var(--c); }
    .critical { --c: #d32f2f; color: #b42318; }
    .high { --c: #e8590c; }
    .medium { --c: var(--ink); }
    .low { --c: #8d8f98; }
  `,
})
export class PriorityChip {
  readonly priority = input.required<TicketPriority>();
  readonly compact = input(false);
  protected readonly level = computed(() => LEVELS[this.priority()]);
  protected readonly label = computed(() => PRIORITY_LABELS[this.priority()]);
}

const AVATAR_TINTS = ['#dbeafe', '#ede9fe', '#fce7f3', '#dcfce7', '#fef3c7', '#e0f2fe', '#f3e8ff', '#ffedd5'];

/** Iniciales con un fondo estable por persona (mismo nombre, mismo color). */
@Component({
  selector: 'app-avatar',
  template: `
    @if (name(); as n) {
      <span class="avatar" [style.background]="tint()" [style.width.px]="size()" [style.height.px]="size()"
            [style.font-size.px]="size() * 0.4" [attr.title]="n">{{ initials() }}</span>
    } @else {
      <span class="avatar empty" [style.width.px]="size()" [style.height.px]="size()" title="Sin asignar">
        <span class="icon" [style.font-size.px]="size() * 0.55">person</span>
      </span>
    }
  `,
  styles: `
    :host { display: inline-flex; }
    .avatar { display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; flex: none;
      font-family: var(--font-ui); font-weight: 600; color: var(--ink); letter-spacing: .02em; }
    .empty { border: 1.5px dashed var(--line-strong); color: var(--muted); background: transparent; }
  `,
})
export class Avatar {
  readonly name = input<string | null | undefined>(null);
  readonly size = input(28);
  protected readonly initials = computed(() =>
    (this.name() ?? '').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join(''));
  protected readonly tint = computed(() => {
    const name = this.name() ?? '';
    let hash = 0;
    for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
    return AVATAR_TINTS[hash % AVATAR_TINTS.length];
  });
}

/** Clave legible del ticket: ACME-12. */
export function ticketKey(slug: string | undefined, number: number): string {
  return `${(slug ?? 'T').toUpperCase()}-${number}`;
}

/** "hace 5 min", "hace 3 h", "hace 2 d". */
export function timeAgo(iso: string, now = Date.now()): string {
  const minutes = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60_000));
  if (minutes < 1) return 'ahora';
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `hace ${hours} h`;
  return `hace ${Math.round(hours / 24)} d`;
}
