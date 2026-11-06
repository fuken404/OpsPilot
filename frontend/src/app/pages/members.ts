import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Member, Role, ROLE_LABELS } from '../core/models';
import { Session } from '../core/session';

@Component({
  selector: 'app-members',
  imports: [FormsModule, MatTableModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h1>Equipo</h1>

    @if (session.can('ADMIN')) {
      <form class="invite" (ngSubmit)="add()">
        <mat-form-field>
          <mat-label>Correo</mat-label>
          <input matInput type="email" name="email" [(ngModel)]="email" required />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Rol</mat-label>
          <mat-select name="role" [(ngModel)]="role">
            @for (r of roles; track r) {
              <mat-option [value]="r">{{ labels[r] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <button mat-flat-button type="submit">Agregar</button>
      </form>
    }
    @if (message(); as text) {
      <p class="message" role="status">{{ text }}</p>
    }

    <table mat-table [dataSource]="members()">
      <ng-container matColumnDef="name">
        <th mat-header-cell *matHeaderCellDef>Persona</th>
        <td mat-cell *matCellDef="let m">{{ m.displayName }}<br /><small>{{ m.email }}</small></td>
      </ng-container>
      <ng-container matColumnDef="role">
        <th mat-header-cell *matHeaderCellDef>Rol</th>
        <td mat-cell *matCellDef="let m">
          @if (session.can('ADMIN') && !m.pending) {
            <mat-select [value]="m.role" (valueChange)="changeRole(m, $event)" [aria-label]="'Rol de ' + m.email">
              @for (r of roles; track r) {
                <mat-option [value]="r">{{ labels[r] }}</mat-option>
              }
            </mat-select>
          } @else {
            {{ roleLabel(m.role) }}
          }
        </td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Estado</th>
        <td mat-cell *matCellDef="let m">{{ m.pending ? 'Invitación pendiente' : 'Activo' }}</td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>
  `,
  styles: `
    h1 { font: var(--mat-sys-headline-medium); }
    .invite { display: flex; flex-wrap: wrap; gap: 12px; align-items: baseline; }
    .message { color: var(--mat-sys-primary); }
    table { width: 100%; }
    small { color: var(--mat-sys-on-surface-variant); }
  `,
})
export class Members {
  protected readonly session = inject(Session);
  private readonly http = inject(HttpClient);
  protected readonly labels = ROLE_LABELS;
  protected readonly roles: Role[] = ['ADMIN', 'TECHNICIAN', 'CUSTOMER'];
  protected readonly columns = ['name', 'role', 'status'];
  protected readonly members = signal<Member[]>([]);
  protected readonly message = signal<string | null>(null);
  protected email = '';
  protected role: Role = 'CUSTOMER';

  constructor() {
    // Recarga cuando cambia la organización elegida.
    effect(() => {
      const org = this.session.organization();
      if (org) void this.load(org.organizationId);
    });
  }

  protected roleLabel(role: Role): string {
    return ROLE_LABELS[role];
  }

  private base(): string {
    return `${environment.apiUrl}/organizations/${this.session.organization()!.organizationId}/members`;
  }

  private async load(organizationId: string): Promise<void> {
    this.members.set(await firstValueFrom(
      this.http.get<Member[]>(`${environment.apiUrl}/organizations/${organizationId}/members`)));
  }

  protected async add(): Promise<void> {
    await this.run(async () => {
      const member = await firstValueFrom(this.http.post<Member>(this.base(), { email: this.email, role: this.role }));
      this.message.set(member.pending
        ? 'Invitación creada: se activa cuando esa persona entre por primera vez.'
        : 'Miembro agregado.');
      this.email = '';
    });
  }

  protected async changeRole(member: Member, role: Role): Promise<void> {
    await this.run(async () => {
      await firstValueFrom(this.http.patch(`${this.base()}/${member.membershipId}`, { role }));
      this.message.set('Rol actualizado.');
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    try {
      await action();
    } catch (error) {
      const detail = error instanceof HttpErrorResponse ? error.error?.detail : null;
      this.message.set(detail ?? 'No se pudo completar la acción.');
    }
    await this.load(this.session.organization()!.organizationId);
  }
}
