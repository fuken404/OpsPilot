import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Me, MyMembership, Role } from './models';

const SELECTED_KEY = 'opspilot.organization';

/** Quién está conectado y en qué organización trabaja. Todo el estado es de señales. */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly http = inject(HttpClient);
  private readonly oidc = inject(OidcSecurityService);

  readonly me = signal<Me | null>(null);
  readonly error = signal<string | null>(null);
  private readonly selectedId = signal<string | null>(readSelected());

  readonly organization = computed<MyMembership | null>(() => {
    const memberships = this.me()?.memberships ?? [];
    return memberships.find((m) => m.organizationId === this.selectedId()) ?? memberships[0] ?? null;
  });

  readonly role = computed<Role | null>(() => this.organization()?.role ?? null);

  async load(): Promise<void> {
    try {
      this.me.set(await firstValueFrom(this.http.get<Me>(`${environment.apiUrl}/me`)));
      this.error.set(null);
    } catch {
      this.error.set('No se pudo cargar tu sesión. Revisa que la API esté encendida.');
    }
  }

  select(organizationId: string): void {
    this.selectedId.set(organizationId);
    try {
      localStorage.setItem(SELECTED_KEY, organizationId);
    } catch {
      // sin almacenamiento: la selección dura hasta recargar
    }
  }

  can(...roles: Role[]): boolean {
    const role = this.role();
    return role !== null && roles.includes(role);
  }

  logout(): void {
    this.oidc.logoff().subscribe();
  }
}

function readSelected(): string | null {
  try {
    return localStorage.getItem(SELECTED_KEY);
  } catch {
    return null;
  }
}
