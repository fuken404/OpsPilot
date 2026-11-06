import { DestroyRef, effect, inject, Injectable, signal } from '@angular/core';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { SlaKind, TicketStatus } from './models';
import { Session } from './session';

export interface LiveEvent {
  type: 'ticket' | 'sla';
  organizationId: string;
  ticketId: string;
  action?: string;
  status?: TicketStatus;
  actorId?: string;
  kind?: SlaKind;
  level?: 'WARNING' | 'BREACHED';
  at: string;
}

export type LiveStatus = 'off' | 'connecting' | 'live' | 'retrying';

const RENEW_EVERY_MS = 4 * 60_000;
const MAX_BACKOFF_MS = 30_000;

/**
 * Una conexión WebSocket por pestaña, suscrita a la organización elegida. Se reconecta sola con
 * espera creciente (el servidor la cierra al vencer el token, y en Vercel cada pocos minutos).
 * Los eventos solo dicen qué cambió: cada pantalla vuelve a pedir sus datos a la API.
 */
@Injectable({ providedIn: 'root' })
export class Realtime {
  private readonly oidc = inject(OidcSecurityService);
  private readonly session = inject(Session);
  private readonly listeners = new Set<(event: LiveEvent) => void>();
  private socket: WebSocket | null = null;
  private ready = false;
  private attempts = 0;
  private retryTimer?: ReturnType<typeof setTimeout>;
  private renewTimer?: ReturnType<typeof setInterval>;

  readonly status = signal<LiveStatus>('off');

  constructor() {
    effect(() => {
      const org = this.session.organization()?.organizationId;
      if (!org) return;
      if (!this.socket) void this.connect();
      else if (this.ready) this.send({ type: 'subscribe', organizationId: org });
    });
  }

  /** Escucha eventos mientras viva el componente que llama. */
  listen(listener: (event: LiveEvent) => void, destroyRef = inject(DestroyRef)): void {
    this.listeners.add(listener);
    destroyRef.onDestroy(() => this.listeners.delete(listener));
  }

  private async connect(): Promise<void> {
    clearTimeout(this.retryTimer);
    this.status.set(this.attempts === 0 ? 'connecting' : 'retrying');
    const token = await firstValueFrom(this.oidc.getAccessToken());
    const socket = new WebSocket(environment.wsUrl);
    this.socket = socket;
    this.ready = false;
    socket.onopen = () => socket.send(JSON.stringify({ type: 'auth', token }));
    socket.onmessage = (message) => this.receive(JSON.parse(message.data));
    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.socket = null;
      this.ready = false;
      clearInterval(this.renewTimer);
      this.status.set('retrying');
      const wait = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** this.attempts++);
      this.retryTimer = setTimeout(() => void this.connect(), wait);
    };
  }

  private receive(message: Omit<Partial<LiveEvent>, 'type'> & { type: string }): void {
    switch (message.type) {
      case 'ready': {
        const org = this.session.organization()?.organizationId;
        if (!this.ready && org) this.send({ type: 'subscribe', organizationId: org });
        this.ready = true;
        clearInterval(this.renewTimer);
        // El token se renueva en silencio; se le pasa el nuevo a la conexión para que no la cierren.
        this.renewTimer = setInterval(async () => {
          this.send({ type: 'auth', token: await firstValueFrom(this.oidc.getAccessToken()) });
        }, RENEW_EVERY_MS);
        break;
      }
      case 'subscribed':
        this.attempts = 0;
        this.status.set('live');
        break;
      case 'ticket':
      case 'sla':
        this.listeners.forEach((listener) => listener(message as unknown as LiveEvent));
        break;
    }
  }

  private send(message: object): void {
    if (this.socket?.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify(message));
  }
}
