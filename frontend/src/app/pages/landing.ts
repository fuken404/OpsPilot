import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { OidcSecurityService } from 'angular-auth-oidc-client';

@Component({
  selector: 'app-landing',
  imports: [MatButtonModule],
  template: `
    <main class="landing">
      <p class="kicker">OpsPilot</p>
      <h1>Incidentes de TI con SLA que se cumplen.</h1>
      <p class="lead">
        Tickets, responsables, prioridades y acuerdos de servicio para varias empresas, con aislamiento de datos
        y auditoría.
      </p>
      <button mat-flat-button (click)="login()">Entrar</button>
      <p class="hint">Cuentas de demostración en el README del repositorio.</p>
    </main>
  `,
  styles: `
    .landing { max-width: 640px; margin: 0 auto; padding: 18vh 24px 48px; }
    .kicker { margin: 0 0 8px; color: var(--mat-sys-primary); font: var(--mat-sys-label-large); letter-spacing: .08em; text-transform: uppercase; }
    h1 { margin: 0 0 16px; font: var(--mat-sys-display-small); }
    .lead { margin: 0 0 28px; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-large); }
    .hint { margin-top: 16px; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-small); }
  `,
})
export class Landing {
  private readonly oidc = inject(OidcSecurityService);

  login(): void {
    this.oidc.authorize();
  }
}
