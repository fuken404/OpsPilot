import { Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { Logo } from '../core/logo';

interface DemoTicket {
  key: string;
  title: string;
  status: string;
  statusClass: string;
  priority: number;
  seconds: number;
  who: string;
}

/** Portada pública: qué es, por qué es distinto y cómo probarlo. */
@Component({
  selector: 'app-landing',
  imports: [Logo],
  template: `
    <div class="hero">
      <header class="top">
        <app-logo [light]="true" />
        <div class="top-actions">
          <a class="ghost" href="https://github.com/fuken404/OpsPilot" target="_blank" rel="noopener">Código</a>
          <button class="cta small" type="button" (click)="login()">Entrar</button>
        </div>
      </header>

      <section class="pitch">
        <div class="copy">
          <p class="kicker"><i aria-hidden="true"></i>Mesa de ayuda de TI · multiempresa</p>
          <h1>Cada incidente,<br /><span>a tiempo.</span></h1>
          <p class="lead">
            Tickets, responsables y acuerdos de servicio que cuentan en horario hábil, con festivos de Colombia.
            Cada empresa ve solo lo suyo y todo cambio queda auditado.
          </p>
          <div class="actions">
            <button class="cta" type="button" (click)="login()">
              Probar la demo <span class="icon" aria-hidden="true">arrow_forward</span>
            </button>
            <span class="hint">Cuentas de prueba abajo</span>
          </div>
        </div>

        <div class="console" aria-hidden="true">
          <div class="console-bar">
            <span class="dots"><i></i><i></i><i></i></span>
            <span class="mono">acme · cola abierta</span>
            <span class="live"><i></i>en vivo</span>
          </div>
          @for (t of tickets(); track t.key) {
            <div class="row" [class.hot]="t.seconds < 900">
              <span class="key mono">{{ t.key }}</span>
              <span class="title">{{ t.title }}<small>{{ t.who }}</small></span>
              <span class="prio" [class]="'p' + t.priority">{{ priorityLabel(t.priority) }}</span>
              <span class="pill" [class]="t.statusClass">{{ t.status }}</span>
              <span class="timer mono">{{ clock(t.seconds) }}</span>
            </div>
          }
          <div class="console-foot mono">SLA en horario hábil · L–V 8:00–18:00 · sin festivos</div>
        </div>
      </section>
    </div>

    <section class="features">
      @for (f of features; track f.title) {
        <article>
          <span class="icon" aria-hidden="true">{{ f.icon }}</span>
          <h2>{{ f.title }}</h2>
          <p>{{ f.text }}</p>
        </article>
      }
    </section>

    <section class="demo">
      <div>
        <p class="eyebrow">Pruébalo</p>
        <h2>Cuatro cuentas, tres roles, dos empresas</h2>
        <p class="muted">Contraseña para todas: <code>demo-opspilot</code>. Abre dos navegadores para ver los cambios en vivo.</p>
      </div>
      <ul>
        @for (a of accounts; track a.user) {
          <li><code>{{ a.user }}</code><span>{{ a.role }}</span></li>
        }
      </ul>
    </section>

    <footer>
      <span>Hecho con Spring Boot 4, Angular 22, PostgreSQL, Redis y Keycloak.</span>
      <a href="https://fuken404.dev" target="_blank" rel="noopener">fuken404.dev</a>
    </footer>
  `,
  styles: `
    :host { display: block; background: var(--paper); }
    .hero { background: radial-gradient(1200px 500px at 85% -10%, #3b2a63 0%, transparent 60%), var(--ink); color: var(--ink-text); padding: 0 clamp(16px, 5vw, 64px) 72px; }
    .top { display: flex; justify-content: space-between; align-items: center; padding: 22px 0; max-width: 1180px; margin: 0 auto; }
    .top-actions { display: flex; align-items: center; gap: 18px; }
    .ghost { color: var(--ink-text); text-decoration: none; font-weight: 500; }
    .ghost:hover { color: #fff; }
    .pitch { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.05fr); gap: clamp(32px, 5vw, 72px); align-items: center;
      max-width: 1180px; margin: 0 auto; padding-top: clamp(24px, 6vw, 72px); }
    .kicker { display: inline-flex; align-items: center; gap: 8px; margin: 0 0 18px; padding: 6px 12px; border-radius: 999px;
      background: var(--ink-3); font: 700 12.5px/1 var(--font-ui); color: var(--ink-text); }
    .kicker i { width: 7px; height: 7px; border-radius: 50%; background: var(--accent-light); }
    h1 { margin: 0; font: 900 clamp(44px, 6.4vw, 80px)/1 var(--font-display); letter-spacing: -0.03em; color: #fff; }
    h1 span { color: var(--accent-light); }
    .lead { margin: 22px 0 30px; max-width: 48ch; font-size: 17px; line-height: 1.6; color: #c9c8c2; }
    .actions { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; }
    .hint { font-size: 13px; color: var(--ink-muted); }
    .cta { display: inline-flex; align-items: center; gap: 8px; border: 0; cursor: pointer; border-radius: 999px;
      padding: 14px 22px; background: var(--accent-light); color: #1e1b2e; font: 800 15px/1 var(--font-ui);
      box-shadow: 0 8px 30px -8px rgb(167 139 250 / 45%); transition: transform .15s ease; }
    .cta:hover { transform: translateY(-1px); }
    .cta.small { padding: 10px 14px; box-shadow: none; font-size: 14px; }
    .cta .icon { font-size: 18px; }

    .console { border-radius: 22px; background: #1f1f23; border: 1px solid var(--ink-3); overflow: hidden;
      box-shadow: 0 30px 80px -30px rgb(0 0 0 / 70%); }
    .console-bar { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-bottom: 1px solid var(--ink-3);
      font-size: 12px; color: var(--ink-muted); }
    .dots { display: inline-flex; gap: 6px; }
    .dots i { width: 9px; height: 9px; border-radius: 50%; background: #3f3f46; }
    .live { margin-left: auto; display: inline-flex; align-items: center; gap: 6px; font: 500 12px/1 var(--font-mono); color: var(--accent-light); }
    .live i { width: 6px; height: 6px; border-radius: 50%; background: var(--accent-light); }
    .row { display: grid; grid-template-columns: 78px minmax(0, 1fr) 64px 92px 64px; align-items: center; gap: 12px;
      padding: 13px 16px; border-bottom: 1px solid #2c2c31; font-size: 13.5px; }
    .key { color: var(--ink-muted); font-size: 12px; }
    .title { display: flex; flex-direction: column; color: #f3f2ee; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .title small { color: var(--ink-muted); font-size: 12px; }
    .prio { justify-self: start; padding: 3px 9px; border-radius: 999px; font: 800 11.5px/1.3 var(--font-ui); }
    .prio.p4 { background: #ef4444; color: #fff; }
    .prio.p3 { background: #fbbf24; color: #3b2600; }
    .prio.p2 { background: #ddd6fe; color: #4c1d95; }
    .prio.p1 { background: #3f3f46; color: #d4d4d8; }
    .pill { justify-self: start; padding: 3px 9px; border-radius: 999px; font-size: 12px; }
    .pill.new { background: #1d2a4f; color: #a9c1ff; }
    .pill.progress { background: #2b2350; color: #c7b8ff; }
    .pill.waiting { background: #43361a; color: #f5d27a; }
    .timer { justify-self: end; color: #c9c8c2; font-size: 13px; }
    .hot .timer { color: #ff8a7a; }
    .console-foot { padding: 11px 16px; font-size: 11.5px; color: var(--ink-muted); }

    .features { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; max-width: 1180px;
      margin: -36px auto 0; padding: 0 clamp(16px, 5vw, 64px); position: relative; }
    .features article { background: var(--card); border: 1px solid var(--line); border-radius: var(--radius); padding: 22px; box-shadow: var(--shadow); }
    .features article:nth-child(1) .icon { background: var(--tint-violet); color: #6d28d9; }
    .features article:nth-child(2) .icon { background: var(--tint-pink); color: #be185d; }
    .features article:nth-child(3) .icon { background: var(--tint-amber); color: #b45309; }
    .features article:nth-child(4) .icon { background: var(--tint-sky); color: #0369a1; }
    .features .icon { display: inline-grid; place-items: center; width: 40px; height: 40px; border-radius: 50%;
      background: #ede9fe; color: var(--accent); font-size: 20px; }
    .features h2 { margin: 14px 0 6px; font-size: 17px; font-weight: 800; }
    .features p { margin: 0; color: var(--muted); font-size: 14px; line-height: 1.55; }

    .demo { display: grid; grid-template-columns: 1fr 1fr; gap: 32px; align-items: center; max-width: 1180px;
      margin: 56px auto 0; padding: 0 clamp(16px, 5vw, 64px); }
    .demo h2 { margin: 6px 0 8px; font-size: 26px; font-weight: 800; }
    .demo ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
    .demo li { display: flex; justify-content: space-between; gap: 12px; padding: 12px 18px; border-radius: 999px;
      background: var(--card); border: 1px solid var(--line); font-size: 14px; }
    .demo li span { color: var(--muted); text-align: right; }
    code { font-family: var(--font-mono); font-size: 13px; color: var(--ink); }
    footer { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; max-width: 1180px; margin: 56px auto 0;
      padding: 24px clamp(16px, 5vw, 64px) 40px; border-top: 1px solid var(--line); color: var(--muted); font-size: 13px; }
    footer a { color: var(--ink); }

    @media (max-width: 980px) {
      .pitch { grid-template-columns: 1fr; }
      .features { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .demo { grid-template-columns: 1fr; }
    }
    @media (max-width: 560px) {
      .row { grid-template-columns: minmax(0, 1fr) 64px 64px; }
      .row .key, .row .pill { display: none; }
      .features { grid-template-columns: 1fr; }
      .ghost { display: none; }
    }
  `,
})
export class Landing implements OnDestroy {
  private readonly oidc = inject(OidcSecurityService);
  private readonly started = Date.now();
  private readonly tick = signal(0);
  private readonly timer = setInterval(() => this.tick.update((n) => n + 1), 1000);

  private readonly base: DemoTicket[] = [
    { key: 'ACME-128', title: 'VPN caída en la bodega norte', who: 'Tomás · Red', status: 'En curso', statusClass: 'progress', priority: 4, seconds: 754 },
    { key: 'ACME-127', title: 'Nómina no genera el PDF', who: 'Sin asignar · Software', status: 'Nuevo', statusClass: 'new', priority: 3, seconds: 3480 },
    { key: 'ACME-125', title: 'Alta de usuario para auditoría', who: 'Ana · Accesos', status: 'Esperando', statusClass: 'waiting', priority: 2, seconds: 14100 },
    { key: 'ACME-121', title: 'Impresora del piso 3 atascada', who: 'Tomás · Hardware', status: 'En curso', statusClass: 'progress', priority: 1, seconds: 26520 },
  ];

  /** Cuenta regresiva de mentira para la consola de la portada. */
  protected readonly tickets = computed(() => {
    this.tick();
    const elapsed = Math.floor((Date.now() - this.started) / 1000);
    return this.base.map((t) => ({ ...t, seconds: t.statusClass === 'waiting' ? t.seconds : Math.max(0, t.seconds - elapsed) }));
  });

  protected readonly features = [
    { icon: 'event_available', title: 'SLA en horario hábil', text: 'Cuenta de lunes a viernes, salta festivos de Colombia y se pausa mientras se espera al cliente.' },
    { icon: 'shield_lock', title: 'Aislamiento real', text: 'Row Level Security en PostgreSQL: aunque una consulta olvide filtrar, nadie ve otra empresa.' },
    { icon: 'bolt', title: 'En tiempo real', text: 'WebSocket por organización con Redis: lo que cambia uno lo ve el resto sin recargar.' },
    { icon: 'history', title: 'Todo auditado', text: 'Cada cambio queda en un historial que la aplicación no puede editar ni borrar.' },
  ];

  protected readonly accounts = [
    { user: 'ana.admin', role: 'Administradora en Acme y Nimbus' },
    { user: 'tomas.tecnico', role: 'Técnico en Acme' },
    { user: 'carla.cliente', role: 'Cliente en Acme' },
    { user: 'lucia.cliente', role: 'Cliente en Nimbus' },
  ];

  protected priorityLabel(level: number): string {
    return ['Baja', 'Media', 'Alta', 'Crítica'][level - 1];
  }

  protected clock(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  login(): void {
    this.oidc.authorize();
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }
}
