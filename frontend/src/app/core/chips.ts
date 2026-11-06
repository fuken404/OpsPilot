import { Component, computed, input } from '@angular/core';
import { PRIORITY_LABELS, STATUS_LABELS, TicketPriority, TicketStatus } from './models';

@Component({
  selector: 'app-status-chip',
  template: `<span class="chip" [class]="status().toLowerCase()">{{ label() }}</span>`,
  styles: `
    .chip { display: inline-block; padding: 2px 10px; border-radius: 999px; font: var(--mat-sys-label-medium); white-space: nowrap; }
    .new { background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); }
    .in_progress { background: var(--mat-sys-tertiary-container); color: var(--mat-sys-on-tertiary-container); }
    .waiting_customer { background: var(--mat-sys-surface-container-highest); color: var(--mat-sys-on-surface); }
    .resolved { background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .closed, .cancelled { background: transparent; color: var(--mat-sys-on-surface-variant); box-shadow: inset 0 0 0 1px var(--mat-sys-outline-variant); }
  `,
})
export class StatusChip {
  readonly status = input.required<TicketStatus>();
  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
}

@Component({
  selector: 'app-priority-chip',
  template: `<span class="priority" [class]="priority().toLowerCase()"><i></i>{{ label() }}</span>`,
  styles: `
    .priority { display: inline-flex; align-items: center; gap: 6px; font: var(--mat-sys-label-medium); white-space: nowrap; }
    i { width: 8px; height: 8px; border-radius: 50%; background: var(--mat-sys-outline); }
    .critical { color: var(--mat-sys-error); }
    .critical i { background: var(--mat-sys-error); }
    .high i { background: var(--mat-sys-tertiary); }
    .medium i { background: var(--mat-sys-primary); }
  `,
})
export class PriorityChip {
  readonly priority = input.required<TicketPriority>();
  protected readonly label = computed(() => PRIORITY_LABELS[this.priority()]);
}
