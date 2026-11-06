import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { apiError } from '../core/api-error';
import { CATEGORIES, CATEGORY_LABELS, PRIORITIES, PRIORITY_LABELS, TicketCategory, TicketPriority } from '../core/models';
import { Session } from '../core/session';
import { TicketsApi } from '../core/tickets-api';

@Component({
  selector: 'app-new-ticket',
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <nav class="crumbs"><a routerLink=".."><span class="icon" aria-hidden="true">arrow_back</span>Tickets</a></nav>
    <header class="page-head">
      <div>
        <p class="eyebrow">{{ session.organization()?.name }}</p>
        <h1>Reportar un problema</h1>
        <p class="lede">Mientras más claro, más rápido. Cuenta desde cuándo pasa, a quién afecta y qué mensaje aparece.</p>
      </div>
    </header>

    <form class="card card-pad" #form="ngForm" (ngSubmit)="form.valid && submit()">
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

      <fieldset>
        <legend>Categoría</legend>
        <div class="choices">
          @for (c of categories; track c) {
            <label class="choice" [class.on]="category === c">
              <input type="radio" name="category" [value]="c" [(ngModel)]="category" />
              <span class="icon" aria-hidden="true">{{ icons[c] }}</span>
              {{ categoryLabels[c] }}
            </label>
          }
        </div>
      </fieldset>

      @if (session.can('ADMIN', 'TECHNICIAN')) {
        <fieldset>
          <legend>Prioridad</legend>
          <div class="segmented" role="group" aria-label="Prioridad">
            @for (p of priorities; track p) {
              <button type="button" [attr.aria-pressed]="priority === p" (click)="priority = p">{{ priorityLabels[p] }}</button>
            }
          </div>
        </fieldset>
      } @else {
        <p class="note"><span class="icon" aria-hidden="true">info</span>El equipo de soporte define la prioridad al revisar tu ticket.</p>
      }

      @if (error(); as text) {
        <p class="error-text" role="alert">{{ text }}</p>
      }
      <div class="foot">
        <a mat-button routerLink="..">Cancelar</a>
        <button mat-flat-button type="submit" class="btn-icon" [disabled]="saving()"><span class="icon">send</span>Crear ticket</button>
      </div>
    </form>
  `,
  styles: `
    .crumbs { margin-bottom: 18px; font-size: 13px; }
    .crumbs a { display: inline-flex; align-items: center; gap: 4px; color: var(--muted); text-decoration: none; font-weight: 500; }
    .crumbs .icon { font-size: 18px; }
    form { display: flex; flex-direction: column; gap: 16px; max-width: 720px; box-sizing: border-box; }
    fieldset { border: 0; margin: 0; padding: 0; }
    legend { padding: 0; margin-bottom: 8px; font-size: 13px; font-weight: 600; }
    .choices { display: grid; grid-template-columns: repeat(auto-fill, minmax(118px, 1fr)); gap: 8px; }
    .choice { position: relative; display: flex; flex-direction: column; align-items: flex-start; gap: 8px; padding: 12px;
      border: 1px solid var(--line-strong); border-radius: 12px; cursor: pointer; font-size: 14px; font-weight: 500; }
    .choice input { position: absolute; opacity: 0; pointer-events: none; }
    .choice .icon { font-size: 22px; color: var(--muted); }
    .choice:hover { border-color: var(--ink); }
    .choice.on { border-color: var(--ink); background: var(--ink); color: #fff; }
    .choice.on .icon { color: var(--lime); }
    .choice:focus-within { outline: 2px solid var(--lime-deep); outline-offset: 2px; }
    .note { display: flex; align-items: center; gap: 8px; margin: 0; padding: 10px 12px; border-radius: 10px; background: #f1f0ea; font-size: 13.5px; color: var(--muted); }
    .foot { display: flex; justify-content: flex-end; gap: 8px; padding-top: 4px; }
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
  protected readonly icons: Record<TicketCategory, string> = {
    HARDWARE: 'computer', SOFTWARE: 'apps', NETWORK: 'wifi', ACCESS: 'key', OTHER: 'help',
  };
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
