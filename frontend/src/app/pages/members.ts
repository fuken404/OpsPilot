import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Avatar } from '../core/chips';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Member, Role, ROLE_LABELS } from '../core/models';
import { Session } from '../core/session';

@Component({
  selector: 'app-members',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, Avatar],
  template: `
    <header class="page-head">
      <div>
        <p class="eyebrow">{{ session.organization()?.name }}</p>
        <h1>Equipo</h1>
        <p class="lede">Quién trabaja en esta organización y con qué rol. El rol vale solo aquí: alguien puede ser técnico en una empresa y cliente en otra.</p>
      </div>
    </header>

    @if (session.can('ADMIN')) {
      <form class="invite card card-pad" (ngSubmit)="add()">
        <div class="invite-text">
          <h2 class="card-title">Invitar a alguien</h2>
          <p class="card-sub">Si ya entró antes, queda agregado; si no, la invitación se activa en su primer ingreso con correo verificado.</p>
        </div>
        <mat-form-field class="email">
          <mat-label>Correo</mat-label>
          <input matInput type="email" name="email" [(ngModel)]="email" required />
        </mat-form-field>
        <mat-form-field class="role">
          <mat-label>Rol</mat-label>
          <mat-select name="role" [(ngModel)]="role">
            @for (r of roles; track r) {
              <mat-option [value]="r">{{ labels[r] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <button mat-flat-button type="submit" class="btn-icon"><span class="icon">person_add</span>Invitar</button>
      </form>
    }
    @if (message(); as text) {
      <p class="message" role="status"><span class="icon" aria-hidden="true">info</span>{{ text }}</p>
    }

    <section class="card people">
      @for (m of members(); track m.email) {
        <div class="person">
          <app-avatar [name]="m.pending ? null : m.displayName" [size]="38" />
          <div class="who">
            <strong>{{ m.pending ? m.email : m.displayName }}</strong>
            <span>{{ m.pending ? 'Invitación pendiente' : m.email }}</span>
          </div>
          @if (session.can('ADMIN') && !m.pending) {
            <mat-form-field class="role-select">
              <mat-select [value]="m.role" (valueChange)="changeRole(m, $event)" [aria-label]="'Rol de ' + m.email">
                @for (r of roles; track r) {
                  <mat-option [value]="r">{{ labels[r] }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          } @else {
            <span class="role-tag" [class]="m.role.toLowerCase()">{{ roleLabel(m.role) }}</span>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .invite { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) 170px auto; gap: 12px; align-items: center; margin-bottom: 16px; }
    .invite-text { min-width: 0; }
    .message { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; padding: 10px 14px; border-radius: 10px; background: #ede9fe; color: #4c1d95; }
    .people { overflow: hidden; }
    .person { display: flex; align-items: center; gap: 14px; padding: 14px 20px; }
    .person + .person { border-top: 1px solid var(--line); }
    .who { display: flex; flex-direction: column; min-width: 0; flex: 1; }
    .who strong { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .who span { font-size: 13px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .role-select { width: 170px; }
    .role-tag { padding: 4px 10px; border-radius: 999px; font-size: 12.5px; font-weight: 700; background: #f4f4f5; }
    .role-tag.admin { background: var(--ink); color: var(--accent-light); }
    .role-tag.technician { background: #e3ebff; color: #1e3a8a; }
    @media (max-width: 900px) {
      .invite { grid-template-columns: 1fr 1fr; }
      .invite-text { grid-column: 1 / -1; }
      .role-select { width: 130px; }
    }
  `,
})
export class Members {
  protected readonly session = inject(Session);
  private readonly http = inject(HttpClient);
  protected readonly labels = ROLE_LABELS;
  protected readonly roles: Role[] = ['ADMIN', 'TECHNICIAN', 'CUSTOMER'];
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
