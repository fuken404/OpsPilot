import { Component, computed, input } from '@angular/core';
import { PRIORITY_LABELS, STATUS_LABELS, TicketPriority, TicketStatus } from './models';

/** Estado como píldora con punto: el texto siempre acompaña al color. */
@Component({
  selector: 'app-status-chip',
  template: `<span class="pill" [class]="status().toLowerCase()"><i aria-hidden="true"></i>{{ label() }}</span>`,
  styles: `
    .pill { display: inline-flex; align-items: center; gap: 6px; padding: 3px 10px 3px 8px; border-radius: 999px;
      font: 700 12.5px/1.4 var(--font-ui); white-space: nowrap; background: var(--bg); color: var(--fg); }
    i { width: 7px; height: 7px; border-radius: 50%; background: var(--dot); }
    .new { --bg: #e3ebff; --fg: #1e3a8a; --dot: #3b6ef5; }
    .in_progress { --bg: #ece8ff; --fg: #43218c; --dot: #7148e8; }
    .waiting_customer { --bg: #fdf0c8; --fg: #713f12; --dot: #d58a06; }
    .resolved { --bg: #dcf6e3; --fg: #14532d; --dot: #1f9d4c; }
    .closed, .cancelled { --bg: #f4f4f5; --fg: #52525b; --dot: #a1a1aa; }
  `,
})
export class StatusChip {
  readonly status = input.required<TicketStatus>();
  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
}

/** Prioridad como píldora: el color sube con la urgencia y el texto siempre la nombra. */
@Component({
  selector: 'app-priority-chip',
  template: `
    <span class="priority" [class]="priority().toLowerCase()" [class.compact]="compact()">
      @if (priority() === 'CRITICAL') { <span class="icon" aria-hidden="true">priority_high</span> }
      {{ label() }}
    </span>
  `,
  styles: `
    .priority { display: inline-flex; align-items: center; gap: 2px; padding: 3px 10px; border-radius: 999px;
      font: 700 12.5px/1.4 var(--font-ui); white-space: nowrap; }
    .icon { font-size: 15px; margin-left: -4px; }
    .critical { background: #ef4444; color: #fff; }
    .high { background: #fbbf24; color: #3b2600; }
    .medium { background: #ddd6fe; color: #4c1d95; }
    .low { background: #f4f4f5; color: #52525b; }
  `,
})
export class PriorityChip {
  readonly priority = input.required<TicketPriority>();
  readonly compact = input(false);
  protected readonly label = computed(() => PRIORITY_LABELS[this.priority()]);
}

const AVATAR_TINTS = ['#ddd6fe', '#fbcfe8', '#bae6fd', '#bbf7d0', '#fde68a', '#c7d2fe', '#f5d0fe', '#fed7aa'];

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
      font-family: var(--font-ui); font-weight: 800; color: var(--ink); letter-spacing: .02em; }
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
