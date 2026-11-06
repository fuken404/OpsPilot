import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { apiError } from '../core/api-error';
import { CATEGORIES, CATEGORY_LABELS, PRIORITIES, PRIORITY_LABELS, TicketCategory, TicketPriority } from '../core/models';
import { Session } from '../core/session';
import { TicketsApi } from '../core/tickets-api';

@Component({
  selector: 'app-new-ticket',
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <a mat-button routerLink="..">← Tickets</a>
    <h1>Nuevo ticket</h1>
    <form #form="ngForm" (ngSubmit)="form.valid && submit()">
      <mat-form-field>
        <mat-label>Título</mat-label>
        <input matInput name="title" [(ngModel)]="title" required minlength="3" maxlength="200"
               placeholder="Ej.: No puedo entrar al ERP" />
      </mat-form-field>
      <mat-form-field>
        <mat-label>Qué pasa</mat-label>
        <textarea matInput name="description" [(ngModel)]="description" required maxlength="10000" rows="6"
                  placeholder="Desde cuándo, a quién afecta y qué mensaje aparece."></textarea>
      </mat-form-field>
      <div class="pair">
        <mat-form-field>
          <mat-label>Categoría</mat-label>
          <mat-select name="category" [(ngModel)]="category" required>
            @for (c of categories; track c) {
              <mat-option [value]="c">{{ categoryLabels[c] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (session.can('ADMIN', 'TECHNICIAN')) {
          <mat-form-field>
            <mat-label>Prioridad</mat-label>
            <mat-select name="priority" [(ngModel)]="priority">
              @for (p of priorities; track p) {
                <mat-option [value]="p">{{ priorityLabels[p] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
      </div>
      @if (!session.can('ADMIN', 'TECHNICIAN')) {
        <p class="muted">El equipo de soporte asigna la prioridad al revisar tu ticket.</p>
      }
      @if (error(); as text) {
        <p class="error" role="alert">{{ text }}</p>
      }
      <button mat-flat-button type="submit" [disabled]="saving()">Crear ticket</button>
    </form>
  `,
  styles: `
    h1 { font: var(--mat-sys-headline-medium); margin: 8px 0 16px; }
    form { display: flex; flex-direction: column; max-width: 640px; }
    .pair { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
    .muted { color: var(--mat-sys-on-surface-variant); margin: 0 0 16px; }
    .error { color: var(--mat-sys-error); }
    button { align-self: flex-start; }
  `,
})
export class NewTicket {
  protected readonly session = inject(Session);
  private readonly api = inject(TicketsApi);
  private readonly router = inject(Router);
  protected readonly categories = CATEGORIES;
  protected readonly priorities = PRIORITIES;
  protected readonly categoryLabels = CATEGORY_LABELS;
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected title = '';
  protected description = '';
  protected category: TicketCategory = 'SOFTWARE';
  protected priority: TicketPriority = 'MEDIUM';

  protected async submit(): Promise<void> {
    const org = this.session.organization();
    if (!org) return;
    this.saving.set(true);
    try {
      const staff = this.session.can('ADMIN', 'TECHNICIAN');
      const ticket = await this.api.create(org.organizationId, {
        title: this.title, description: this.description, category: this.category,
        priority: staff ? this.priority : null,
      });
      await this.router.navigate(['/app/tickets', ticket.id]);
    } catch (error) {
      this.error.set(apiError(error, 'No se pudo crear el ticket.'));
    } finally {
      this.saving.set(false);
    }
  }
}
