import { Component, computed, input } from '@angular/core';
import { formatMinutes, SlaClock } from './models';

/** Resume un reloj en una frase corta: "Vence en 2 h", "Vencido hace 15 min", "Cumplido". */
@Component({
  selector: 'app-sla-badge',
  template: `<span class="sla" [class]="clock().state.toLowerCase()" [attr.title]="title()">{{ text() }}</span>`,
  styles: `
    .sla { display: inline-block; margin-left: -8px; font: var(--mat-sys-label-medium); white-space: nowrap; padding: 2px 8px; border-radius: 6px; }
    .running { color: var(--mat-sys-on-surface-variant); }
    .at_risk { background: var(--mat-sys-tertiary-container); color: var(--mat-sys-on-tertiary-container); }
    .breached { background: var(--mat-sys-error); color: var(--mat-sys-on-error); }
    .paused, .met, .stopped { color: var(--mat-sys-on-surface-variant); }
  `,
})
export class SlaBadge {
  readonly clock = input.required<SlaClock>();

  protected readonly text = computed(() => {
    const c = this.clock();
    const amount = formatMinutes(c.remainingMinutes ?? 0, !c.businessHours) + (c.businessHours ? ' hábiles' : '');
    switch (c.state) {
      case 'BREACHED':
        return `Vencido hace ${amount}`;
      case 'AT_RISK':
      case 'RUNNING':
        return `Vence en ${amount}`;
      case 'PAUSED':
        return `En pausa · quedan ${amount}`;
      case 'MET':
        return c.late ? 'Cumplido tarde' : 'Cumplido';
      case 'STOPPED':
        return 'Detenido';
    }
  });

  protected readonly title = computed(() =>
    this.clock().businessHours ? 'Cuenta en horario laboral' : 'Cuenta las 24 horas');
}
