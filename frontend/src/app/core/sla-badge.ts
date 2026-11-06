import { Component, computed, input } from '@angular/core';
import { formatMinutes, SlaClock } from './models';

/** Resume un reloj: "2 h 15 min" que quedan, "vencido hace 15 min", "cumplido". */
@Component({
  selector: 'app-sla-badge',
  template: `
    <span class="sla" [class]="clock().state.toLowerCase()" [attr.title]="title()">
      <span class="icon" aria-hidden="true">{{ icon() }}</span>{{ text() }}
    </span>
  `,
  styles: `
    .sla { display: inline-flex; align-items: center; gap: 4px; padding: 3px 10px; border-radius: 999px; white-space: nowrap;
      font: 500 12px/1.3 var(--font-mono); font-variant-numeric: tabular-nums; color: var(--muted); background: #f4f4f5; }
    .icon { font-size: 15px; }
    .at_risk { background: #fdf0c8; color: #713f12; }
    .breached { background: #dc2626; color: #fff; }
    .met { background: #dcf6e3; color: #14532d; }
  `,
})
export class SlaBadge {
  readonly clock = input.required<SlaClock>();

  protected readonly icon = computed(() => ({
    RUNNING: 'timer', AT_RISK: 'warning', BREACHED: 'error', PAUSED: 'pause_circle', MET: 'check_circle', STOPPED: 'block',
  })[this.clock().state]);

  protected readonly text = computed(() => {
    const c = this.clock();
    const amount = formatMinutes(c.remainingMinutes ?? 0, !c.businessHours) + (c.businessHours ? ' háb.' : '');
    switch (c.state) {
      case 'BREACHED':
        return `vencido ${amount}`;
      case 'AT_RISK':
      case 'RUNNING':
        return amount;
      case 'PAUSED':
        return `pausa · ${amount}`;
      case 'MET':
        return c.late ? 'cumplido tarde' : 'cumplido';
      case 'STOPPED':
        return 'detenido';
    }
  });

  protected readonly title = computed(() => {
    const c = this.clock();
    const clock = c.businessHours ? 'cuenta en horario hábil' : 'cuenta las 24 horas';
    return c.state === 'BREACHED' ? `Vencido; ${clock}` : `Tiempo restante; ${clock}`;
  });
}
