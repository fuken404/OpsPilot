import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { apiError } from '../core/api-error';
import { PriorityChip } from '../core/chips';
import { formatMinutes, SlaPolicy } from '../core/models';
import { Session } from '../core/session';
import { SlaApi } from '../core/sla-api';

/** En pantalla se editan horas; la API guarda minutos. */
interface Row {
  policy: SlaPolicy;
  responseHours: number;
  resolutionHours: number;
  businessHours: boolean;
}

@Component({
  selector: 'app-sla-policies',
  imports: [FormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule, PriorityChip],
  template: `
    <header class="page-head">
      <div>
        <p class="eyebrow">{{ session.organization()?.name }}</p>
        <h1>Acuerdos de servicio</h1>
        <p class="lede">
          Tiempo máximo para la primera respuesta y para resolver, según la prioridad. En horario hábil cuentan de lunes
          a viernes de 8:00 a 18:00 (hora de Colombia), sin festivos, y esperar al cliente pausa la resolución.
        </p>
      </div>
    </header>
    <p class="note"><span class="icon" aria-hidden="true">info</span>Los cambios aplican a los tickets nuevos y a los que cambien de prioridad.</p>
    @if (message(); as text) {
      <p class="message" role="status">{{ text }}</p>
    }
    <div class="grid">
      @for (row of rows(); track row.policy.priority) {
        <form class="card card-pad policy" (ngSubmit)="save(row)">
          <app-priority-chip [priority]="row.policy.priority" />
          @if (editable()) {
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Primera respuesta (horas)</mat-label>
              <input matInput type="number" min="0.25" step="0.25" [name]="'r' + row.policy.priority"
                     [(ngModel)]="row.responseHours" required />
            </mat-form-field>
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Resolución (horas)</mat-label>
              <input matInput type="number" min="0.25" step="0.25" [name]="'s' + row.policy.priority"
                     [(ngModel)]="row.resolutionHours" required />
            </mat-form-field>
            <mat-checkbox [name]="'b' + row.policy.priority" [(ngModel)]="row.businessHours">
              Solo horario laboral
            </mat-checkbox>
            <button mat-stroked-button type="submit" [disabled]="saving()">Guardar</button>
          } @else {
            <p>Primera respuesta: <strong>{{ format(row.policy.responseMinutes) }}</strong></p>
            <p>Resolución: <strong>{{ format(row.policy.resolutionMinutes) }}</strong></p>
            <p class="muted">{{ row.policy.businessHours ? 'En horario laboral' : 'Las 24 horas' }}</p>
          }
        </form>
      }
    </div>
  `,
  styles: `
    .note { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; font-size: 13.5px; color: var(--muted); }
    .note .icon { font-size: 18px; }
    .message { color: var(--accent); font-weight: 500; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; }
    .policy { display: flex; flex-direction: column; gap: 12px; }
    .policy p { margin: 0; }
    .policy p strong { font-family: var(--font-mono); font-weight: 500; }
    button { align-self: flex-start; }
  `,
})
export class SlaPolicies {
  protected readonly session = inject(Session);
  private readonly api = inject(SlaApi);
  protected readonly rows = signal<Row[]>([]);
  protected readonly saving = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly editable = () => this.session.can('ADMIN');

  constructor() {
    effect(() => {
      const org = this.session.organization();
      if (org) void this.load(org.organizationId);
    });
  }

  protected format(minutes: number): string {
    return formatMinutes(minutes, false);
  }

  private async load(org: string): Promise<void> {
    try {
      const policies = await this.api.policies(org);
      this.rows.set(policies.map((policy) => ({
        policy, responseHours: policy.responseMinutes / 60, resolutionHours: policy.resolutionMinutes / 60,
        businessHours: policy.businessHours,
      })));
    } catch (error) {
      this.message.set(apiError(error, 'No se pudieron cargar las políticas.'));
    }
  }

  protected async save(row: Row): Promise<void> {
    const org = this.session.organization()!.organizationId;
    this.saving.set(true);
    try {
      await this.api.updatePolicy(org, row.policy.priority, {
        responseMinutes: Math.round(row.responseHours * 60),
        resolutionMinutes: Math.round(row.resolutionHours * 60),
        businessHours: row.businessHours,
      });
      this.message.set('Política guardada.');
      await this.load(org);
    } catch (error) {
      this.message.set(apiError(error));
    } finally {
      this.saving.set(false);
    }
  }
}
