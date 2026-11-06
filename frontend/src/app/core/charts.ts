import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, ElementRef, inject, input, OnDestroy, signal } from '@angular/core';

/*
 * Gráficos pequeños hechos a mano: no justifican una librería. Colores validados para daltonismo
 * (azul y naranja de la paleta de referencia); el texto siempre va en tinta neutra, nunca del
 * color de la serie.
 */

export interface BarItem {
  label: string;
  value: number;
  note?: string;
}

/** Barras horizontales de una sola serie: un solo tono, valor en la punta. */
@Component({
  selector: 'app-bar-list',
  template: `
    <ul class="bars" [attr.aria-label]="label()">
      @for (item of items(); track item.label) {
        <li>
          <span class="name">{{ item.label }}</span>
          <span class="track">
            <span class="bar" [style.width.%]="share(item.value)" [class.zero]="item.value === 0"></span>
            <span class="value">{{ item.value }}</span>
          </span>
          @if (item.note) {
            <span class="note">{{ item.note }}</span>
          }
        </li>
      }
    </ul>
  `,
  styles: `
    .bars { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
    li { display: grid; grid-template-columns: minmax(90px, 34%) 1fr; column-gap: 12px; align-items: center; }
    .name { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .track { display: flex; align-items: center; gap: 8px; min-width: 0; }
    .bar { height: 12px; border-radius: 0 4px 4px 0; background: var(--accent); min-width: 2px; max-width: calc(100% - 40px); }
    .bar.zero { background: var(--line-strong); }
    .value { font: var(--mat-sys-label-large); font-variant-numeric: tabular-nums; color: var(--mat-sys-on-surface); }
    .note { grid-column: 2; font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }
  `,
})
export class BarList {
  readonly items = input.required<BarItem[]>();
  readonly label = input('');
  private readonly max = computed(() => Math.max(1, ...this.items().map((i) => i.value)));

  protected share(value: number): number {
    return (value / this.max()) * 100;
  }
}

/** Porcentaje contra una meta: la barra cambia de tono con la gravedad; el texto lo dice igual. */
@Component({
  selector: 'app-meter',
  imports: [DecimalPipe],
  template: `
    <div class="meter">
      <div class="head">
        <span class="label">{{ label() }}</span>
        @if (value() !== null) {
          <strong>{{ value() | number: '1.0-1' }} %</strong>
        } @else {
          <span class="empty">Sin datos aún</span>
        }
      </div>
      <div class="track" role="meter" aria-valuemin="0" aria-valuemax="100" [attr.aria-valuenow]="value()"
           [attr.aria-label]="label()">
        <div class="fill" [class]="level()" [style.width.%]="value() ?? 0"></div>
      </div>
      <p class="note">{{ note() }}</p>
    </div>
  `,
  styles: `
    .head { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; }
    .label { font: var(--mat-sys-body-medium); }
    strong { font: var(--mat-sys-title-large); }
    .empty { color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-small); }
    .track { height: 8px; border-radius: 4px; background: var(--accent-soft); margin: 6px 0 4px; overflow: hidden; }
    .fill { height: 100%; border-radius: 4px; background: var(--accent); }
    .fill.warning { background: #fab219; }
    .fill.critical { background: #d03b3b; }
    .note { margin: 0; font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }
  `,
})
export class Meter {
  readonly label = input.required<string>();
  readonly value = input.required<number | null>();
  readonly note = input('');
  protected readonly level = computed(() => {
    const value = this.value() ?? 100;
    return value >= 90 ? 'ok' : value >= 75 ? 'warning' : 'critical';
  });
}

export interface TrendPoint {
  date: string;
  created: number;
  resolved: number;
}

const SERIES = [
  { key: 'created', label: 'Creados', color: '#2a78d6' },
  { key: 'resolved', label: 'Resueltos', color: '#eb6834' },
] as const;

/**
 * Dos líneas en un mismo eje (creados y resueltos por día). La guía vertical sigue al puntero o a
 * las flechas del teclado y el tooltip lista ambas series en ese día.
 */
@Component({
  selector: 'app-trend-chart',
  imports: [DatePipe],
  template: `
    <div class="legend">
      @for (s of series; track s.key) {
        <span><i [style.background]="s.color"></i>{{ s.label }}</span>
      }
    </div>
    <div class="plot">
      <svg [attr.viewBox]="'0 0 ' + width() + ' ' + height" [attr.width]="width()" [attr.height]="height" tabindex="0"
           role="img" [attr.aria-label]="summary()" (pointermove)="point($event)" (pointerleave)="active.set(null)"
           (keydown)="key($event)" (blur)="active.set(null)">
        @for (tick of ticks(); track tick) {
          <line class="grid" [attr.x1]="left" [attr.x2]="width() - right" [attr.y1]="y(tick)" [attr.y2]="y(tick)" />
          <text class="axis" [attr.x]="left - 8" [attr.y]="y(tick) + 4" text-anchor="end">{{ tick }}</text>
        }
        <line class="baseline" [attr.x1]="left" [attr.x2]="width() - right" [attr.y1]="y(0)" [attr.y2]="y(0)" />
        @for (i of labelIndexes(); track i) {
          <text class="axis" [attr.x]="x(i)" [attr.y]="height - 6" text-anchor="middle">
            {{ points()[i].date | date: 'd MMM' }}
          </text>
        }
        @let i = active();
        @if (i !== null) {
          <line class="cross" [attr.x1]="x(i)" [attr.x2]="x(i)" [attr.y1]="top" [attr.y2]="y(0)" />
        }
        @for (s of series; track s.key) {
          <path [attr.d]="path(s.key)" [attr.stroke]="s.color" />
          <circle [attr.cx]="x(points().length - 1)" [attr.cy]="y(last()[s.key])" r="4" [attr.fill]="s.color" />
          @if (i !== null) {
            <circle [attr.cx]="x(i)" [attr.cy]="y(points()[i][s.key])" r="4" [attr.fill]="s.color" class="ring" />
          }
        }
      </svg>
      @if (i !== null) {
        <div class="tooltip" [style.left.px]="x(i)" [class.flip]="x(i) > width() * 0.6">
          <p class="date">{{ points()[i].date | date: 'EEEE d MMM' }}</p>
          @for (s of series; track s.key) {
            <p><i [style.background]="s.color"></i><strong>{{ points()[i][s.key] }}</strong> {{ s.label.toLowerCase() }}</p>
          }
        </div>
      }
    </div>
    <details>
      <summary>Ver como tabla</summary>
      <table>
        <thead><tr><th>Día</th><th>Creados</th><th>Resueltos</th></tr></thead>
        <tbody>
          @for (p of points(); track p.date) {
            <tr><td>{{ p.date | date: 'EEE d MMM' }}</td><td>{{ p.created }}</td><td>{{ p.resolved }}</td></tr>
          }
        </tbody>
      </table>
    </details>
  `,
  styles: `
    :host { display: block; }
    .legend { display: flex; gap: 16px; font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); margin-bottom: 8px; }
    .legend span { display: inline-flex; align-items: center; gap: 6px; }
    .legend i { width: 14px; height: 2px; border-radius: 1px; }
    .plot { position: relative; }
    svg { display: block; max-width: 100%; overflow: visible; outline: none; touch-action: pan-y; }
    svg:focus-visible { outline: 2px solid var(--mat-sys-primary); outline-offset: 4px; border-radius: 4px; }
    .grid { stroke: #e1e0d9; stroke-width: 1; }
    .baseline { stroke: #c3c2b7; stroke-width: 1; }
    .cross { stroke: #898781; stroke-width: 1; }
    .axis { fill: #898781; font: 11px Roboto, sans-serif; font-variant-numeric: tabular-nums; }
    path { fill: none; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    circle { stroke: var(--mat-sys-surface); stroke-width: 2; }
    .tooltip { position: absolute; top: 0; transform: translateX(12px); pointer-events: none; min-width: 130px;
      background: var(--mat-sys-surface-container-highest); color: var(--mat-sys-on-surface); border-radius: 8px;
      padding: 8px 10px; box-shadow: 0 2px 8px rgb(0 0 0 / 12%); font: var(--mat-sys-body-small); }
    .tooltip.flip { transform: translateX(calc(-100% - 12px)); }
    .tooltip p { margin: 2px 0; display: flex; align-items: center; gap: 6px; }
    .tooltip .date { color: var(--mat-sys-on-surface-variant); }
    .tooltip .date::first-letter { text-transform: uppercase; }
    .tooltip i { width: 12px; height: 2px; border-radius: 1px; }
    .tooltip strong { font: var(--mat-sys-label-large); }
    details { margin-top: 8px; font: var(--mat-sys-body-small); }
    summary { cursor: pointer; color: var(--mat-sys-primary); }
    table { border-collapse: collapse; margin-top: 8px; font-variant-numeric: tabular-nums; }
    th, td { padding: 4px 12px 4px 0; text-align: left; border-bottom: 1px solid var(--mat-sys-outline-variant); }
  `,
})
export class TrendChart implements OnDestroy {
  readonly points = input.required<TrendPoint[]>();
  protected readonly series = SERIES;
  protected readonly height = 200;
  protected readonly top = 12;
  protected readonly left = 32;
  protected readonly right = 12;
  private readonly bottom = 26;
  protected readonly width = signal(600);
  protected readonly active = signal<number | null>(null);
  private readonly observer: ResizeObserver;

  protected readonly last = computed(() => this.points()[this.points().length - 1]);
  private readonly max = computed(() => {
    const top = Math.max(1, ...this.points().flatMap((p) => [p.created, p.resolved]));
    const step = Math.max(1, Math.ceil(top / 4));
    return step * 4;
  });
  protected readonly ticks = computed(() => [0, 1, 2, 3, 4].map((i) => (this.max() / 4) * i));
  protected readonly labelIndexes = computed(() => {
    const n = this.points().length;
    return n < 2 ? [0] : [0, Math.floor((n - 1) / 2), n - 1];
  });
  protected readonly summary = computed(() => {
    const total = (key: 'created' | 'resolved') => this.points().reduce((sum, p) => sum + p[key], 0);
    return `Últimos ${this.points().length} días: ${total('created')} tickets creados y ${total('resolved')} resueltos.`;
  });

  constructor() {
    const host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    this.observer = new ResizeObserver(([entry]) => this.width.set(Math.max(280, Math.floor(entry.contentRect.width))));
    this.observer.observe(host);
  }

  ngOnDestroy(): void {
    this.observer.disconnect();
  }

  protected x(i: number): number {
    const n = Math.max(1, this.points().length - 1);
    return this.left + (i / n) * (this.width() - this.left - this.right);
  }

  protected y(value: number): number {
    return this.top + (1 - value / this.max()) * (this.height - this.top - this.bottom);
  }

  protected path(key: 'created' | 'resolved'): string {
    return this.points().map((p, i) => `${i ? 'L' : 'M'}${this.x(i)},${this.y(p[key])}`).join(' ');
  }

  /** La guía salta al día más cercano: nadie tiene que apuntarle a una línea de 2 px. */
  protected point(event: PointerEvent): void {
    const svg = event.currentTarget as SVGElement;
    const offset = event.clientX - svg.getBoundingClientRect().left;
    const n = this.points().length - 1;
    const ratio = (offset - this.left) / (this.width() - this.left - this.right);
    this.active.set(Math.min(n, Math.max(0, Math.round(ratio * n))));
  }

  protected key(event: KeyboardEvent): void {
    const n = this.points().length - 1;
    const current = this.active() ?? n;
    if (event.key === 'ArrowLeft') this.active.set(Math.max(0, current - 1));
    else if (event.key === 'ArrowRight') this.active.set(Math.min(n, current + 1));
    else return;
    event.preventDefault();
  }
}
